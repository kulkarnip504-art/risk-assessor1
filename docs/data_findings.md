# What the data shows (verify these yourself in Excel before using them in the memo)

Raw file: 2,018 rows. After cleaning: 1,984 usable orders. Overall RTO rate about 28%, matching the brief.

## Data problems found and fixed
- 30 exact duplicate rows (same order id, same content) removed.
- Status written 4 ways (`RTO`, `Rto`, `delivered`, `DELIVERED`): made consistent. 1 order `IN_TRANSIT` removed (outcome unknown).
- 3 orders with value Rs.0 removed. 12 missing pincodes, 4 missing cities.
- 32 phone numbers not 10 digits (17 with 9 digits, 15 with 15 digits). 77 addresses of 3 characters or less (".", "xyz", "home", "call me").
- Pincode was read as a decimal number; kept as text.

## Findings (RTO rate; n = orders)
1. **Junk addresses are the clearest signal.** Addresses of 20 characters or less: 62% RTO (n=183) vs 25% for the rest. Fake names ("Test Order", "asdf", "qwerty"): 66% (n=35).
2. **It is the state, not the courier.** Among orders with a usable address: Bihar 52% (n=132), Uttar Pradesh 39%, Assam 36%, Madhya Pradesh 34%, versus Maharashtra 14% (n=342). All five couriers sit between 23% and 27%.
3. **Customers who already returned twice are almost sure RTOs.** 2 earlier RTOs: 76% (n=29). 1 earlier RTO: 35% (n=100). None: 23%.
4. **Marketing is partly right.** First-time buyers: 26% vs 21% for repeat customers, a smaller gap than expected. Meta-ads first-timers 30% vs direct first-timers 19%, so some of the Meta risk is real, but most first-timers with a good address in a safe state are fine.
5. **Big baskets return more.** Orders above Rs.1,500: 37%, vs about 20% for Rs.700 to Rs.1,500.
6. **Landmark-only addresses (no house number)** are mildly worse: 30% (n=391) vs 24%. A weak signal, and a place where the AI reading Hinglish can add something.
7. **Time of day and number of items barely matter.** Do not build rules on them.
8. **No pincode has enough orders (about 10 or more) to judge on its own.** "Stop COD in bad pincodes" cannot be supported by this data; use state until you have more history.
