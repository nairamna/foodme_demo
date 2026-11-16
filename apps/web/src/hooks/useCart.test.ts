import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db, type ICartItem } from "@/lib/db";
import {
  addDishToCart,
  clearCart,
  decrementCartItem,
  getCartChefId,
  incrementCartItem,
} from "@/hooks/useCart";
import type { DishAdditionDto, DishDto } from "@/types";

function dish(overrides: Partial<DishDto> = {}): DishDto {
  return {
    id: 1,
    nameEn: "Lavash Wrap",
    nameHy: "Lavash Wrap",
    nameRu: "Lavash Wrap",
    descriptionEn: "",
    price: 1800,
    url: "/img/dish/1.jpg",
    portionEn: "",
    portionHy: "",
    portionRu: "",
    status: "ACTIVE",
    minimumOrderCount: 1,
    priorityIndex: 0,
    chefId: 1,
    dishTagDto: { id: 1, nameEn: "Mains", nameHy: "Mains", nameRu: "Mains" },
    ...overrides,
  };
}

function addition(id: number, price: number): DishAdditionDto {
  return { id, nameEn: `Extra ${id}`, nameHy: "", nameRu: "", price };
}

function cartRow(overrides: Partial<ICartItem> = {}): ICartItem {
  return {
    id: 1,
    uid: "1-1-",
    chefId: 1,
    nameEn: "Lavash Wrap",
    price: 1800,
    url: "/img/dish/1.jpg",
    quantity: 1,
    limitations: { minQuantity: 1 },
    ...overrides,
  };
}

beforeEach(async () => {
  await db.products.clear();
});

describe("addDishToCart", () => {
  it("adds a new dish at its minimum order quantity", async () => {
    await addDishToCart(dish({ minimumOrderCount: 3 }));

    const row = await db.products.get("1-1-");
    expect(row?.quantity).toBe(3);
    expect(row?.limitations.minQuantity).toBe(3);
  });

  it("treats a missing or zero minimum as 1", async () => {
    await addDishToCart(dish({ minimumOrderCount: 0 }));

    expect((await db.products.get("1-1-"))?.quantity).toBe(1);
  });

  it("uses the requested quantity when it is above the minimum", async () => {
    await addDishToCart(dish({ minimumOrderCount: 2 }), 5);

    expect((await db.products.get("1-1-"))?.quantity).toBe(5);
  });

  it("adds to the quantity when the same dish is added again", async () => {
    await addDishToCart(dish());
    await addDishToCart(dish(), 2);

    expect(await db.products.count()).toBe(1);
    expect((await db.products.get("1-1-"))?.quantity).toBe(3);
  });

  it("prices a line as the dish plus its additions", async () => {
    await addDishToCart(dish({ additions: [addition(5, 300), addition(2, 200)] }));

    const [row] = await db.products.toArray();
    expect(row.price).toBe(2300);
  });

  it("keeps the same dish with different additions as separate lines", async () => {
    await addDishToCart(dish());
    await addDishToCart(dish({ additions: [addition(5, 300)] }));

    expect(await db.products.count()).toBe(2);
  });

  it("treats the same additions in a different order as the same line", async () => {
    await addDishToCart(dish({ additions: [addition(5, 300), addition(2, 200)] }));
    await addDishToCart(dish({ additions: [addition(2, 200), addition(5, 300)] }));

    const rows = await db.products.toArray();
    expect(rows).toHaveLength(1);
    expect(rows[0].quantity).toBe(2);
  });

  it("refuses a dish from another chef and leaves the cart alone", async () => {
    await addDishToCart(dish({ chefId: 1 }));

    const result = await addDishToCart(dish({ id: 9, chefId: 2 }));

    expect(result).toBe("mismatch");
    expect(await getCartChefId()).toBe(1);
    expect(await db.products.count()).toBe(1);
  });

  it("replaces the cart with the other chef's dish when asked to", async () => {
    await addDishToCart(dish({ chefId: 1 }));

    const result = await addDishToCart(dish({ id: 9, chefId: 2 }), 1, { replaceOtherChef: true });

    expect(result).toBe("ok");
    expect(await getCartChefId()).toBe(2);
    expect(await db.products.count()).toBe(1);
  });
});

describe("incrementCartItem", () => {
  it("raises the quantity by one", async () => {
    await db.products.add(cartRow({ quantity: 2 }));

    await incrementCartItem("1-1-");

    expect((await db.products.get("1-1-"))?.quantity).toBe(3);
  });

  it("does nothing for an item that isn't in the cart", async () => {
    await incrementCartItem("missing");

    expect(await db.products.count()).toBe(0);
  });
});

// FM-BUG-07 (KAN-4) lived here: the item disappeared one step too early.
describe("decrementCartItem", () => {
  it("lowers the quantity when it is one above the minimum", async () => {
    await db.products.add(cartRow({ quantity: 3, limitations: { minQuantity: 2 } }));

    await decrementCartItem("1-1-");

    expect((await db.products.get("1-1-"))?.quantity).toBe(2);
  });

  it("removes the item when it is at the minimum", async () => {
    await db.products.add(cartRow({ quantity: 2, limitations: { minQuantity: 2 } }));

    await decrementCartItem("1-1-");

    expect(await db.products.get("1-1-")).toBeUndefined();
  });

  it("removes a single item when the minimum is 1", async () => {
    await db.products.add(cartRow({ quantity: 1 }));

    await decrementCartItem("1-1-");

    expect(await db.products.count()).toBe(0);
  });

  it("only touches the item it was asked to change", async () => {
    await db.products.add(cartRow({ uid: "1-1-", quantity: 3 }));
    await db.products.add(cartRow({ uid: "1-2-", id: 2, quantity: 3 }));

    await decrementCartItem("1-1-");

    expect((await db.products.get("1-2-"))?.quantity).toBe(3);
  });
});

describe("clearCart and getCartChefId", () => {
  it("reports no chef for an empty cart", async () => {
    expect(await getCartChefId()).toBeUndefined();
  });

  it("empties the cart", async () => {
    await db.products.add(cartRow());

    await clearCart();

    expect(await db.products.count()).toBe(0);
    expect(await getCartChefId()).toBeUndefined();
  });
});
