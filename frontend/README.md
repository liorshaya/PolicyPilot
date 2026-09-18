# PolicyPilot frontend

React 19, TypeScript (strict), Vite, TanStack Query; Vitest with Testing Library and MSW, Playwright; ESLint
and Prettier. Deployed to Vercel from this folder (`vercel.json` carries the SPA rewrite). The design is
Document 2, "Frontend Architecture"; this file only explains the layout and the commands.

## Layout

```
frontend/
  src/
    main.tsx                   React root with the TanStack Query provider and the fonts (Inter, Heebo)
    App.tsx                    day 1: the access gate and nothing else
    index.css                  design tokens, RTL rule for Hebrew content blocks
    api/                       API base URL today; the generated client, sse.ts and the code exchange from days 4 and 6
    features/<name>/           policy, rules, decide, chat, change, audit (from day 6)
    shared/                    layout, access gate, provider badge, i18n, RTL utilities (gate today)
    test/                      Vitest setup and the MSW server (handlers generated from OpenAPI on day 6)
  e2e/                         Playwright: the gate today, the four scripted demo steps as they arrive
  vitest.config.ts             coverage thresholds of Document 6 (80% on features, 100% on the three named files)
  playwright.config.ts         starts the dev server itself; CI stage 7 runs it on pull requests
  eslint.config.js             type-checked rules, react-hooks, react/no-danger
```

## Commands

```
npm ci                 install exactly the locked versions
npm run dev            dev server on http://localhost:5173
npm test               Vitest once; npm run test:coverage adds the thresholds
npm run lint           ESLint;  npm run format:check for Prettier
npm run typecheck      tsc -b
npm run e2e            Playwright (run npx playwright install chromium once)
npm run build          production build into dist/
```

`VITE_API_BASE_URL` selects the API (see `.env.example`); it defaults to `http://localhost:8080`.
