import { test, expect, type Page } from "@playwright/test";
import { createAccountAtCheckout } from "./auth";

// KAN-5: customers cancel their own order before the kitchen accepts it.
// These tests place real orders, so they don't belong in the live-safe list.
const API = process.env.VITE_API_BASE_URL || "http://localhost:8081";

async function placeTakeawayOrderAndTrack(page: Page) {
  await page.goto("/explore");
  await page.locator("a.cc_card").first().click();
  await expect(page).toHaveURL(/\/chef\/\d+/);
  await page.locator("button.dc_card").first().click();
  await page.getByRole("button", { name: "Add to cart" }).click();
  await page.locator("aside.uc-panel").getByRole("link", { name: "Go to checkout" }).click();

  await createAccountAtCheckout(page, "Cancel Tester");
  await page.getByRole("button", { name: "Takeaway Pick up" }).click();
  const checkout = page.getByRole("form", { name: "Checkout" });
  await checkout.getByLabel("Full name").fill("Cancel Tester");
  await checkout.getByLabel("Phone").fill("+37491111222");
  await checkout.getByLabel("Email").fill("cancel-tester@example.com");
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page).toHaveURL(/\/orders\/success/);

  await page.getByRole("link", { name: "Track order" }).click();
  await expect(page).toHaveURL(/\/tracking\/FM-\d+/);
  await expect(page.getByText("Order received")).toBeVisible();
  return page.url().match(/FM-\d+/)![0];
}

test.describe("Order cancellation (KAN-5)", () => {
  test("customer cancels a received order and sees it as Cancelled", async ({ page }) => {
    const number = await placeTakeawayOrderAndTrack(page);

    await page.getByRole("button", { name: "Cancel order" }).click();
    await expect(page.getByText("Cancel this order? This can’t be undone.")).toBeVisible();
    await page.getByRole("button", { name: "Yes, cancel order" }).click();

    await expect(page.getByText("Order cancelled")).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel order" })).toHaveCount(0);

    await page.getByRole("link", { name: "All orders" }).click();
    const row = page.getByRole("listitem").filter({ hasText: number });
    await expect(row.getByText("Cancelled", { exact: true })).toBeVisible();
  });

  test("Keep order leaves the order received", async ({ page }) => {
    await placeTakeawayOrderAndTrack(page);

    await page.getByRole("button", { name: "Cancel order" }).click();
    await page.getByRole("button", { name: "Keep order" }).click();

    await expect(page.getByText("Order received")).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel order" })).toBeVisible();
  });

  test("cancel option disappears once the kitchen accepts", async ({ page, request }) => {
    const number = await placeTakeawayOrderAndTrack(page);
    await expect(page.getByRole("button", { name: "Cancel order" })).toBeVisible();

    const login = await request.post(`${API}/admin/auth/login`, {
      data: { username: "admin", password: "admin123" },
    });
    expect(login.ok()).toBeTruthy();
    const { token } = await login.json();
    const headers = { Authorization: `Bearer ${token}` };
    const list = await (await request.get(`${API}/admin/order?page=0&size=50`, { headers })).json();
    const order = list.list.find((o: { number: string }) => o.number === number);
    expect(order).toBeTruthy();
    const accepted = await request.patch(`${API}/admin/order/${order.id}/status`, {
      headers,
      data: { status: "ACCEPTED" },
    });
    expect(accepted.ok()).toBeTruthy();

    await page.reload();
    await expect(page.getByText("Preparing your order")).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel order" })).toHaveCount(0);
  });
});
