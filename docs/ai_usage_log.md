# AI usage log

IMPORTANT: this is a draft. Edit it so it matches what YOU actually did and checked. Do not submit claims you cannot explain.

## Tools used
- **Claude (chat):** read the assignment, planned the approach, analysed the CSV, wrote the Java code, tests and this documentation.
- **Claude (in the product, if you turn it on):** the address and fake-name check (`LlmJudge`).

## What AI was used for
Data analysis and findings, design of the decision rules, all Java files, the dashboard and chat pages, drafts of the logs.

## Where the AI was wrong or weak, and how it was caught
1. **Wrong discount numbers.** Early in the chat the AI said the break-even prepaid discount was "about Rs.60 at 14% risk, Rs.95 at 20%, Rs.150 at 30%". Writing `Economics.maxDiscount` forced me to use the real formula, (risk - 4%) x Rs.169 - fee, which gives only about Rs.17 before fees at 14% and Rs.44 at 30%. The earlier numbers were about 3x too high. Fixed in the code and the decision log.
2. **Confirmation threshold too low.** The AI first suggested confirming at 30% risk, using the break-even for a repeat customer. The backtest showed that bucket losing money (about -Rs.1.5k a month). Checking the maths showed 80% of orders are first-timers, whose break-even is closer to 40% because of the Rs.300 ad spend. Changed to 40%.
3. **Illogical WhatsApp message.** For the address "." the bot asked "is this address correct?". Found by running the chat and reading the message. Changed to ask for a full address.
4. **Junk address slipping through as a nudge.** A test order with the address "home" got a prepaid nudge, which would still ship COD to "home". Found with a hand-made test order. Added a "junk address or fake name always needs confirmation" rule.
5. **Tooling slips.** The AI's first folder-creation command made a literal folder named `{src/...}` (the shell did not expand it) and one cleanup command killed its own shell. Noticed because files were missing / no output came back; redone.

## What I would still check myself
- Re-run every number in `data_findings.md` in Excel or a quick pivot table.
- Test the live AI call with a real key on 50 addresses and compare to the built-in rules.
