package risk_assessor;

/** The answer for one order: what to do, why, and the expected Rupee effect. */
public record Decision(String orderId, Action action, double risk, String reason, int discount,
                       double saved, double lost, double discountsAndFees) {
    public enum Action {
        SHIP("Ship as-is"), CONFIRM("Ship after confirmation"), NUDGE("Nudge to prepaid"), HOLD("Hold / cancel");
        public final String label;
        Action(String l) { label = l; }
    }
    public double net() { return saved - lost - discountsAndFees; }
}
