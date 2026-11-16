# Setting up Vitest in apps/web

Done on 2026-11-16: `vitest` and `fake-indexeddb` are in `apps/web/package.json`.
This file records how, in case it has to be redone.

## 1. Install

From `apps/web`:

```bash
npm i -D vitest@5.0.5 fake-indexeddb@6.2.7 --legacy-peer-deps
```

`--legacy-peer-deps` works around an npm 11.5 crash ("Cannot read properties of
null (reading 'edgesOut')") while it resolves Vitest's optional peers. It's
only needed for that install: a later plain `npm ci` from the lockfile works.

`fake-indexeddb` gives Node an in-memory IndexedDB, which the Dexie cart in
`src/lib/db.ts` needs.

## 2. Add the script

```bash
npm pkg set scripts.test="vitest run --dir src"
```

`--dir src` matters. Without it Vitest also collects the Playwright specs in
`e2e/` and fails on every one, because they import `@playwright/test`.

There's no separate Vitest config: Vitest reads `vite.config.ts`, so the `@/`
alias already works.

## 3. Check it

```bash
npm test
npm run build
```

Both should pass. `npm run build` runs `tsc -b`, which type-checks test files
in `src/` along with the app.

## Verified

This setup was tried on 2026-11-06 with Vite 8, Vitest 5.1 and
fake-indexeddb 6.2: `npm test`, `npm run build` and `npm run lint` all passed,
and a cart test failed against the FM-BUG-07 version of `decrementCartItem`
and passed against the fixed one.
