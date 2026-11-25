# AGENTS.md

Instructions for AI agents and human contributors working in this repository.

**Rule of precedence:** if this file and the code disagree, the code wins. Fix this
file in the same change that makes it stale. When a task requires something listed
under [AI boundaries](#ai-boundaries) as forbidden, stop and ask a human.

---

## Project mission

FarmOrchestra is an open-source hydroponics monitoring system, but its main
purpose is a **research test bed**. It monitors a resource-constrained growing
environment and measures the **energy and communication efficiency of the whole
system** end to end: sensor, collector, network, API, database and dashboard. It
is the instrument for a thesis research project, not a product.

Typical hydroponics monitoring focuses on the growing variables themselves:
water, humidity, CO2 and nutrients. FarmOrchestra still collects the variables an
experiment needs, but those are inputs, not the goal. The goal is to understand
and reduce the energy and bandwidth the monitoring system spends to produce and
deliver a reading, and to use that knowledge to optimise this class of
agriculture system. The first system under study is the simplest possible DWC
setup; the design must then scale to other hydroponic systems without turning
into a platform.

Simplicity is a hard requirement: one dashboard page, at most two. No SaaS, no
accounts, no multi-tenant features. A small surface is easier to measure, reason
about and keep honest.

**What it is**

- A research instrument: sense, store, display, measure.
- A whole-system efficiency test bed: energy per reading and per day, bytes and
  messages on the wire, retries and idle cost, from the Pi to the dashboard.
- A deliberately small, self-hostable system on a Raspberry Pi and a modest
  server.
- A monorepo of three apps with one clear job each: collector, API, dashboard.
- Open source (GNU AGPL v3) and contributor-friendly.
- A starting point on the simplest DWC setup, built to generalise later.

**What it is not (yet, and maybe never)**

- Not a grower-facing product, SaaS, or commercial farm-management/ERP system.
  Home growers may benefit, but they are not the design driver. There are no
  accounts, organisations or billing.
- Not a system whose success is measured by water, humidity, CO2 or nutrient
  optimisation. Those are monitored; energy and communication efficiency are what
  we measure and improve.
- Not a multi-page application. One dashboard page, at most two.
- Not an actuator or control system. It does not switch pumps, lights, valves or
  heaters. The WebSocket is read-only and rejects client commands.
- Not a plant-diagnosis chatbot or an autonomous "AI farmer". Explanations must be
  grounded in the readings and the documented rules.
- Not a general-purpose IoT hub or a cloud data platform. Farm data stays on the
  owner's hardware by default.
- Not a replacement for the operator's judgement or for testing water and
  nutrients.
- Not a full history/export service. History is capped on purpose (see below).

Scope decisions (authentication, alerts, new metrics, new hardware) belong to the
maintainer. Propose them; do not ship them unasked.

---

## Architecture

### Repository layout

```
apps/
  api/         Spring Boot REST + WebSocket backend (Java)
  dashboard/   React + TypeScript + Vite frontend
  collector/   Python sensor collector for the Raspberry Pi (early stub)
docs/
  architecture/  proposed MVP diagram (PNG/PDF)
  design/        static HTML design reference (sample data, no backend)
Makefile       dev shortcuts
```

### Data flow

```
sensor -> collector (Pi) -> POST /api/events/telemetry -> TelemetryService
       -> TelemetryRepository -> database
       -> TelemetrySocket broadcast -> dashboard WebSocket -> useTelemetry
```

1. The collector reads a physical sensor and POSTs one reading with the
   `DEVICE-API-KEY` header.
2. The API checks shape (`@Valid`) and meaning (known metric, value in range,
   timestamp not far in the future), then stores it.
3. If the reading is fresh (measured within the last 30 seconds), the API pushes
   it as one JSON object to every connected dashboard.
4. The dashboard parses, validates, merges by sensor and marks a card inactive
   after 30 seconds without an update.

### Backend (`apps/api`)

- Spring Boot 4.1.1, Java 17 toolchain, Gradle Kotlin DSL.
- Two packages, one direction of dependency: `web` -> `telemetry`. The domain
  never imports the web layer.
- `telemetry/`: `MetricType` (closed set, unit, sane range), `TelemetryRequest`
  (input), `TelemetryReading` (stored output), `TelemetryService` (rules,
  transactions), `TelemetryRepository` (plain SQL via `JdbcTemplate`).
- `web/`: `TelemetryController` (REST), `TelemetrySocket` (read-only broadcast),
  `DeviceKeyFilter` (device auth), `ApiConfig` (CORS + WS origins), `ApiErrors`
  (domain errors to HTTP 400 `ProblemDetail`).
- Persistence: Flyway migrations in `src/main/resources/migration/`. Default
  runtime is a file-based H2 database in PostgreSQL mode; the `postgres` profile
  switches to PostgreSQL. Tests use in-memory H2.
- Default bind address is `127.0.0.1:8181`. It is loopback on purpose.
- JSON is Jackson 3 (`tools.jackson.*`). Do not mix in `com.fasterxml.jackson.*`.

**Endpoints**

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/events/telemetry` | `DEVICE-API-KEY` | Store one reading, 201 |
| GET | `/api/events/telemetry?sensorId=&limit=` | none | Recent history, max 100 |
| GET | `/api/events/telemetry/recent` | none | Legacy alias |
| GET | `/api/events/telemetry/{sensorId}` | none | Legacy per-sensor history |
| WS | `/ws/telemetry/stream` | origin allowlist | Push fresh readings |

The WebSocket message is a bare `TelemetryReading` object, with no envelope.
Field names (`id`, `sensorId`, `metricType`, `value`, `unit`, `timestamp`,
`receivedAt`) are a contract with the dashboard. Do not rename them casually.

### Frontend (`apps/dashboard`)

- React 19 + TypeScript + Vite 8, Sass with a small token layer.
- Hash routing (`#/`, `#/dashboard`) in `App.tsx`; no router dependency.
- `src/telemetry.ts` holds pure logic: known sensors, `parseReading`,
  `mergeReading`, `isActive`. `src/hooks/useTelemetry.ts` owns the WebSocket,
  demo mode, reconnects and the clock. Components stay presentational.
- Demo mode is the default (`VITE_TELEMETRY_MODE=demo`). Live mode uses
  `VITE_TELEMETRY_WS_URL`.
- The dashboard's `sensors` table includes a soil-moisture sensor (`ADC`) that
  the API does not accept yet. Add the metric to `MetricType` first; do not
  assume the backend already supports it.

### Sensor layer (`apps/collector`)

- Python 3.12 managed with `uv`; `httpx` for HTTP, `pytest` and `ruff` for dev.
- Runs on the Raspberry Pi. Today `main.py` is a stub: no sensor driver, no
  upload loop, no tests. Build it in small steps, testable without hardware.
- The collector owns the stable `sensorId`. Readings buffered offline **must**
  send their original `timestamp`, or old data will look new.
- Resource budget is a Pi: avoid busy loops, batch or back off, and never hold
  unbounded readings in memory.

---

## Engineering principles

- **Modular.** One app, one job. Inside the API, HTTP concerns stay in `web` and
  rules stay in `telemetry`. Inside the dashboard, pure logic stays in
  `telemetry.ts`, effects in hooks, markup in components. No layer reaches
  around another.
- **Shape before logic.** Decide the data shape first (request -> validated
  reading -> stored row -> JSON -> dashboard `Reading`), then write the smallest
  logic that produces it. Keep the Java records, SQL columns and TypeScript
  types in sync in the same change.
- **Testable by construction.** Constructor injection, `final` fields, pure
  functions, and no hidden globals. If a piece is hard to test, that is a design
  smell, not a reason to skip the test.
- **Resource-conscious.** The target is a Raspberry Pi and a small server.
  Keep queries bounded (history is capped at 100), avoid polling, serialize a
  broadcast payload once, cap WebSocket buffers, and do not add a heavyweight
  dependency for a small problem.
- **Fail fast, degrade gracefully.** A missing device key must stop startup. A
  broken dashboard connection must never fail an ingest that is already saved.
- **Smallest diff.** Subtract before you add. No speculative abstraction, no new
  dependency, option or file without a stated reason. Follow the existing names,
  style and patterns.
- **Explain the why.** Comments and Javadoc explain decisions and trade-offs,
  not syntax. Keep the docs and README true in the same change.

---

## Java conventions

- **Spring Boot, constructor injection.** Inject through the constructor into
  `private final` fields. No field injection, no `@Autowired` on fields, no
  service locator. Tests should be able to call `new TelemetryService(fake)`.
- **Java 17 language level.** The Gradle toolchain is authoritative (the README
  still says 21). Do not use newer syntax or preview features.
- **Records for immutable data.** DTOs and outputs are `record`s. Use an enum
  for a closed set like `MetricType`, never free-form strings in the domain.
- **Two levels of validation.**
  1. *Shape*: Jakarta Bean Validation annotations on the request record, enabled
     with `@Valid` in the controller (`@NotBlank`, `@Size`, `@NotNull`).
  2. *Meaning*: business rules in the service (known metric, range, timestamp).
  Both must end as HTTP 400. Throw `IllegalArgumentException` with a message
  that is safe to show a client; `ApiErrors` maps it to `ProblemDetail`. Never
  leak stack traces, SQL or secrets.
- **One responsibility per class.** Controller = HTTP only. Service = rules and
  `@Transactional`. Repository = SQL only and trusts its caller. Do not validate
  in the repository, and do not write SQL in the controller.
- **Plain SQL with `JdbcTemplate`.** Use `?` placeholders (never string
  concatenation), `GeneratedKeyHolder` for IDs, and a `RowMapper`. No ORM, no
  lazy loading, no hidden queries.
- **Transactions.** `@Transactional` on writes; `@Transactional(readOnly = true)`
  on reads. Keep transactions short and free of network calls.
- **Time.** Use `OffsetDateTime` in UTC for instants. Never use `LocalDateTime`
  for a moment in time.
- **Migrations.** Add a new Flyway file `V<n>__<snake_case>.sql` under
  `src/main/resources/migration/`. Never edit an applied migration.
- **Docs in code.** Javadoc on public types and methods, especially the *why*.
  Keep the package-level `package-info.java` current. No Lombok.
- **Style.** 4-space indent in Java, existing brace style, `var` for obvious
  locals. Format only the lines you touch; do not reformat whole files.

---

## TypeScript conventions

- **Strict typing.** `strict` is on. No `any`, no `@ts-ignore`, and no non-null
  assertions to silence the compiler. The one accepted exception is the bootstrap
  `document.getElementById("root")!` in `main.tsx`. Treat external input as
  `unknown` and narrow it explicitly, exactly like `parseReading` does.
- **Small components.** One component, one job. Pages compose; components render
  props; hooks own effects. Prefer named exports, matching the existing files.
- **Pure logic separate from effects.** Parsing, merging and freshness rules go
  in `telemetry.ts` with tests. Sockets, timers and browser APIs stay in hooks,
  and every effect cleans up (clear timers, close sockets, guard with a
  `disposed` flag).
- **No new framework by default.** Hash routing, `useState` and a plain fetch/WS
  are deliberate. Do not add a router, state library, query library or UI kit
  without a stated reason and approval.
- **Contracts are explicit.** The dashboard's `Reading` mirrors the API JSON.
  When the API shape changes, update `parseReading`, its tests and the types in
  the same change.
- **Environment.** Only `import.meta.env.VITE_*` values reach the browser.
  Never put a secret there; Vite env values are public.
- **Styles.** Colors and fonts come from `_tokens.scss`. Use the existing
  BEM-like class names and put component styles in `styles/components/`, page
  styles in `styles/pages/`. Do not hard-code new colors.

---

## Testing

Tests are required for behavior changes, not optional polish.

- **API unit tests.** Plain JUnit 5 + AssertJ, Mockito only for collaborators
  that are hard to fake. Fast, no Spring context when the class can be built with
  `new`.
- **API integration tests.** `@SpringBootTest(webEnvironment = RANDOM_PORT)` with
  in-memory H2 in PostgreSQL mode and a test-only device key. Exercise the real
  HTTP and WebSocket surface, as `TelemetryApiTest` does.
- **Dashboard tests.** `node --test` with `tsx`, testing the pure functions in
  `telemetry.ts`. There is no component-test setup yet; propose tooling before
  adding one.
- **Collector tests.** `pytest` is configured but unused. Add tests for payload
  building, retries/backoff and offline buffering before wiring real hardware.
  Hardware and network must be faked.
- **Failure tests are mandatory**, not just happy paths. Cover at least:
  missing and wrong device key, unknown metric, out-of-range and non-finite
  values, unparseable and future timestamps, history `limit` out of bounds,
  a disallowed WebSocket origin, a client command on the read-only socket, a
  broken client not stopping delivery to healthy ones, an old buffered reading
  not being broadcast, and database errors surfacing as 400/500 without leaks.
- **Test behavior, not implementation.** Name tests as sentences about behavior
  (`rejectsReadingWithoutDeviceKey`). Assert the observable result, not private
  call order.
- **Every bug gets a regression test** that fails before the fix.
- **Do not hit real hardware, real farms or the public internet in tests.**
- Coverage percentage is not a target; meaningful behavior is.

**Definition of done:** the relevant build/lint/test commands pass, the diff is
reviewed, and you can say how you know it works.

---

## Security

- **Secrets never enter the repo.** Keys and passwords come from environment
  variables. `.env.example` documents names with fake values only; Spring does
  not auto-load `.env`. Never read, print, log or commit a real `.env`, key or
  database password. Do not put secrets in `VITE_*` variables.
- **Device authentication.** Writes require the `DEVICE-API-KEY` header. The
  filter fails startup when the key is blank, compares in constant time, and
  never logs the key. Keep it that way; do not add a default key.
- **Read access is currently open.** GET endpoints and the WebSocket have no user
  authentication. Treat sensor data as readable by anyone who can reach the
  server. Do not add new write or admin endpoints without authentication.
- **Origins are allowlisted.** CORS for `/api/**` and the WebSocket handshake use
  `DASHBOARD_ORIGINS`. Browsers do not apply CORS to WebSockets, so the origin
  check is the protection. Never use `*` outside a throwaway local experiment.
- **Network exposure.** Bind to `127.0.0.1` by default. Exposing the API beyond
  the LAN requires TLS (reverse proxy), authentication for reads, and an explicit
  decision. Never change the bind address, open ports or touch router/firewall
  settings as a side effect of another task.
- **Input at the edge.** Validate shape and meaning, use parameterized SQL, bound
  every query, and return generic problem details. Do not add an endpoint that
  accepts arbitrary SQL, paths or URLs.
- **Dependencies.** A new dependency is attack surface on a Pi. Justify it, keep
  it maintained, and prefer the standard library.
- **Vulnerabilities.** Report privately to the maintainer. Do not open a public
  issue with exploit details.

---

## Git

- **Branches.** `master` is the main line. Work on a short-lived feature branch
  named `feature/<short-name>` (existing examples: `feature/migrate-front-end`,
  `feature/migrate-back-end`). One feature or fix per branch.
- **Commits.** Small, one idea each, imperative lowercase subject
  (`add telemetry tests`, `fix main class`). Do not mix refactors, formatting and
  behavior in one commit. Do not commit generated output: `.gradle/`, `build/`,
  `node_modules/`, `dist/`, `.venv/`, `data/`, `.env`.
- **Pull requests.** Every change lands through a reviewed PR: say *why*, *what
  changed*, *scope*, *risks*, and *how it was verified*. Keep it small enough for
  a ~10-minute review. Do not merge your own unreviewed change.
- **Never** commit, push, force-push, rewrite history, tag, open or merge a PR
  unless a human explicitly asks for that action in this session.
- **Never** use `git add -A` or `git commit -a`; stage only the files you
  intentionally changed.

---

## AI boundaries

These are hard limits for any agent working in this repository.

### Explicitly permitted

- Read and search any tracked source, test, doc or config file.
- Run the build, lint, type-check and test commands, and run the apps locally.
- Create or edit code, tests, migrations, styles and docs inside the requested
  scope, following the conventions above.
- Add a metric end to end when asked: `MetricType` constant with unit and range,
  tests, and the README table.
- Use in-memory H2, fakes and mocks for verification.
- Create a feature branch when explicitly asked, and stage only the files you
  changed.
- Propose a plan, ask clarifying questions, and push back when a request conflicts
  with this file.
- Update `AGENTS.md` when a convention genuinely changes, in the same PR.

### Explicitly forbidden

- Committing, pushing, force-pushing, rewriting history, tagging, or opening,
  merging or closing a PR without an explicit request.
- Reading, printing, copying or committing `.env` files, credentials, keys or
  database dumps. If a task seems to need one, ask the human to run that step.
- Weakening, skipping or deleting tests, validation, the device-key filter, the
  origin allowlist or the 100-row history cap to make something pass.
- Adding a default or fallback secret, disabling TLS/origin checks, or binding the
  server to a public interface.
- Running destructive database commands (`DROP`, `TRUNCATE`, `DELETE` without a
  `WHERE`), deleting `data/`, or resetting a real database.
- Deploying to the Raspberry Pi or any production host, installing system
  packages, or running `sudo`.
- Adding a dependency, framework, service or external API without a stated
  reason and approval. Sending farm data to a third party is a product decision,
  not an implementation detail.
- Renaming JSON, database or WebSocket contract fields without a coordinated
  change and tests on both sides.
- Introducing actuators, device commands or any write path through the WebSocket.
- Making product decisions unilaterally: accounts, authentication, alerts,
  notifications, new hardware, pricing or hosting.
- Claiming a command passed, a test passed or a feature works without having run
  it and being able to show the output.
- Creating extra files, placeholder docs or broad reformatting that the task did
  not ask for.

If a request requires a forbidden action, stop, explain the conflict and ask.

---

## Quick reference

```bash
# API (from apps/api)
./gradlew test          # unit + integration tests
./gradlew bootRun       # run the API on 127.0.0.1:8181
./gradlew build         # compile + test

# Dashboard (from apps/dashboard)
npm test                # node:test suite for telemetry.ts
npm run lint            # ESLint
npm run build           # tsc --noEmit + vite build
npm run dev             # Vite dev server on :5173

# Collector (from apps/collector)
uv sync
uv run pytest
uv run ruff check .
uv run main.py
```

`make api` currently calls `./gradlew run`, which does not exist; use
`./gradlew bootRun`. The root README also still says port 8080 and Java 21,
while the code uses 8181 and Java 17. Treat the code as the source of truth and
fix the docs when you touch them.
