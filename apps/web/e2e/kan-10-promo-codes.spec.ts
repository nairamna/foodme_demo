import { test, expect, type Page } from "@playwright/test";
import { createAccountAtCheckout } from "./auth";

// KAN-10: apply a promo code at checkout. Cases and expected values come from
// agentic-workflows/functional-testing/runs/KAN-10/spec.yaml, not from the app.
// TC-11 places a real order, so this file doesn't belong in the live-safe list.

const CHEF = "/chef/17"; // Chef Verona: delivery 500 AMD, free from a 5,000 AMD subtotal
const TART = { name: "Tart with cherry", price: 1000 };
const PIE = { name: "Pie with beans", price: 550 };

const amd = (value: number) => `${value.toLocaleString("en-US")} AMD`;
const row = (label: string, value: string) =>
  new RegExp(`${label.replace(/[()]/g, "\\$&")}\\s*${value}`);

async function addToCart(page: Page, items: { dish: typeof TART; quantity: number }[]) {
  await page.goto(CHEF);
  const cart = page.getByRole("complementary");
  let subtotal = 0;
  for (const { dish, quantity } of items) {
    for (let i = 0; i < quantity; i++) {
      await page.getByRole("button", { name: `Add ${dish.name} to cart` }).click();
      subtotal += dish.price;
      await expect(cart).toContainText(row("Subtotal", amd(subtotal)));
    }
  }
}

async function checkoutSignedIn(page: Page, name: string, method: "Delivery" | "Takeaway") {
  await page.getByRole("complementary").getByRole("link", { name: "Go to checkout" }).click();
  await createAccountAtCheckout(page, name);
  await page
    .getByRole("button", { name: method === "Delivery" ? "Delivery To your door" : "Takeaway Pick up" })
    .click();
}

async function applyCode(page: Page, code: string) {
  await page.getByRole("textbox", { name: "Promo code" }).fill(code);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
}

async function expectApplied(page: Page) {
  await expect(page.getByRole("button", { name: "Remove promo code" })).toBeVisible();
}

const summary = (page: Page) => page.getByRole("complementary");
const discountRows = (page: Page) => summary(page).getByText(/^Discount \(/);

test.describe("Promo codes at checkout (KAN-10)", () => {
  test("KAN-10-TC-01: SAVE10 with a 2,000 AMD subtotal is refused with the missing amount", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 2 }]);
    await checkoutSignedIn(page, "Promo TC01", "Delivery");
    await applyCode(page, "SAVE10");

    await expect(page.getByText("Add 1,000 AMD more to use this code", { exact: true })).toBeVisible();
    await expect(discountRows(page)).toHaveCount(0);
    await expect(summary(page)).toContainText(row("Total", amd(2500)));
  });

  test("KAN-10-TC-02: SAVE10 at exactly its 3,000 AMD minimum takes 10% of subtotal plus delivery", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC02", "Delivery");
    await applyCode(page, "SAVE10");

    await expect(summary(page)).toContainText(row("Discount (SAVE10)", `−${amd(350)}`));
    await expect(summary(page)).toContainText(row("Total", amd(3150)));
  });

  test("KAN-10-TC-03: TREAT15 on an 8,550 AMD subtotal rounds the 1,282.5 AMD discount down", async ({ page }) => {
    await addToCart(page, [
      { dish: TART, quantity: 8 },
      { dish: PIE, quantity: 1 },
    ]);
    await checkoutSignedIn(page, "Promo TC03", "Delivery");
    await applyCode(page, "TREAT15");

    await expect(summary(page)).toContainText(row("Discount (TREAT15)", `−${amd(1282)}`));
    await expect(summary(page)).toContainText(row("Total", amd(7268)));
  });

  test("KAN-10-TC-04: Expired code OLD15 is refused", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC04", "Delivery");
    await applyCode(page, "OLD15");

    await expect(page.getByText("This code has expired", { exact: true })).toBeVisible();
    await expect(discountRows(page)).toHaveCount(0);
    await expect(summary(page)).toContainText(row("Total", amd(3500)));
  });

  test("KAN-10-TC-05: Unknown code NOPE10 is refused", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC05", "Delivery");
    await applyCode(page, "NOPE10");

    await expect(page.getByText("Code not found", { exact: true })).toBeVisible();
    await expect(discountRows(page)).toHaveCount(0);
    await expect(summary(page)).toContainText(row("Total", amd(3500)));
  });

  test("KAN-10-TC-06: Lower-case save10 is treated as an unknown code", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC06", "Delivery");
    await applyCode(page, "save10");

    await expect(page.getByText("Code not found", { exact: true })).toBeVisible();
    await expect(discountRows(page)).toHaveCount(0);
    await expect(summary(page)).toContainText(row("Total", amd(3500)));
  });

  test("KAN-10-TC-07: TREAT15 applied after SAVE10 replaces it instead of adding to it", async ({ page }) => {
    await addToCart(page, [
      { dish: TART, quantity: 8 },
      { dish: PIE, quantity: 1 },
    ]);
    await checkoutSignedIn(page, "Promo TC07", "Delivery");
    await applyCode(page, "SAVE10");
    await expectApplied(page);
    await applyCode(page, "TREAT15");

    await expect(discountRows(page)).toHaveCount(1);
    await expect(summary(page)).toContainText(row("Discount (TREAT15)", `−${amd(1282)}`));
    await expect(summary(page)).toContainText(row("Total", amd(7268)));
  });

  test("KAN-10-TC-08: An expired second code leaves SAVE10 applied", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC08", "Delivery");
    await applyCode(page, "SAVE10");
    await expectApplied(page);
    await applyCode(page, "OLD15");

    await expect(page.getByText("This code has expired", { exact: true })).toBeVisible();
    await expect(summary(page)).toContainText(row("Discount (SAVE10)", `−${amd(350)}`));
    await expect(summary(page)).toContainText(row("Total", amd(3150)));
  });

  test("KAN-10-TC-09: Removing SAVE10 brings the total back to the original", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC09", "Delivery");
    await applyCode(page, "SAVE10");
    await expectApplied(page);
    await page.getByRole("button", { name: "Remove promo code" }).click();

    await expect(page.getByRole("button", { name: "Remove promo code" })).toHaveCount(0);
    await expect(discountRows(page)).toHaveCount(0);
    await expect(summary(page)).toContainText(row("Total", amd(3500)));
  });

  test("KAN-10-TC-10: Switching to Takeaway re-checks SAVE10 and recalculates the discount", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC10", "Delivery");
    await applyCode(page, "SAVE10");
    await expectApplied(page);
    await page.getByRole("button", { name: "Takeaway Pick up" }).click();

    await expect(summary(page)).toContainText(row("Discount (SAVE10)", `−${amd(300)}`));
    await expect(summary(page)).toContainText(row("Total", amd(2700)));
  });

  test("KAN-10-TC-11: A takeaway order placed with SAVE10 is charged the discounted total", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await checkoutSignedIn(page, "Promo TC11", "Takeaway");
    await applyCode(page, "SAVE10");

    await expect(summary(page)).toContainText(row("Discount (SAVE10)", `−${amd(300)}`));
    await expect(summary(page)).toContainText(row("Total", amd(2700)));

    const checkout = page.getByRole("form", { name: "Checkout" });
    await checkout.getByLabel("Full name").fill("Promo TC11");
    await checkout.getByLabel("Phone").fill("+37491111222");
    await checkout.getByLabel("Email").fill("promo-tc11@example.com");
    await page.getByRole("textbox", { name: "Allergies, instructions, gate code..." }).fill("STLC KAN-10 KAN-10-TC-11");
    await page.getByRole("button", { name: "Place order" }).click();
    await page.getByRole("link", { name: "Track order" }).click();

    await expect(page.getByRole("main")).toContainText(row("Total", amd(2700)));
  });

  test("KAN-10-TC-12: A guest at checkout cannot apply SAVE10", async ({ page }) => {
    await addToCart(page, [{ dish: TART, quantity: 3 }]);
    await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible();
    await page.getByRole("complementary").getByRole("link", { name: "Go to checkout" }).click();

    await expect(page.getByRole("textbox", { name: "Promo code" })).toHaveCount(0);
    await expect(page.getByText(/^Discount \(/)).toHaveCount(0);

    await page.goto(CHEF);
    await expect(page.getByRole("complementary")).toContainText(row("Total", amd(3500)));
  });
});
