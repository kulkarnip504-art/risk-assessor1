# Decision log

Each line: the choice, what else I considered, why.

## Language and tools
- **Plain Java, no libraries, no Maven.** Considered Spring Boot + Maven (standard), Python (faster for data). Chose plain Java because I know Java, a reviewer needs only a JDK, and there is nothing to download or break in a 10-minute setup. Cost: I wrote a small CSV reader, JSON writer and web server myself.
- **A small logistic regression I wrote myself** instead of a library or a black-box model. Considered XGBoost / a rules-only table. Chose this because every decision needs a plain-English reason, and this lets me say "Bihar +13 pts, short address +26 pts". It scored AUC 0.73 on unseen data, which is decent, not great.

## Data
- **Removed exact duplicates, normalised status, dropped Rs.0 and in-transit orders** from training. Alternative: keep them. Rejected because they would teach the model wrong answers.
- **Time split, not random split.** Trained on orders before 1 Sep, tested on September. A random split would let the model "see the future" and look better than it is.
- **No pincode rules.** Operations asked for them. Considered a pincode block list; no pincode has enough orders. Used state instead and flagged the gap.
- **Courier, hour, item count left out of the model.** They did not move RTO in the data.

## Where the LLM is used
- **LLM only for text judgement:** address quality (GOOD / VAGUE / POOR, Hinglish), fake name. Everything numeric is code. Alternative: ask the LLM to decide the whole action. Rejected: slow, costs money per order, cannot be audited, and a threshold is a one-line comparison.
- **Fixed answer format; anything else is rejected and falls back to rules.** The customer's text is passed as data. Protects against odd or malicious addresses.
- **Rules fallback always exists** so the system never stops if the AI is down or the key is missing.
- **Not tested live** (no API key while building). Still a gap.

## Thresholds and the Rupee maths
- **Break-even drives the defaults.** One RTO costs Rs.65+65+25+(8% x 180) = **Rs.169**. A genuine delivered Rs.649 order earns about Rs.402 (62%). So cancelling pays off only above **~70%** RTO risk (~81% for a first-timer once Marketing's Rs.300 ad spend is counted). That is the answer to Operations in Rupees.
- **Hold is rare on purpose** (about 25 of 1,650 orders). Almost nothing is risky enough to cancel outright.
- **Confirmation threshold 40%, not 30%.** First pass used 30% (break-even for a repeat customer). But 80% of orders are first-timers, whose break-even is nearer 40%, and the backtest at 30% lost money in that bucket. I chose 40% from the maths, **not** by picking the best number on September (that would overfit one month).
- **Nudge from 25%.** Nudge never blocks an order (no reply or "COD" still ships), so it is cheap. At 25% risk a Rs.20 discount plus a 2% gateway fee is still covered by the saving.
- **Junk address or fake name always goes to confirmation,** even if the score is low. Found while testing: a nudge on the address "home" would still ship COD to "home". Switchable in config.
- **Customers with 2+ earlier RTOs are held** (76% RTO) with a "prepaid only" message, not silently cancelled.
- **Marketing's Rs.300 is counted as extra loss on first-time cancellations.** Strictly it is sunk cost; I counted it anyway so the maths is not tilted toward Operations. Even with it set to 0 the plan stays positive and the "cancel everything risky" plan stays negative.

## WhatsApp flow
- **Hinglish, within minutes, one question.** Reply YES / NO. Random text: ask once more, then a human calls (never guess). One reminder at 12h.
- **24h of silence: Confirm orders are cancelled; Nudge orders ship as COD.** A confirmation is the only protection on a risky order; a nudge must never cost a sale.
- **Junk address: ask for the full address instead of "is this correct?".** Found by testing: asking to confirm "." made no sense.
- **Prepaid discount is tiered by the maths, Rs.20 to Rs.60.** Formula: max affordable discount = (risk - 4%) x Rs.169 - 2% fee. We give at most 60% of that, rounded to Rs.5. At 25% risk the whole saving is only about Rs.35 on a Rs.649 order, so a flat Rs.50 would lose money on the borderline customers.

## Dashboard
- **Numbers are estimates, made before outcomes are known**, and labelled so. Sample scaled to about 1,650 COD orders a month.
- **Default day is the last day in the data** (the sample ends 30 Sep; there is no "yesterday").

## Known weaknesses (not fixed)
- Confirmation catch rate (50%), loss rate (8%) and nudge acceptance (25%) are **my assumptions**, not data. Run a small real test.
- Results from one month of about 330 sample orders are noisy.
- Prepaid RTO of 4% may be higher for customers we convert.
- The live AI path is untested.
