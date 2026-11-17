import { test, expect, type APIRequestContext, type Page } from "@playwright/test";
import { createAccountAtCheckout } from "./auth";

// KAN-9: customers rate their delivered orders from My Orders.
// These tests place real orders, so they don't belong in the live-safe list.
const API = process.env.VITE_API_BASE_URL || "http://localhost:8081";

async function placeTakeawayOrder(page: Page) {
  await page.goto("/explore");
  await page.locator("a.cc_card").first().click();
  await expect(page).toHaveURL(/\/chef\/\d+/);
  await page.locator("button.dc_card").first().click();
  await page.getByRole("button", { name: "Add to cart" }).click();
  await page.locator("aside.uc-panel").getByRole("link", { name: "Go to checkout" }).click();

  await createAccountAtCheckout(page, "Rating Tester");
  await page.getByRole("button", { name: "Takeaway Pick up" }).click();
  const checkout = page.getByRole("form", { name: "Checkout" });
  await checkout.getByLabel("Full name").fill("Rating Tester");
  await checkout.getByLabel("Phone").fill("+37491111222");
  await checkout.getByLabel("Email").fill("rating-tester@example.com");
  await page.getByRole("button", { name: "Place order" }).click();
  await expect(page).toHaveURL(/\/orders\/success/);

  await page.getByRole("link", { name: "Track order" }).click();
  await expect(page).toHaveURL(/\/tracking\/FM-\d+/);
  return page.url().match(/FM-\d+/)![0];
}

async function adminMovesTo(request: APIRequestContext, number: string, statuses: string[]) {
  const login = await request.post(`${API}/admin/auth/login`, {
    data: { username: "admin", password: "admin123" },
  });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  const headers = { Authorization: `Bearer ${token}` };
  const list = await (await request.get(`${API}/admin/order?page=0&size=50`, { headers })).json();
  const order = list.list.find((o: { number: string }) => o.number === number);
  expect(order).toBeTruthy();
  for (const status of statuses) {
    const res = await request.patch(`${API}/admin/order/${order.id}/status`, {
      headers,
      data: { status },
    });
    expect(res.ok()).toBeTruthy();
  }
}

test.describe("Order ratings (KAN-9)", () => {
  test("customer rates a delivered order and still sees it after a reload", async ({ page, request }) => {
    const number = await placeTakeawayOrder(page);
    await adminMovesTo(request, number, ["ACCEPTED", "DELIVERED"]);

    await page.goto("/orders");
    const row = page.getByTestId(`order-${number}`);
    await row.getByRole("button", { name: "Rate order" }).click();

    const dialog = page.getByRole("dialog", { name: `Rate order ${number}` });
    await expect(dialog.getByPlaceholder("Tell us about your order")).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Submit rating" })).toBeDisabled();
    await dialog.getByRole("radio", { name: "4 stars" }).check({ force: true });
    await dialog.getByPlaceholder("Tell us about your order").fill("Warm and tasty");
    await dialog.getByRole("button", { name: "Submit rating" }).click();

    await expect(dialog).toBeHidden();
    await expect(row.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
    await expect(row.getByText("Warm and tasty")).toBeVisible();
    await expect(row.getByRole("button", { name: "Rate order" })).toHaveCount(0);

    await page.reload();
    await expect(row.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
    await expect(row.getByRole("button", { name: "Rate order" })).toHaveCount(0);

    await page.goto(`/tracking/${number}`);
    const review = page.getByTestId("order-review");
    await expect(review.getByRole("img", { name: "Rated 4 out of 5" })).toBeVisible();
    await expect(review.getByText("Warm and tasty")).toBeVisible();
  });

  test("stars can be picked with the keyboard", async ({ page, request }) => {
    const number = await placeTakeawayOrder(page);
    await adminMovesTo(request, number, ["ACCEPTED", "DELIVERED"]);

    await page.goto("/orders");
    const row = page.getByTestId(`order-${number}`);
    await row.getByRole("button", { name: "Rate order" }).click();
    const dialog = page.getByRole("dialog", { name: `Rate order ${number}` });

    await dialog.getByRole("radio", { name: "1 star" }).focus();
    await page.keyboard.press("ArrowRight");
    await page.keyboard.press("ArrowRight");
    await expect(dialog.getByRole("radio", { name: "3 stars" })).toBeChecked();
    await dialog.getByRole("button", { name: "Submit rating" }).click();

    await expect(dialog).toBeHidden();
    await expect(row.getByRole("img", { name: "Rated 3 out of 5" })).toBeVisible();
  });

  test("orders that aren't delivered have no rating option", async ({ page, request }) => {
    const received = await placeTakeawayOrder(page);
    await page.goto("/orders");
    const row = page.getByTestId(`order-${received}`);
    await expect(row.getByText("Received", { exact: true })).toBeVisible();
    await expect(row.getByRole("button", { name: "Rate order" })).toHaveCount(0);

    await adminMovesTo(request, received, ["ACCEPTED"]);
    await page.reload();
    await expect(row.getByText("Preparing", { exact: true })).toBeVisible();
    await expect(row.getByRole("button", { name: "Rate order" })).toHaveCount(0);
  });
});
