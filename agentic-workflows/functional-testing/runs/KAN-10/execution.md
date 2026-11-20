# Execution: KAN-10

SUT: https://foodme-nairamnatsakanyan.onrender.com/ · cases approved 2026-11-20T14:44:02Z

## Run plan

- KAN-10-TC-07: the result can depend on state the server keeps about codes applied earlier by the same customer (earlier cases apply SAVE10 with the same account), not only on this case's steps.
- KAN-10-TC-08: same risk as TC-07: an earlier SAVE10 application by the same customer could affect what OLD15 does.
- KAN-10-TC-10: "Change the delivery method to Takeaway" after applying a code; the case doesn't say whether the code must be applied again, so I have to read what the page does on its own.
- KAN-10-TC-11: places a real order on the live site, which can't be undone, so I ask the human first; the placed order's total is on another page (Track order).
- KAN-10-TC-12: needs the checkout signed out. I must not sign out on a deployed host, so the human has to sign out (or the case is blocked).

## KAN-10-TC-01

- Started: 2026-11-20T14:52:38Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓
- Expected (from spec.yaml): The page shows "Add 1,000 AMD more to use this code"; no discount is applied and the total stays 2,500 AMD (2,000 + 500 delivery)
- Observed: alert "Add 1,000 AMD more to use this code"; Subtotal 2,000 AMD, Delivery 500 AMD, Total 2,500 AMD; no discount row
- Result: match
- Confidence: sure

## KAN-10-TC-02

- Started: 2026-11-20T14:53:12Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓
- Expected (from spec.yaml): SAVE10 is applied: a discount of 350 AMD (10% of 3,000 + 500 delivery) and a new total of 3,150 AMD are shown
- Observed: "SAVE10 applied: 10% off"; Subtotal 3,000 AMD, Delivery 500 AMD, "Discount (SAVE10)" −350 AMD, Total 3,150 AMD
- Result: match
- Confidence: sure

## KAN-10-TC-03

- Started: 2026-11-20T14:53:56Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓
- Expected (from spec.yaml): TREAT15 is applied: a discount of 1,282 AMD (15% of 8,550, delivery free, 1,282.5 rounded down) and a new total of 7,268 AMD are shown
- Observed: "TREAT15 applied: 15% off"; Subtotal 8,550 AMD, Delivery Free, "Discount (TREAT15)" −1,633 AMD, Total 6,917 AMD. Retry (reloaded checkout, applied again): same −1,633 AMD / 6,917 AMD.
- Network: POST /api/customer/promo/apply 200 at 2026-11-20T14:54:13Z, request {"code":"TREAT15","chefId":17,"subtotal":8550,"deliveryMethod":"DELIVERY"}, response {"code":"TREAT15","status":"APPLIED","percent":15,"missingAmount":null,"discount":1633.0,"deliveryPrice":0.0,"total":6917.0}
- Evidence: evidence/kan-10-tc-03.png
- Result: mismatch
- Confidence: unsure: 1,633 equals 1,283 (15% of 8,550 rounded up, not down) plus the 350 AMD SAVE10 discount the same customer got in TC-02, so the value may come from server state left by TC-02; a human should check whether a customer with no earlier promo gets 1,282 AMD.
- Human check: mismatch by Admin at 2026-11-20T15:06:31Z: 

## KAN-10-TC-04

- Started: 2026-11-20T14:54:55Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓
- Expected (from spec.yaml): The page shows "This code has expired"; no discount is applied and the total stays 3,500 AMD (3,000 + 500 delivery)
- Observed: alert "This code has expired"; Subtotal 3,000 AMD, Delivery 500 AMD, Total 3,500 AMD; no discount row
- Result: match
- Confidence: sure

## KAN-10-TC-05

- Started: 2026-11-20T14:55:20Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓
- Expected (from spec.yaml): The page shows "Code not found"; no discount is applied and the total stays 3,500 AMD (3,000 + 500 delivery)
- Observed: alert "Code not found"; Subtotal 3,000 AMD, Delivery 500 AMD, Total 3,500 AMD; no discount row
- Result: match
- Confidence: sure

## KAN-10-TC-06

- Started: 2026-11-20T14:55:46Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓
- Expected (from spec.yaml): The page shows "Code not found"; no discount is applied and the total stays 3,500 AMD (3,000 + 500 delivery)
- Observed: no "Code not found"; instead "SAVE10 applied: 10% off"; Subtotal 3,000 AMD, Delivery 500 AMD, "Discount (SAVE10)" −1,983 AMD, Total 1,517 AMD. Retry (reloaded checkout, applied save10 again): same.
- Network: POST /api/customer/promo/apply 200 at 2026-11-20T14:56:03Z, response {"code":"SAVE10","status":"APPLIED","percent":10,"missingAmount":null,"discount":1983.0,"deliveryPrice":500.0,"total":1517.0}
- Evidence: evidence/kan-10-tc-06.png
- Result: mismatch
- Confidence: sure (the lower-case code was accepted, which the expected result rules out; read from the DOM twice). Note: the 1,983 AMD discount (= 350 + TC-03's 1,633) also depends on state left by earlier cases.

## KAN-10-TC-07

- Started: 2026-11-20T14:56:42Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓ 6 ✓
- Expected (from spec.yaml): Only TREAT15 is applied: the discount is 1,282 AMD (15% of 8,550, rounded down), not 2,137 AMD, and the new total is 7,268 AMD
- Observed: after SAVE10: "SAVE10 applied: 10% off", "Discount (SAVE10)" −855 AMD, Total 7,695 AMD. After TREAT15: "TREAT15 applied: 15% off", Subtotal 8,550 AMD, Delivery Free, "Discount (TREAT15)" −2,138 AMD, Total 6,412 AMD. Retry (reloaded checkout, SAVE10 then TREAT15 again): "Discount (TREAT15)" −4,276 AMD, Total 4,274 AMD.
- Network: POST /api/customer/promo/apply 200 at 2026-11-20T14:56:53Z, response {"code":"TREAT15","status":"APPLIED","percent":15,"missingAmount":null,"discount":2138.0,"deliveryPrice":0.0,"total":6412.0}
- Evidence: evidence/kan-10-tc-07.png (first attempt)
- Result: mismatch
- Confidence: unsure: the retry gave a different discount (−4,276 instead of −2,138), so the exact value depends on server state left by earlier applications for this customer; both are far from the expected 1,282 AMD. A human should check with a fresh customer whether SAVE10 then TREAT15 gives 1,282 AMD.
- Human check: unsure (can't check now) by Admin at 2026-11-20T15:06:31Z: 

## KAN-10-TC-08

- Started: 2026-11-20T14:57:37Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓
- Expected (from spec.yaml): The page shows "This code has expired", and SAVE10 stays applied: the discount is still 350 AMD and the total is still 3,150 AMD
- Observed: after SAVE10: "SAVE10 applied: 10% off", "Discount (SAVE10)" −4,626 AMD, Total "-1,126 AMD" (a negative total). After OLD15: alert "This code has expired"; no "SAVE10 applied" line, no discount row, Subtotal 3,000 AMD, Delivery 500 AMD, Total 3,500 AMD. Retry (reloaded checkout, SAVE10 then OLD15): same end state, SAVE10 no longer applied.
- Evidence: evidence/kan-10-tc-08-negative-total.png (the SAVE10 step)
- Result: mismatch
- Confidence: sure (after OLD15 the page shows no applied code and no discount, read twice; the expected result says SAVE10 stays applied). Note: the SAVE10 discount before OLD15 was already wrong (−4,626 AMD, from server state left by earlier cases).

## KAN-10-TC-09

- Started: 2026-11-20T14:58:27Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓
- Expected (from spec.yaml): No code is applied, no discount is shown, and the total is back to 3,500 AMD (3,000 + 500 delivery)
- Observed: after "Remove promo code": the promo field is empty, no applied-code line, no discount row; Subtotal 3,000 AMD, Delivery 500 AMD, Total 3,500 AMD
- Result: match
- Confidence: sure

## KAN-10-TC-10

- Started: 2026-11-20T14:58:54Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓
- Expected (from spec.yaml): SAVE10 stays applied with the discount recalculated: a discount of 300 AMD (10% of 3,000, no delivery fee) and a new total of 2,700 AMD
- Observed: after choosing Takeaway (no re-apply needed; the page sent POST /api/order/delivery-price and POST /api/customer/promo/apply again by itself): "SAVE10 applied: 10% off"; Subtotal 3,000 AMD, Delivery Free, "Discount (SAVE10)" −300 AMD, Total 2,700 AMD
- Result: match
- Confidence: sure

## KAN-10-TC-11

- Started: 2026-11-20T15:00:17Z
- Tool: playwright-mcp
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✓ 5 ✓ 6 ✓ (the human approved placing the order in chat before step 5)
- Expected (from spec.yaml): Checkout shows a discount of 300 AMD and a total of 2,700 AMD before placing, and the placed order's total is 2,700 AMD
- Observed: before placing: Takeaway pressed, "SAVE10 applied: 10% off", Subtotal 3,000 AMD, Delivery Free, "Discount (SAVE10)" −300 AMD, Total 2,700 AMD. Placed with note "STLC KAN-10 KAN-10-TC-11" (receiver fields prefilled from the signed-in account): "Order placed!", order FM-100001. Track order (/tracking/FM-100001): "3×Tart with cherry 3,000 AMD", "Total 2,700 AMD".
- Network: POST /api/order 200 at 2026-11-20T15:00:33Z
- Result: match
- Confidence: sure

## KAN-10-TC-12

- Started: 2026-11-20T15:01:04Z
- Tool: playwright-mcp (a separate isolated browser context "kan10-guest", which starts signed out; nobody signed out)
- Steps: 1 ✓ 2 ✓ 3 ✓ 4 ✗ (there is no promo code field or Apply button for a guest)
- Expected (from spec.yaml): No discount is applied while signed out; the total stays 3,500 AMD (3,000 + 500 delivery)
- Observed: the header shows "Sign in" (signed out). The cart on /chef/17 showed 3 × Tart with cherry, Subtotal 3,000 AMD, Delivery 500 AMD, Total 3,500 AMD. /checkout as a guest shows only "Account — Sign in or create an account so you can track this order and see your history later." with Sign in / Create account tabs; no promo code field, no Apply button, no order summary or total.
- Evidence: evidence/kan-10-tc-12.png
- Result: match
- Confidence: unsure: a guest can't reach a promo code field at all, so no discount can be applied, but the checkout page shows no total, so "the total stays 3,500 AMD" was only seen in the chef page's cart, not on checkout. A human should decide whether this counts as a match.
- Human check: match by Admin at 2026-11-20T15:06:31Z: 
