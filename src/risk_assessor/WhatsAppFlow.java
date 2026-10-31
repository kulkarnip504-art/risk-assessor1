package risk_assessor;

import risk_assessor.Decision.Action;
import java.util.*;

/**
 * Mock WhatsApp conversation for CONFIRM and NUDGE orders.
 * One conversation per order. States: WAITING -> SHIPPED | CANCELLED | HUMAN_REVIEW.
 * Time is simulated: call tick(hours) to move the clock (the chat page has buttons for this).
 */
public class WhatsAppFlow {
    public static class Chat {
        public final String orderId, name; public final Action kind; public final int discount, value;
        public boolean needsAddress = false; public String state = "WAITING"; public int hoursPassed = 0; public boolean reminded = false; public int confusedReplies = 0;
        public final List<String[]> messages = new ArrayList<>();   // {"bot"|"customer"|"system", text}
        Chat(Order o, Action k, int d) { orderId = o.id(); name = o.name().split(" ")[0]; kind = k; discount = d; value = o.value(); }
    }
    private final Map<String, Chat> chats = new LinkedHashMap<>();
    private final Config cfg;
    private final String brand;
    public WhatsAppFlow(Config cfg) { this.cfg = cfg; this.brand = cfg.s("brand_name"); }

    /** Message 1: sent within minutes of the order. Hinglish, short, one clear question. */
    public Chat start(Order o, Decision d) {
        Chat c = new Chat(o, d.action(), d.discount());
        if (d.action() == Action.CONFIRM && o.address().trim().length() <= 20) {
            // junk address: asking "is it correct?" is pointless. Ask for the real one instead (fixes the cause of the RTO).
            c.needsAddress = true;
            c.messages.add(new String[]{"bot", "Namaste " + c.name + "! 🙏 Aapka " + brand + " order " + o.id() + " (₹" + o.value() + ", COD) mil gaya.\n"
                + "Delivery ke liye address poora nahi hai. Kripya apna *poora address* reply karein (ghar/flat number, area, landmark).\n\nOrder cancel karna ho to *NO* likhein."});
        } else if (d.action() == Action.CONFIRM) {
            c.messages.add(new String[]{"bot", "Namaste " + c.name + "! 🙏 Aapka " + brand + " order " + o.id() + " (₹" + o.value() + ", COD) mil gaya.\n"
                + "Shipping se pehle confirm kar dein: kya address sahi hai?\n\nReply *YES* to confirm ✅ ya *NO* to cancel ❌\n(Pura address: " + o.address() + ", " + o.city() + ")"});
        } else {
            int pay = o.value() - d.discount();
            c.messages.add(new String[]{"bot", "Namaste " + c.name + "! 🙏 Aapka " + brand + " order " + o.id() + " (₹" + o.value() + ") mil gaya.\n"
                + "Abhi online pay karein aur ₹" + d.discount() + " ki chhoot payein — sirf ₹" + pay + " lagenge! 🎁\n"
                + "Pay link: https://pay.example/" + o.id() + "\n\nReply *PAY* for the link, ya *COD* agar cash on delivery hi chahiye."});
        }
        chats.put(o.id(), c); return c;
    }

    /** What happens when the customer replies. */
    public Chat reply(String orderId, String textRaw) {
        Chat c = chats.get(orderId); if (c == null || !c.state.equals("WAITING")) return c;
        String t = textRaw.trim().toLowerCase();
        c.messages.add(new String[]{"customer", textRaw});
        boolean yes = t.matches("(yes|y|haan|ha|han|ok|okay|confirm|sahi hai|theek hai).*");
        boolean no = t.matches("(no|n|nahi|nahin|cancel|mat bhejo|nhi).*");
        boolean pay = t.matches("(pay|paid|link|online).*");
        if (c.kind == Action.CONFIRM && c.needsAddress && !no) {
            if (t.length() >= 20 && !yes) { c.state = "SHIPPED"; bot(c, "Shukriya! ✅ Address mil gaya, order ship ho jayega."); sys(c, "Customer gave a full address -> order released. New address: " + textRaw); }
            else confused(c, "Kripya poora address likhein (ghar/flat number, area, landmark) ya *NO* to cancel.");
        } else if (c.kind == Action.CONFIRM) {
            if (yes) { c.state = "SHIPPED"; bot(c, "Shukriya! ✅ Order confirm ho gaya, kal tak ship ho jayega."); sys(c, "Order released for shipping."); }
            else if (no) { c.state = "CANCELLED"; bot(c, "Theek hai, order cancel kar diya. Phir milte hain! 🙏"); sys(c, "Order cancelled. Saved the RTO cost."); }
            else confused(c, "Reply *YES* to confirm ya *NO* to cancel.");
        } else {
            if (pay) { c.state = "SHIPPED"; bot(c, "Great choice! 🎉 Payment link bhej diya. Payment ke baad order ship hoga."); sys(c, "Customer switched to prepaid. Order ships after payment."); }
            else if (t.startsWith("cod")) { c.state = "SHIPPED"; bot(c, "Koi baat nahi! Order COD par hi ship hoga. 📦"); sys(c, "Customer kept COD. Order ships as COD (the nudge never blocks an order)."); }
            else if (no) { c.state = "CANCELLED"; bot(c, "Theek hai, order cancel kar diya. 🙏"); sys(c, "Customer cancelled."); }
            else confused(c, "Reply *PAY* for the discount link ya *COD* for cash on delivery.");
        }
        return c;
    }

    /** Random text: ask once more, then hand to a human. We never guess on a random reply. */
    private void confused(Chat c, String hint) {
        c.confusedReplies++;
        if (c.confusedReplies == 1) bot(c, "Maaf kijiye, samajh nahi aaya. 🙏 " + hint);
        else { c.state = "HUMAN_REVIEW"; bot(c, "Hamari team aapko call karegi. Dhanyavaad! 🙏"); sys(c, "Unclear replies twice -> sent to the support team to call. Order stays on hold."); }
    }

    /** Move the clock. 12h with no reply = one reminder. 24h = final rule. */
    public Chat tick(String orderId, int hours) {
        Chat c = chats.get(orderId); if (c == null || !c.state.equals("WAITING")) return c;
        c.hoursPassed += hours;
        if (c.hoursPassed >= 12 && !c.reminded) {
            c.reminded = true; bot(c, "Reminder 🔔 " + c.name + ", aapka order abhi confirm hona baaki hai. " + (c.kind == Action.CONFIRM ? "Reply *YES* ya *NO*." : "Reply *PAY* ya *COD*."));
        }
        if (c.hoursPassed >= 24) {
            if (c.kind == Action.CONFIRM) { c.state = "CANCELLED"; sys(c, "No reply in 24h -> order cancelled (customer never confirmed; risky orders are not shipped unconfirmed)."); }
            else { c.state = "SHIPPED"; sys(c, "No reply in 24h -> ships as COD. A discount offer must never cost us a sale."); }
        }
        return c;
    }
    public Chat get(String id) { return chats.get(id); }
    private void bot(Chat c, String t) { c.messages.add(new String[]{"bot", t}); }
    private void sys(Chat c, String t) { c.messages.add(new String[]{"system", t}); }
}
