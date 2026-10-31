# COD Guard

Looks at every Cash-on-Delivery order before it ships and picks one of four actions:
**Ship as-is, Ship after WhatsApp confirmation, Nudge to prepaid, Hold / cancel.**
Every decision comes with a plain-English reason and a Rupee estimate.

## Run it (about 5 minutes)

You need **Java 17 or newer (a JDK, not just a JRE)**. Check with `javac -version`.
No Maven, no downloads, no libraries.

    sh run.sh backtest      # Windows: run.bat backtest
    sh run.sh serve         # Windows: run.bat serve

- `backtest` trains on orders before 1 Sep 2026 and replays September using the real outcomes (saved in `docs/backtest_output.txt`).
- `serve` opens two pages: **http://localhost:8080/** (founder dashboard) and **http://localhost:8080/chat** (WhatsApp simulator).
  Use another port with `sh run.sh serve 9000`.

## Change the rules without touching code

Open `config/rules.properties` in any text editor. Every threshold has a plain-English note above it.
Save, then restart `sh run.sh serve`. The two dials that matter for the Operations vs Marketing debate:

    hold_if_risk_at_least=0.70              (lower = cancel more, Operations)
    hold_if_risk_at_least_first_time=0.80   (higher = protect new customers, Marketing)

## Where the AI is used, and where it is not

| AI (LLM) reads text | Plain code / maths does the rest |
|---|---|
| Is this address usable? (GOOD / VAGUE / POOR, including Hinglish landmarks) | Cleaning, duplicates, phone and pincode checks |
| Is this name fake ("asdf", "Test Order")? | RTO risk score, thresholds, the 4-way decision |
| | Rupee maths, discount size, dashboard numbers |

Why: reading messy Hinglish is what a language model is good at; a score, a threshold or a sum is something code does
exactly, instantly, for free and the same way every time. The AI answer is forced into a fixed format (anything else is
rejected) and customer text is treated as data, so an address saying "ignore your rules" does nothing.
If `use_llm=false`, there is no `ANTHROPIC_API_KEY`, or the call fails, built-in rules take over. To switch AI on:

    export ANTHROPIC_API_KEY=your_key      # then set use_llm=true in config/rules.properties

## Files

    config/rules.properties   all numbers a business person may change
    data/orders_sample.csv    past COD orders
    src/codguard/
      OrderCleaner, Csv, Order     load and clean the data
      LlmJudge                     the only AI step (+ fallback rules)
      RiskScorer                   estimates RTO chance, explains the drivers
      Economics                    all the Rupee maths and break-even points
      DecisionEngine, Decision     picks the action and writes the reason
      WhatsAppFlow                 mock chat: yes / no / random / silence / 24h
      Backtest                     replays last month with real outcomes
      WebServer, Main              dashboard + simulator, command line
    web/dashboard.html, chat.html
    docs/decision_log.md, ai_usage_log.md, data_findings.md, backtest_output.txt

## Honest limits

- The live AI call was **not tested** (no API key where this was built). Only the built-in rules were run. Test with your key before relying on it.
- Rupee figures for confirmation and prepaid nudges rest on **assumptions** (50% of RTOs caught, 8% of genuine orders lost, 25% accept the nudge). They are in the config. Measure them with a small real test.
- The sample is ~330 orders a month, so one month of results is noisy. The dashboard scales the sample up to ~1,650 COD orders a month.
- The model uses the data's own patterns (state, address, history). It cannot see a customer's real intent.
