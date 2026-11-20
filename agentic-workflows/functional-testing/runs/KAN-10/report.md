# Test report: KAN-10 Apply a promo code at checkout

SUT: https://foodme-nairamnatsakanyan.onrender.com/ · run dir `agentic-workflows/functional-testing/runs/KAN-10/`

## 1. Summary

The feature is **not ready**. Unknown and expired codes, the minimum-order message, removing a code, re-checking on a delivery-method change, the placed order's total and the guest case behave as specified. Discount amounts don't: TREAT15 is rounded up instead of down (KAN-10-TC-03, 1,283 AMD instead of 1,282 in step 08), a second code adds to the first instead of replacing it (KAN-10-TC-07, 2,138 AMD instead of 1,282 in step 08; still unsure after the manual check), a lower-case "save10" is accepted (KAN-10-TC-06), and an expired second code removes an applied SAVE10 (KAN-10-TC-08). In step 05, discounts for one customer kept growing across checkouts, up to a negative checkout total (−1,126 AMD, KAN-10-TC-08). Blocking cases: KAN-10-TC-03, KAN-10-TC-06, KAN-10-TC-07, KAN-10-TC-08. Four automated tests (TC-01, TC-02, TC-04, TC-06) failed during sign-up, before their first assertion.

## 2. Coverage

| AC | Cases | Manual (05) | Automated (08) |
|---|---|---|---|
| AC-1 | TC-02, TC-12 | ✅ | ⏸ (TC-02 failed in setup; TC-12 passed) |
| AC-2 | TC-02, TC-03, TC-10, TC-11 | ❌ (TC-03) | ❌ (TC-03 failed; TC-02 failed in setup; TC-10, TC-11 passed) |
| AC-3 | TC-01, TC-02 | ✅ | ⏸ (both failed in setup) |
| AC-4 | TC-04 | ✅ | ⏸ (failed in setup) |
| AC-5 | TC-05, TC-06 | ❌ (TC-06) | ⏸ (TC-05 passed; TC-06 failed in setup) |
| AC-6 | TC-07, TC-08 | ❌ (TC-07, TC-08) | ❌ (TC-07, TC-08 failed) |
| AC-7 | TC-09 | ✅ | ✅ |

## 3. Cases

| Case | Title | 05 result | Confidence | Test title | 08 result |
|---|---|---|---|---|---|
| KAN-10-TC-01 | SAVE10 with a 2,000 AMD subtotal is refused with the missing amount | match | sure | KAN-10-TC-01: SAVE10 with a 2,000 AMD subtotal is refused with the missing amount | failed (setup) |
| KAN-10-TC-02 | SAVE10 at exactly its 3,000 AMD minimum takes 10% of subtotal plus delivery | match | sure | KAN-10-TC-02: SAVE10 at exactly its 3,000 AMD minimum takes 10% of subtotal plus delivery | failed (setup) |
| KAN-10-TC-03 | TREAT15 on an 8,550 AMD subtotal rounds the 1,282.5 AMD discount down | mismatch (checked by Admin) | unsure | KAN-10-TC-03: TREAT15 on an 8,550 AMD subtotal rounds the 1,282.5 AMD discount down | failed |
| KAN-10-TC-04 | Expired code OLD15 is refused | match | sure | KAN-10-TC-04: Expired code OLD15 is refused | failed (setup) |
| KAN-10-TC-05 | Unknown code NOPE10 is refused | match | sure | KAN-10-TC-05: Unknown code NOPE10 is refused | passed |
| KAN-10-TC-06 | Lower-case save10 is treated as an unknown code | mismatch | sure | KAN-10-TC-06: Lower-case save10 is treated as an unknown code | failed (setup) |
| KAN-10-TC-07 | TREAT15 applied after SAVE10 replaces it instead of adding to it | mismatch (human: still unsure) | unsure | KAN-10-TC-07: TREAT15 applied after SAVE10 replaces it instead of adding to it | failed |
| KAN-10-TC-08 | An expired second code leaves SAVE10 applied | mismatch | sure | KAN-10-TC-08: An expired second code leaves SAVE10 applied | failed |
| KAN-10-TC-09 | Removing SAVE10 brings the total back to the original | match | sure | KAN-10-TC-09: Removing SAVE10 brings the total back to the original | passed |
| KAN-10-TC-10 | Switching to Takeaway re-checks SAVE10 and recalculates the discount | match | sure | KAN-10-TC-10: Switching to Takeaway re-checks SAVE10 and recalculates the discount | passed |
| KAN-10-TC-11 | A takeaway order placed with SAVE10 is charged the discounted total | match | sure | KAN-10-TC-11: A takeaway order placed with SAVE10 is charged the discounted total | passed |
| KAN-10-TC-12 | A guest at checkout cannot apply SAVE10 | match (checked by Admin) | unsure | KAN-10-TC-12: A guest at checkout cannot apply SAVE10 | passed |

Step 08: 5 passed, 7 failed (results.json, one run, 60 s).

## 4. Not triaged

**05 mismatches**

- **KAN-10-TC-03.** Expected: "TREAT15 is applied: a discount of 1,282 AMD (15% of 8,550, delivery free, 1,282.5 rounded down) and a new total of 7,268 AMD are shown". Observed: "Discount (TREAT15)" −1,633 AMD, Total 6,917 AMD (twice).
- **KAN-10-TC-06.** Expected: "The page shows "Code not found"; no discount is applied and the total stays 3,500 AMD (3,000 + 500 delivery)". Observed: "SAVE10 applied: 10% off", "Discount (SAVE10)" −1,983 AMD, Total 1,517 AMD (twice).
- **KAN-10-TC-07.** Expected: "Only TREAT15 is applied: the discount is 1,282 AMD (15% of 8,550, rounded down), not 2,137 AMD, and the new total is 7,268 AMD". Observed: "Discount (TREAT15)" −2,138 AMD, Total 6,412 AMD; retry −4,276 AMD, Total 4,274 AMD.
- **KAN-10-TC-08.** Expected: "The page shows "This code has expired", and SAVE10 stays applied: the discount is still 350 AMD and the total is still 3,150 AMD". Observed: alert "This code has expired"; no applied code, no discount row, Total 3,500 AMD (twice). Before OLD15, SAVE10 showed "Discount (SAVE10)" −4,626 AMD and Total "-1,126 AMD".

**08 failures (assertions)**

- **KAN-10-TC-03:** `Expected pattern: /Discount \(TREAT15\)\s*−1,282 AMD/` · received "…Discount (TREAT15)−1,283 AMDTotal7,267 AM…".
- **KAN-10-TC-07:** `Expected pattern: /Discount \(TREAT15\)\s*−1,282 AMD/` · received "…Discount (TREAT15)−2,138 AMDTotal6,412 AM…".
- **KAN-10-TC-08:** `Expected pattern: /Discount \(SAVE10\)\s*−350 AMD/` · received "…This code has expiredSubtotal3,000 AMDDelivery500 AMDAdd 2,000 AMD more for free delivery.Total3,500 AMDPlace order".

**08 failures (setup, not app results)**

- **KAN-10-TC-01, KAN-10-TC-02, KAN-10-TC-04, KAN-10-TC-06:** `expect(locator).toBeVisible() failed` · `Locator: getByText(/Signed in as Promo TC0n/)` · `element(s) not found`, in `createAccountAtCheckout` (`e2e/auth.ts:18`), before the first promo step.

## 5. Still unsure

- **KAN-10-TC-07** (human: "Can't check now"): Discount −2,138 AMD (855 + 1,283), retry −4,276 AMD; expected 1,282; value depends on server state for this customer. In step 08, with a new customer, the test also received −2,138 AMD.

## 6. Questions answered

- Q-1 → From the subtotal plus delivery (the whole total) (Admin)
- Q-2 → Rounded down to a whole AMD (1,207.5 → 1,207) (Admin)
- Q-3 → With a thousands comma: "Add 1,200 AMD more to use this code" (Admin)
- Q-4 → The first code stays applied, and the error for the second code is shown (Admin)
- Q-5 → Re-check the code: below the minimum it stops applying and shows the AC-3 message; otherwise the discount is recalculated (Admin)
- Q-6 → Codes must match exactly: "save10" shows "Code not found" (Admin)
- Q-7 → Yes: the placed order's total equals the discounted total shown at checkout (Admin)
- Q-8 → Only signed-in customers can apply a code (Admin)

## 7. Review

- requirements: approved by Admin at 2026-11-20T14:27:59Z
- plan: approved by Admin at 2026-11-20T14:32:48Z
- cases: approved by Admin at 2026-11-20T14:44:02Z, note "judge 04-01 unsure"
- manual_check: approved by Admin at 2026-11-20T15:06:31Z, note "3 unsure, 2 confirmed, 1 still unsure"
- tests: approved by Admin at 2026-11-20T15:10:34Z, note "judge 07-01 unsure"

Judge verdicts:

- **04-01 (unsure):** C2 · spec · no question asks whether the free-delivery threshold is judged on the subtotal before or after the discount. C5 · KAN-10-TC-11 · the receiver's name, phone and email have no concrete values.
- **07-01 (unsure):** T4 · KAN-10-TC-12 · the test asserts the promo field is absent (no case says so) and checks the total on the chef page instead of at checkout.

## 8. Not tested

- Out of scope: Free-delivery codes; Chef-specific codes; First-order-only codes.
- From the plan: changing the cart's contents after a code is applied (the checkout page has no quantity control; Q-5 covered by TC-10 instead), and admin views of the order.
- Blocked cases: none.

## 9. Found issues

| Id | Title | Cases | Status |
|---|---|---|---|
| KAN-10-ISSUE-01 | Bug: TREAT15 gives the wrong discount (1,633 AMD on an 8,550 AMD order, 1,283 for a new customer) instead of 1,282 AMD | KAN-10-TC-03 | proposed |
| KAN-10-ISSUE-02 | Bug: lower-case "save10" is accepted as SAVE10, with a 1,983 AMD discount on a 3,500 AMD order | KAN-10-TC-06 | proposed |
| KAN-10-ISSUE-03 | Bug: entering an expired code removes an applied SAVE10 discount | KAN-10-TC-08 | proposed |

## 10. Qase

- Project `FOODME`, suite 1 "KAN-10 Apply a promo code at checkout", cases 1–12 (`qase_id` in `spec.yaml`).
- Run 1 "KAN-10 STLC 2026-11-20" (completed): 5 passed, 7 failed, 0 blocked · https://app.qase.io/run/FOODME/dashboard/1

## 11. Found issues: Jira

| Id | Jira key | Priority | Sprint | Status |
|---|---|---|---|---|
| KAN-10-ISSUE-01 | [KAN-12](https://naramnacakanyan77.atlassian.net/browse/KAN-12) | Highest | not assigned (no active sprint in KAN) | filed |
| KAN-10-ISSUE-02 | [KAN-13](https://naramnacakanyan77.atlassian.net/browse/KAN-13) | Highest | not assigned (no active sprint in KAN) | filed |
| KAN-10-ISSUE-03 | [KAN-14](https://naramnacakanyan77.atlassian.net/browse/KAN-14) | Medium | not assigned (no active sprint in KAN) | filed |
