package risk_assessor;

import risk_assessor.Decision.Action;
import risk_assessor.LlmJudge.Judgement;
import risk_assessor.RiskScorer.Driver;
import risk_assessor.RiskScorer.Result;
import java.util.*;
import java.util.stream.Collectors;

/** Turns an order into one of 4 actions, using ONLY the numbers in rules.properties. */
public class DecisionEngine {
    private final Config c; private final LlmJudge judge; private final RiskScorer scorer; private final Economics eco;
    public DecisionEngine(Config c, LlmJudge j, RiskScorer s, Economics e) { this.c = c; judge = j; scorer = s; eco = e; }
    public Economics economics() { return eco; }

    public Decision decide(Order o) {
        Judgement j = judge.judge(o);
        Result r = scorer.score(o, j);
        double p = r.risk(), L = eco.rtoLoss();
        double holdAt = o.firstTime() ? c.d("hold_if_risk_at_least_first_time") : c.d("hold_if_risk_at_least");
        Action a; int disc = 0; String why;
        String drivers = drivers(r, j);
        String head = String.format("%.0f%% chance of RTO (average is about 28%%).", p * 100);

        if (o.priorRto() >= c.i("hold_if_prior_rto_at_least")) {
            a = Action.HOLD;
            why = "Customer already returned " + o.priorRto() + " earlier orders. " + head + " Do not ship COD; offer prepaid only.";
        } else if (p >= holdAt) {
            a = Action.HOLD;
            why = head + drivers + " Expected RTO cost if shipped: Rs." + Math.round(p * L) + ".";
        } else if (p >= c.d("confirm_if_risk_at_least")) {
            a = Action.CONFIRM;
            why = head + drivers + " Ask the customer to confirm on WhatsApp before we ship.";
        } else if (p >= c.d("nudge_if_risk_at_least") && (disc = eco.discount(p, o.value())) > 0) {
            a = Action.NUDGE;
            why = head + drivers + " Offer Rs." + disc + " off to pay online (online orders return only about 4%).";
        } else {
            a = Action.SHIP; why = head + " Low risk, ship normally." + (drivers.isEmpty() ? "" : drivers);
        }
        // Safety rule: unusable address or fake name must be confirmed first, whatever the score says.
        if ((a == Action.SHIP || a == Action.NUDGE) && c.b("confirm_junk_address_or_fake_name")
                && (j.address() == LlmJudge.Quality.POOR || j.fakeName())) {
            a = Action.CONFIRM; disc = 0;
            why = head + (j.fakeName() ? " The name looks fake." : " The address is too short or unusable (" + j.note() + ").")
                + " Ask the customer to confirm / give a full address before we ship.";
        }
        return withImpact(o, a, p, why, disc);
    }

    /** Expected Rupee effect of this decision, before we know the real outcome. */
    private Decision withImpact(Order o, Action a, double p, String why, int disc) {
        double L = eco.rtoLoss(), G = eco.lossIfGenuineLost(o), saved = 0, lost = 0, fees = 0;
        switch (a) {
            case HOLD -> { saved = p * L; lost = (1 - p) * G; }
            case CONFIRM -> { saved = p * c.d("confirm_catches_rto_share") * L; lost = (1 - p) * c.d("confirm_loses_genuine_share") * G; }
            case NUDGE -> { double acc = c.d("nudge_acceptance_rate");
                saved = acc * (p - c.d("prepaid_rto_rate")) * L; fees = acc * (disc + eco.gatewayFee(o.value())); }
            default -> {}
        }
        return new Decision(o.id(), a, p, why, disc, saved, lost, fees);
    }

    private String drivers(Result r, Judgement j) {
        List<Driver> up = r.drivers().stream().filter(d -> d.points() > 0).limit(3).collect(Collectors.toList());
        if (up.isEmpty()) return "";
        String s = up.stream().map(d -> String.format("%s (+%.0f pts)", d.label(), d.points())).collect(Collectors.joining("; "));
        return " Main reasons: " + s + ".";
    }
}
