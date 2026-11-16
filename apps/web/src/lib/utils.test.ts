import { describe, expect, it } from "vitest";
import { formatAmd, sameLabel, translate } from "@/lib/utils";

describe("formatAmd", () => {
  it("adds thousands separators and the currency", () => {
    expect(formatAmd(12500)).toBe("12,500 AMD");
  });

  it("rounds to whole drams", () => {
    expect(formatAmd(1799.6)).toBe("1,800 AMD");
    expect(formatAmd(1799.4)).toBe("1,799 AMD");
  });

  it("formats zero", () => {
    expect(formatAmd(0)).toBe("0 AMD");
  });
});

describe("translate", () => {
  const name = [
    { lang: "en", value: "Marta's Kitchen" },
    { lang: "hy", value: "Մարտայի խոհանոց" },
  ];

  it("returns the English value by default", () => {
    expect(translate(name)).toBe("Marta's Kitchen");
  });

  it("returns the requested language", () => {
    expect(translate(name, "hy")).toBe("Մարտայի խոհանոց");
  });

  it("falls back to the first value when the language is missing", () => {
    expect(translate(name, "ru")).toBe("Marta's Kitchen");
  });

  it("returns an empty string for no list or an empty list", () => {
    expect(translate(undefined)).toBe("");
    expect(translate([])).toBe("");
  });
});

describe("sameLabel", () => {
  it("ignores case and accents", () => {
    expect(sameLabel("Desserts", "desserts")).toBe(true);
    expect(sameLabel("Crème", "creme")).toBe(true);
  });

  it("tells different labels apart", () => {
    expect(sameLabel("Mains", "Drinks")).toBe(false);
  });
});
