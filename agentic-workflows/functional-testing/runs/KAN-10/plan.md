# Test plan: KAN-10 Apply a promo code at checkout

SUT: https://foodme-nairamnatsakanyan.onrender.com/ · requirements approved 2026-11-20 (spec.yaml, Q-1 to Q-8 answered)

## 1. Scope

- **In scope:** AC-1 to AC-7, with the answers to Q-1 to Q-8 as part of the requirements.
- **Out of scope (spec.yaml):** free-delivery codes, chef-specific codes, first-order-only codes.
- **Also not tested:**
  - Changing the cart's contents after a code is applied (Q-5, "below the minimum"): the checkout page has no quantity control, so the only way is to leave checkout and edit the cart. Q-5 is covered through a delivery-method change instead (TC-10).
  - Admin views of the order: not in any AC.
  - Lower-case code at its minimum only once (TC-06); other spellings (spaces) would be the same partition.

## 2. Approach per AC

| AC | Partitions / boundaries | Technique | Level | Cases planned |
|---|---|---|---|---|
| AC-1 | signed-in customer can enter and apply (Q-8); guest can't | equivalence partition | manual 05 + automated 06 | TC-02 (signed in), TC-12 (guest) |
| AC-2 | discount on subtotal + delivery (Q-1); fractional discount rounded down (Q-2); placed order charged the discounted total (Q-7) | equivalence partition, arithmetic | manual 05 + automated 06 | TC-02, TC-03, TC-11 |
| AC-3 | subtotal below SAVE10's 3,000 minimum; exactly at 3,000 | boundary value (below / at) | manual 05 + automated 06 | TC-01 (below, message with comma per Q-3), TC-02 (at) |
| AC-4 | expired code OLD15 | equivalence partition | manual 05 + automated 06 | TC-04 |
| AC-5 | unknown code; lower-case launch code (Q-6: exact match) | equivalence partition | manual 05 + automated 06 | TC-05, TC-06 |
| AC-6 | valid → valid replaces (no stacking); valid → invalid keeps the first (Q-4) | state transition (apply → replace) | manual 05 + automated 06 | TC-07, TC-08 |
| AC-7 | applied → removed: total back to the original | state transition (apply → remove) | manual 05 + automated 06 | TC-09 |
| (Q-5) | applied code re-checked when the delivery method changes | state transition | manual 05 + automated 06 | TC-10 |

12 cases. Expected values come from the ACs, the launch codes and Q-1 to Q-8, never from what the app shows.

## 3. Test data

Chef: **Chef Verona** (`/chef/17`). Delivery fee **500 AMD**, free delivery from a **5,000 AMD** subtotal (live API, `deliveryPrice` / `freeDeliveryFrom`). Dishes, from `GET /api/dish/17/active`:

- **Tart with cherry**: 1,000 AMD
- **Pie with beans**: 550 AMD

| Cart | Subtotal | Delivery | Total before code | Code | Discount (Q-1: on subtotal + delivery; Q-2: round down) | New total |
|---|---|---|---|---|---|---|
| A: 2 × Tart with cherry | 2,000 | 500 | 2,500 | SAVE10 | none: 3,000 − 2,000 = 1,000 missing → "Add 1,000 AMD more to use this code" | 2,500 |
| B: 3 × Tart with cherry | 3,000 | 500 | 3,500 | SAVE10 | 10% × 3,500 = 350 | 3,150 |
| B, takeaway | 3,000 | 0 | 3,000 | SAVE10 | 10% × 3,000 = 300 | 2,700 |
| C: 8 × Tart with cherry + 1 × Pie with beans | 8,550 | 0 (≥ 5,000) | 8,550 | TREAT15 | 15% × 8,550 = 1,282.5 → 1,282 | 7,268 |
| C | 8,550 | 0 | 8,550 | SAVE10 | 10% × 8,550 = 855 | 7,695 |

Cart C after SAVE10 then TREAT15 (TC-07): only TREAT15 applies, so 1,282 off and 7,268. Stacking would give 855 + 1,282 = 2,137 off.

Codes: SAVE10 (10%, minimum 3,000 AMD), TREAT15 (15%, minimum 8,000 AMD), OLD15 (expired), `NOPE10` (unknown), `save10` (lower case).

Delivery method is **Delivery** unless a case says Takeaway. TC-11 places a takeaway order, so no address is needed. Its order note is `STLC KAN-10 KAN-10-TC-11`.

## 4. Environment

- SUT: https://foodme-nairamnatsakanyan.onrender.com/
- Step 05 browser: Playwright MCP (Claude in Chrome as fallback). Before step 05, the human signs in as a customer in that browser; the agent never types a password.
- Step 08: the Playwright spec creates its own customer per test (`createAccountAtCheckout`), on the live site.
- Noise to ignore: random 200–1500 ms API latency, a free-tier cold start of up to a minute, heartbeat errors in GlitchTip, and `/api/debug/boom`.

## 5. Risks

- **Correct app looks broken:** API latency and cold start (wait for elements, retry once); a cart or applied code left from an earlier case (empty the cart and remove the code before each case); a cart with another chef's dishes.
- **Broken app looks correct:** asserting the discount the page shows instead of the arithmetic above; checking only the checkout page and not the placed order (TC-11); applying codes in one order only, which hides stacking (TC-07).
- **Live site side effects:** TC-11 places a real order, and step 08 creates customer accounts. The human approves placing the order in step 05.

## 6. Exit criteria

- Every AC (and Q-5) is covered by at least one case.
- Every case is executed in step 05 and automated in step 06.
- Every 05 mismatch and every 08 failure is listed in the report as not triaged.
