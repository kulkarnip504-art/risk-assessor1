package risk_assessor;

import risk_assessor.LlmJudge.Judgement;
import risk_assessor.LlmJudge.Quality;
import java.util.*;

/**
 * Estimates the chance an order becomes an RTO. A small logistic regression (a "scorecard"):
 * every signal adds or removes some risk, so we can always say WHICH signals drove the score.
 * Learned from past orders; no outside library.
 */
public class RiskScorer {
    public record Driver(String label, double points) {}          // points = effect on risk, in percentage points
    public record Result(double risk, List<Driver> drivers) {}

    private static final String[] FLAGS = {"Address is very short or junk", "Address has landmarks but no house number",
        "Name looks fake or test", "Phone number is invalid", "1 earlier order was returned", "2+ earlier orders were returned",
        "First-time customer", "Order value above Rs.1,500"};
    private final List<String> states = new ArrayList<>();
    private double bias; private double[] w;
    private double meanStateW;

    private static boolean phoneOk(String p) { return p.matches("[6-9]\\d{9}"); }

    private double[] features(Order o, Judgement j) {
        double[] x = new double[states.size() + FLAGS.length];
        int s = states.indexOf(o.state());
        if (s >= 0) x[s] = 1;
        int k = states.size();
        x[k]     = j.address() == Quality.POOR ? 1 : 0;
        x[k + 1] = j.address() == Quality.VAGUE ? 1 : 0;
        x[k + 2] = j.fakeName() ? 1 : 0;
        x[k + 3] = phoneOk(o.phone()) ? 0 : 1;
        x[k + 4] = o.priorRto() == 1 ? 1 : 0;
        x[k + 5] = o.priorRto() >= 2 ? 1 : 0;
        x[k + 6] = o.firstTime() ? 1 : 0;
        x[k + 7] = o.value() > 1500 ? 1 : 0;
        return x;
    }
    private static double sig(double z) { return 1 / (1 + Math.exp(-z)); }

    public void train(List<Order> orders, LlmJudge judge) {
        states.clear();
        orders.stream().map(Order::state).distinct().sorted().forEach(states::add);
        int n = orders.size(), d = states.size() + FLAGS.length;
        double[][] X = new double[n][]; double[] y = new double[n];
        for (int i = 0; i < n; i++) { X[i] = features(orders.get(i), judge.judge(orders.get(i))); y[i] = orders.get(i).isRto() ? 1 : 0; }
        w = new double[d]; bias = 0;
        double lr = 1.0, l2 = 0.002;
        for (int it = 0; it < 3000; it++) {
            double[] g = new double[d]; double gb = 0;
            for (int i = 0; i < n; i++) {
                double z = bias; for (int j = 0; j < d; j++) z += w[j] * X[i][j];
                double e = sig(z) - y[i];
                gb += e; for (int j = 0; j < d; j++) g[j] += e * X[i][j];
            }
            bias -= lr * gb / n;
            for (int j = 0; j < d; j++) w[j] -= lr * (g[j] / n + l2 * w[j]);
        }
        meanStateW = 0; for (int s = 0; s < states.size(); s++) meanStateW += w[s] / states.size();
    }

    public Result score(Order o, Judgement j) {
        double[] x = features(o, j);
        double z = bias; for (int k = 0; k < w.length; k++) z += w[k] * x[k];
        double p = sig(z);
        List<Driver> ds = new ArrayList<>();
        int s = states.indexOf(o.state());
        if (s >= 0) add(ds, "State: " + o.state(), p, z, w[s] - meanStateW);
        for (int f = 0; f < FLAGS.length; f++) if (x[states.size() + f] == 1) add(ds, FLAGS[f], p, z, w[states.size() + f]);
        ds.sort((a, b) -> Double.compare(Math.abs(b.points()), Math.abs(a.points())));
        return new Result(p, ds);
    }
    private void add(List<Driver> ds, String label, double p, double z, double effectInLogOdds) {
        double pts = (p - sig(z - effectInLogOdds)) * 100;
        if (Math.abs(pts) >= 2) ds.add(new Driver(label, pts));
    }

    /** How well does the score rank risky orders above safe ones? 0.5 = coin flip, 1.0 = perfect. */
    public double auc(List<Order> test, LlmJudge judge) {
        int n = test.size(); double[] sc = new double[n]; boolean[] pos = new boolean[n];
        for (int i = 0; i < n; i++) { sc[i] = score(test.get(i), judge.judge(test.get(i))).risk(); pos[i] = test.get(i).isRto(); }
        double wins = 0; long pairs = 0;
        for (int i = 0; i < n; i++) if (pos[i]) for (int j = 0; j < n; j++) if (!pos[j]) { pairs++; wins += sc[i] > sc[j] ? 1 : sc[i] == sc[j] ? 0.5 : 0; }
        return wins / pairs;
    }
    public double baseRate(List<Order> orders) { return orders.stream().filter(Order::isRto).count() / (double) orders.size(); }
}
