# KAN-10: Apply a promo code at checkout

<!-- Word-for-word copy of the Jira description of KAN-10, read 2026-11-20 from
     https://naramnacakanyan77.atlassian.net/browse/KAN-10, so the read-only judges can check against it. -->

## Why

Marketing wants to run simple percentage promotions. Today there is no way for a customer to get a discount at checkout.

## User story

As a customer, I want to apply a promo code at checkout so that I pay less.

## What success looks like

1. On the checkout page, the customer can enter a promo code and press **Apply**.
2. A valid code reduces the subtotal by the code's percentage. The discount and the new total are shown before the order is placed.
3. A code works only if the subtotal is at least the code's minimum order amount. Otherwise the page shows "Add {missing amount} AMD more to use this code".
4. An expired code shows "This code has expired".
5. An unknown code shows "Code not found".
6. One code per order. Applying a second code replaces the first.
7. The customer can remove the applied code, and the total goes back to the original.

## Launch codes

| Code | Discount | Minimum order | Status |
| --- | --- | --- | --- |
| SAVE10 | 10% | 3,000 AMD | valid |
| TREAT15 | 15% | 8,000 AMD | valid |
| OLD15 | 15% | none | expired |

## Not in this release

* Free-delivery codes
* Chef-specific codes
* First-order-only codes

## Breakdown

1. Backend: promo validation and discount calculation
2. Checkout UI: promo field, messages, remove link
