# PolicyPilot frontend

React 19, TypeScript (strict), Vite, TanStack Query; Vitest with Testing Library and MSW, Playwright; ESLint
and Prettier. Deployed to Vercel from this folder (`vercel.json` carries the SPA rewrite). The design is
Document 2, "Frontend Architecture"; this file only explains the layout and the commands.

## Layout

```
frontend/
  src/
    main.tsx                   React root with the TanStack Query provider and the fonts (Inter, Heebo)
    App.tsx                    the access gate, then the workspace: six screens and the guided demo panel
    index.css                  global styles and the RTL rule for Hebrew content blocks
    styles/                    the design tokens of the brand kit
    api/                       the client generated from the OpenAPI document, sse.ts, the code exchange, the queries
    features/<name>/           policy, rules, cases, chat, change, audit, demo (the guided panel)
    shared/                    layout (with the provider badge), access gate, i18n and RTL utilities, UI primitives
    test/                      Vitest setup, the MSW handlers, the payloads built from the committed fixtures
  e2e/                         Playwright: the gate and the four demo steps, alone and in one run, on the fixtures
  e2e/stack/                   Playwright against the real stack: the gate and sandbox isolation, no model call
  vitest.config.ts             coverage thresholds of Document 6 (80% on features, 100% on the three named files)
  playwright.config.ts         starts the dev server itself; CI stage 7 runs it on pull requests
  playwright.stack.config.ts   runs against a stack already up (make up); CI stage 7 runs it after the demo flows
  eslint.config.js             type-checked rules, react-hooks, react/no-danger
```

## Commands

```
npm ci                 install exactly the locked versions
npm run dev            dev server on http://localhost:5173
npm test               Vitest once; npm run test:coverage adds the thresholds
npm run lint           ESLint;  npm run format:check for Prettier
npm run typecheck      tsc -b
npm run e2e            Playwright on the fixtures (run npx playwright install chromium once)
npm run e2e:stack      Playwright against the stack of make up, its code in E2E_ACCESS_CODE
npm run build          production build into dist/
```

`VITE_API_BASE_URL` selects the API (see `.env.example`); it defaults to `http://localhost:8080`.
