package risk_assessor;

/** All the Rupee maths in one place, so it is easy to read and check. */
public class Economics {
    private final Config c;
    public Economics(Config c) { this.c = c; }

    /** Money lost on one RTO: forward + return shipping + packing + damaged stock share. */
    public double rtoLoss() {
        return c.d("forward_shipping_inr") + c.d("return_shipping_inr") + c.d("packaging_handling_inr")
             + c.d("damaged_return_share") * c.d("product_cost_inr");
    }
    /** Profit we make when an order is delivered. */
    public double margin(int value) { return value * c.d("gross_margin_pct") / 100.0; }
    /** What we lose if a genuine order is cancelled (profit, plus ad spend for first-time buyers). */
    public double lossIfGenuineLost(Order o) { return margin(o.value()) + (o.firstTime() ? c.d("first_time_extra_loss_inr") : 0); }
    public double gatewayFee(int value) { return value * c.d("payment_fee_pct") / 100.0; }

    /** Cancelling pays off only if the RTO chance is above this. */
    public double cancelBreakEven(Order o) { double g = lossIfGenuineLost(o); return g / (g + rtoLoss()); }
    /** Asking for confirmation pays off only if the RTO chance is above this. */
    public double confirmBreakEven(Order o) {
        double drop = c.d("confirm_loses_genuine_share") * lossIfGenuineLost(o);
        return drop / (c.d("confirm_catches_rto_share") * rtoLoss() + drop);
    }
    /** Most we could pay as discount and still come out ahead if the customer switches to prepaid. */
    public double maxDiscount(double risk, int value) {
        return (risk - c.d("prepaid_rto_rate")) * rtoLoss() - gatewayFee(value);
    }
    /** The discount we actually offer: a share of the saving, rounded to Rs.5, kept inside min/max. Returns 0 if not affordable. */
    public int discount(double risk, int value) {
        double max = maxDiscount(risk, value);
        if (max < c.d("min_discount_inr")) return 0;
        double want = Math.round(c.d("discount_share_of_saving") * max / 5.0) * 5.0;
        return (int) Math.max(c.d("min_discount_inr"), Math.min(c.d("max_discount_inr"), want));
    }
}
