package risk_assessor;

import risk_assessor.Decision.Action;
import java.util.*;

/** "What would the system have done to last month's orders?" using the REAL outcomes we now know. */
public class Backtest {
    public static final java.time.LocalDate SPLIT = java.time.LocalDate.of(2026, 9, 1);   // train before, test from this date

    /** Rupee effect of an action once we know whether the order was really returned. Returns {saved, lost, fees}. */
    static double[] actual(Action a, Order o, Decision d, Config c, Economics e) {
        double L = e.rtoLoss(), G = e.lossIfGenuineLost(o);
        switch (a) {
            case HOLD:    return new double[]{o.isRto() ? L : 0, o.isRto() ? 0 : G, 0};
            case CONFIRM: return new double[]{o.isRto() ? c.d("confirm_catches_rto_share") * L : 0,
                                              o.isRto() ? 0 : c.d("confirm_loses_genuine_share") * G, 0};
            case NUDGE:   { double acc = c.d("nudge_acceptance_rate");
                            return new double[]{o.isRto() ? acc * (1 - c.d("prepaid_rto_rate")) * L : 0, 0, acc * (d.discount() + e.gatewayFee(o.value()))}; }
            default:      return new double[]{0, 0, 0};
        }
    }

    public static void run(Config c, List<Order> test, DecisionEngine eng, Economics eco, RiskScorer sc, LlmJudge judge, List<Order> train) {
        double scale = c.d("monthly_cod_orders") / test.size();
        System.out.printf("%nTRAINED on %d orders before %s, TESTED on %d later orders (last month in the data).%n", train.size(), SPLIT, test.size());
        System.out.printf("Model quality (AUC, 0.5 = guessing, 1.0 = perfect): %.2f%n", sc.auc(test, judge));
        System.out.printf("Rupee facts: one RTO costs Rs.%.0f. Cancelling only pays off above %.0f%% RTO risk (%.0f%% for a first-time buyer on a Rs.649 order).%n",
                eco.rtoLoss(), 100 * eco.cancelBreakEven(new Order("x", null, "", "", "", "", "", "", 649, 1, 5, 0, "", "", "DELIVERED")),
                100 * eco.cancelBreakEven(new Order("x", null, "", "", "", "", "", "", 649, 1, 0, 0, "", "", "DELIVERED")));

        Map<Action, int[]> cnt = new EnumMap<>(Action.class);          // {orders, rto}
        Map<Action, double[]> money = new EnumMap<>(Action.class);     // {saved, lost, fees}
        for (Action a : Action.values()) { cnt.put(a, new int[2]); money.put(a, new double[3]); }
        List<Decision> ds = new ArrayList<>();
        for (Order o : test) {
            Decision d = eng.decide(o); ds.add(d);
            int[] k = cnt.get(d.action()); k[0]++; if (o.isRto()) k[1]++;
            double[] m = actual(d.action(), o, d, c, eco), t = money.get(d.action());
            for (int i = 0; i < 3; i++) t[i] += m[i];
        }
        System.out.println("\nWHAT THE SYSTEM WOULD HAVE DONE (scaled to ~" + c.i("monthly_cod_orders") + " COD orders a month):");
        System.out.printf("%-24s %7s %12s %10s %10s %10s%n", "Action", "Orders", "Real RTO %", "Saved Rs.", "Lost Rs.", "Net Rs.");
        double ts = 0, tl = 0, tf = 0; int tn = 0, tr = 0;
        for (Action a : Action.values()) {
            int[] k = cnt.get(a); double[] m = money.get(a);
            System.out.printf("%-24s %7.0f %11.0f%% %10.0f %10.0f %10.0f%n", a.label, k[0] * scale, k[0] == 0 ? 0 : 100.0 * k[1] / k[0],
                    m[0] * scale, m[1] * scale, (m[0] - m[1] - m[2]) * scale);
            ts += m[0]; tl += m[1]; tf += m[2]; tn += k[0]; tr += k[1];
        }
        System.out.printf("%-24s %7.0f %11.0f%% %10.0f %10.0f %10.0f   (discounts+fees Rs.%.0f)%n", "TOTAL", tn * scale, 100.0 * tr / tn,
                ts * scale, tl * scale, (ts - tl - tf) * scale, tf * scale);

        System.out.println("\nCOMPARED WITH THE TWO LEADERS' IDEAS (same orders, real outcomes, per month):");
        System.out.printf("  Ship everything (Marketing's wish):          net Rs.0  | RTO cost we eat: Rs.%.0f%n", tr * scale * eco.rtoLoss());
        for (double cut : new double[]{0.30, 0.40, 0.50}) {
            double s = 0, l = 0; int h = 0;
            for (Order o : test) { double p = sc.score(o, judge.judge(o)).risk();
                if (p >= cut) { h++; double[] m = actual(Action.HOLD, o, null, c, eco); s += m[0]; l += m[1]; } }
            System.out.printf("  Cancel every order with risk >= %.0f%% (Ops): cancels %4.0f orders, saves Rs.%.0f, loses Rs.%.0f -> net Rs.%.0f%n",
                    cut * 100, h * scale, s * scale, l * scale, (s - l) * scale);
        }
        System.out.printf("  Our 4-bucket plan:                           net Rs.%.0f%n", (ts - tl - tf) * scale);
    }
}
