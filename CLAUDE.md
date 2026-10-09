# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## How we work
- I am learning while building. Rule zero: nothing is merged that I can't explain.
- Keep changes small (reviewable in ~10 min). One feature per branch.
- Never commit, push, or create extra files/tests unless I ask.
- Ask which mode we use for each task: "I write, you review" / "you propose small diffs" / "design first".
- Explain with Java analogies and design patterns when useful.

## Project

FarmOrchestra is an open source hydroponics monitoring system (first target: a deep-water culture lettuce setup). It is an early-stage monorepo with three apps under `apps/`:

| App | Stack | State |
|---|---|---|
| `apps/dashboard` | React 19 + TypeScript + Vite, SCSS | Active development — the only app with real code and tests |
| `apps/api` | Java 21, Spring Boot 4 (Gradle Kotlin DSL), Lombok | Skeleton (`ApiApplication` only, no endpoints yet) |
| `apps/collector` | Python 3.12, managed with `uv`, `httpx` | Stub (`main.py` prints hello) |

Intended data flow (see `README.md` and `docs/architecture/architecture.png`; much is still planned): the collector runs on a Raspberry Pi, reads sensors, and persists readings to PostgreSQL; the API serves them; the dashboard displays them, receiving live readings over a WebSocket.

## Commands

From the repo root (each in its own terminal):

```bash
make dashboard   # Vite dev server on http://localhost:5173
make api         # ./gradlew run → http://localhost:8080
make collector   # uv run main.py
```

Dashboard (`cd apps/dashboard`, after `npm install`):

```bash
npm run dev
npm run build    # tsc --noEmit && vite build
npm run lint     # eslint .
npm test         # node's built-in test runner via tsx: src/telemetry.test.ts
node --import tsx --test --test-name-pattern="mergeReading" src/telemetry.test.ts   # single test / group
```

Note: `npm test` names `src/telemetry.test.ts` explicitly — new test files must be added to the `test` script in `package.json` to run.

API (`cd apps/api`):

```bash
./gradlew run           # Makefile uses the `application` plugin's run task
./gradlew test
./gradlew test --tests "com.farmorchestra.api.ApiApplicationTests"
```

Collector (`cd apps/collector`, after `uv sync`): dev deps are `pytest` and `ruff` → `uv run pytest`, `uv run ruff check .` (no tests exist yet).

## Dashboard architecture

- **Routing** is a hand-rolled hash router in `src/App.tsx`: `#/dashboard` → `DashboardPage`, anything else → `HomePage`. No router library.
- **`src/telemetry.ts`** holds the pure, framework-free domain logic and is what the tests cover:
  - `sensors` — the registry of known sensors (id, title, unit). Sensor IDs are strings internally; incoming messages may send numeric IDs, which `parseReading` normalizes.
  - `parseReading` — validates a raw WebSocket JSON message (`{sensorId, value, timestamp}`), rejects unknown sensor IDs / non-finite values / bad timestamps, and stamps `receivedAt`.
  - `mergeReading` — keeps the latest reading per sensor, ignoring out-of-order (older-timestamp) messages; returns the same object when unchanged.
  - `isActive` — a reading is active only while connected and received less than `STALE_MS` (30s) ago.
- **`src/hooks/useTelemetry.ts`** is the only stateful data source. It runs in one of two modes chosen by `VITE_TELEMETRY_MODE` (defaults to `demo`):
  - *demo*: emits sine-wave readings for sensors 3 and 4 every second, pausable; "connected" means "not paused".
  - *live*: connects to `VITE_TELEMETRY_WS_URL` (default `ws://localhost:8181/ws/telemetry/stream` — a separate telemetry server, not the API on 8080), auto-reconnects every 2s, and feeds messages through `parseReading` → `mergeReading`.
  - A 1s clock tick (`now`) drives staleness re-renders.
  Keep validation/merge logic in `telemetry.ts` (testable) rather than in the hook.
- Env config: copy `.env.example` to `.env`.

### Styles

All styling is SCSS under `src/styles/`, entered via `main.scss` (imported in `main.tsx`). Design tokens live in `_tokens.scss` (`@use "tokens" as t;`), with partials per component (`components/_<Component>.scss`) and per page (`pages/_<Page>.scss`) registered in `main.scss`. Class names follow BEM (`sensor-card__value`, `sensor-status--active`). `src/index.css` and `src/App.css` are leftovers from the Vite template and are not imported.

`sass` is declared in the **root** `package.json`, not in `apps/dashboard/package.json`; Vite resolves it from the root `node_modules`, so run `npm install` at the root too.

## Conventions

- Exported functions, hooks and components carry JSDoc comments (see `telemetry.ts`, `useTelemetry.ts`).
- TypeScript is strict about unused locals/params and uses `verbatimModuleSyntax` — use `import { type X }` for type-only imports.

## Working loop (per feature)

| # | Step | Command / action | Done when |
|---|------|------------------|-----------|
| 1 | **Issue** | Write a GitHub issue: goal + acceptance criteria | Criteria are testable |
| 2 | **Branch** | `git switch -c feat/<name>` | On a clean branch |
| 3 | **Fresh context** | `/clear` | No leftovers from last task |
| 4 | **Plan** | Shift+Tab → plan mode, then `/start-feature <description>` | Plan approved, mode chosen |
| 5 | **Step loop** | Repeat 5a–5d for every step of the plan (one step = one commit) | All steps committed |
| 5a | ↳ **Build** | Claude implements one step (≤10 min of reading). Meanwhile update the github ticket with the previous commit state and explanation or even visual diagrams | Step compiles, tests pass |
| 5b | ↳ **Explain back** | I explain the step in 1–3 sentences (what it does + why). Claude grades ✅ / ⚠️ / ❌ and fills gaps with an example | ✅; a ⚠️/❌ is re-explained, not skipped |
| 5c | ↳ **Seed** | Claude suggests 1–2 seed-note possible topics from this step to the final obsidian note; I pick one possible topic | Topic chosen (written during the next 5a) |
| 5d | ↳ **Commit** | I write the commit message myself | Message explains *why* |
| 6 | **Verify** | Ask Claude to run lint/test/build; run the app myself | All green and seen working for real |
| 7 | **Review** | `/code-review` (e.g. `/code-review high`), fix findings (each fix goes through 5b–5d) | No open issues I disagree with |
| 8 | **Close the loop** | `/close-the-loop`: 2 questions on how the steps fit together | No ❌ left; every commit has a good explanation and refernce on the ticket | Write a public note on the digital garden, to reference on the pull request as more info about one of the topics. 
| 9 | **PR** | Open PR, CI must pass, self-review the diff on GitHub | Merged |

`/start-feature` and `/close-the-loop` are project skills in `.claude/skills/`; they only run when invoked by the user.

### Guardrails
- If Claude's diff is too big to read in 10 min → ask it to split or revert.
- If I can't explain a line → ask "explain line N like I'm a Java dev" before moving on.
- Hardware/IoT changes: test with the real sensor before merging (mock ≠ reality).
- Long sessions: `/compact` or `/clear` when switching topics.
- Secrets never in code: `.env` + `.env.example`.

### Learning log
- [[FarmOrchestra – Learning Notes]]: paste each `/close-the-loop` note here.
