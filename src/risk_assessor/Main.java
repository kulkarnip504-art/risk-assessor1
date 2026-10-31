package risk_assessor;

import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "backtest";
        Config cfg = new Config("config/rules.properties");
        OrderCleaner cleaner = new OrderCleaner();
        List<Order> all = cleaner.load("data/orders_sample.csv");
        System.out.println(cleaner.summary(all.size()));

        List<Order> train = all.stream().filter(o -> o.time().toLocalDate().isBefore(Backtest.SPLIT)).collect(Collectors.toList());
        List<Order> test = all.stream().filter(o -> !o.time().toLocalDate().isBefore(Backtest.SPLIT)).collect(Collectors.toList());
        LlmJudge judge = new LlmJudge(cfg);
        RiskScorer scorer = new RiskScorer();
        scorer.train(train, judge);
        Economics eco = new Economics(cfg);
        DecisionEngine engine = new DecisionEngine(cfg, judge, scorer, eco);

        switch (mode) {
            case "backtest" -> Backtest.run(cfg, test, engine, eco, scorer, judge, train);
            case "serve" -> new WebServer(cfg, engine, eco, test, judge).start(args.length > 1 ? Integer.parseInt(args[1]) : 8080);
            default -> System.out.println("Use: backtest | serve [port]");
        }
    }
}
