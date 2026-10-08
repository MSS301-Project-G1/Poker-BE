# Game service — Bảo

No-limit Texas Hold’em: engine, REST/internal APIs, personal STOMP snapshots,
timeouts/AFK, history, Admin Match Settings and durable match events.
Implementation batches and review dependencies: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## Run with PostgreSQL and RabbitMQ in Docker

From the BE repository root, with Docker Desktop running:

```powershell
docker compose -f game-service/compose.dev.yml up -d postgres rabbitmq
.\mvnw.cmd -pl game-service -am -DskipTests package
java -jar game-service/target/game-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Dev uses PostgreSQL `127.0.0.1:54329/game_db` (user `poker`, local password
`poker_dev`) and RabbitMQ `127.0.0.1:56729`; management UI is at
`http://localhost:15679` (`guest`/`guest`). Override `GAME_DEV_DB_PASSWORD` for
both Compose and the Java process when changing the local password. Flyway
creates the schema; Hibernate validates it. The named `game-db` volume retains
matches and settings across container restarts. H2 is a **test-scope dependency
only** and is absent from the executable JAR.

To build and run the application itself in Docker:

```powershell
docker compose -f game-service/compose.dev.yml --profile app up -d --build
```

Use either the Java process or the application container on port 8082. Dev is
local only: HTTP identity uses `X-Dev-User`, STOMP CONNECT uses `devUser` (UUID),
and Admin role enforcement is bypassed to test the owned screen. These shortcuts
are unavailable outside the `dev` profile. The Compose app binds inside its
container and publishes only on host loopback.

FE: in `D:\STUDY\MSS\Poker-FE\Poker-FE`, run `npm ci`, then `npm run dev:game`.
Open `http://localhost:5173/table`; use its local launcher to create a table with
humans/bots. It supplies personal URLs for multiple browser tabs. The launcher
calls `POST /dev/tables?humans=1&bots=5` (humans 1–6, bots 0–5, total subject to
mode settings; normally 2–6). The dev launcher and bot creation endpoint are disabled in production.

## Integration contract

See [SPEC §6](../docs/SPEC.md). Card JSON is `{ rank, suit }` using enum names.
Chips are integer table chips and are distinct from wallet coins.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/internal/tables` | CreateTable; returns `tableId`, `matchId` |
| GET | `/internal/matches?accountId=...&page=0&size=20` | `PageResponse<MatchView>`; size 1–100 |
| GET | `/api/game/me/active-table` | Table identifiers, or JSON `null` |
| GET | `/api/game/tables/{tableId}` | Personal snapshot; membership required |
| GET | `/api/game/tables/{tableId}/result` | Durable final result; membership required |
| GET | `/api/admin/match-settings` | All mode settings; ADMIN required |
| PUT | `/api/admin/match-settings/{mode}` | `{ settings: { smallBlind, bigBlind, startingChips, turnTimeSeconds }, minPlayers, maxPlayers }` |

CreateTable is idempotent for `(mode, sourceId)` with an identical request.
A changed request for that key or a player already in another running table
returns a conflict. `playerIds` order defines seats. `NORMAL`/`CUSTOM` require
zero entry fee. Settings updates apply to newly created tables.

Connect native WebSocket `/ws/game`, then STOMP CONNECT with
`Authorization: Bearer <access-token>`. Subscribe to
`/user/queue/tables/{tableId}` and `/user/queue/game-errors`.
SEND `/app/tables/{tableId}/sync` to request a snapshot; SEND
`/app/tables/{tableId}/action` with, for example:

```json
{ "type": "RAISE", "amount": 80, "actionSequence": 12 }
```

`amount` is the total street bet, required only for RAISE. Other actions send
`type` and `actionSequence`. The optional sequence supports existing callers;
the owned FE always sends it to reject duplicate/outdated actions. Snapshots
include authoritative legal actions, raise limits, actor, deadline and server
time. Hole cards are private until a non-folded showdown. A rejected business
action reaches the private error queue; unauthorized destinations terminate
the socket with a STOMP ERROR frame. REST errors use Vietnamese messages.

## Production configuration and dependencies

- PostgreSQL: `GAME_DB_URL`, `GAME_DB_USER`, `GAME_DB_PASSWORD`.
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`.
- Trusted server ingress: configure `GAME_SERVICE_KEY`; gateway/internal callers
  send `X-Game-Service-Key`. The gateway must strip client identity headers,
  validate JWT and set `X-User-Id`/`X-User-Role`. The secret stays on servers.
  Missing/incorrect service keys fail closed outside dev. Keep internal ports
  private and prevent browser access to `/internal/**`.
- STOMP independently validates RS256 JWT: `JWT_JWK_SET_URI`, `JWT_ISSUER`,
  `JWT_AUDIENCE`, UUID `sub`, expiry and signature. Set `GAME_ALLOWED_ORIGINS`.
- `IDENTITY_SERVICE_URL` supplies account restrictions during CreateTable.
  Production fails with a dependency error when restrictions cannot be checked;
  dev/test bypass the unfinished identity integration explicitly.

`poker.events` is a durable topic exchange. Each receiving service must declare
its own durable queue and bind `match.started`/`match.finished` before consuming.
Events use a transactional outbox, persistent messages, mandatory publishing and
publisher confirms. Unroutable/nacked events remain pending for retry; delivery
is at least once, so consumers deduplicate `eventId`. No game-owned consumer
pretends to award Elo/coins or open chat rooms.

Gateway/shared auth/profile/chat/skin/reward integrations require their owners'
review. GĐ2 live `account.penalized` handling still needs Khanh's concrete penalty
type contract; current enforcement checks restrictions when creating a table.
The game runs as one instance: hands live in RAM with a lock per table.
Restart cancels interrupted RUNNING matches without restoring cards or issuing
completion rewards. Finished tables remain in RAM for 300 seconds; history and
final results remain in PostgreSQL. Multi-instance routing/recovery is outside
this implementation.

## Verification

```powershell
.\mvnw.cmd clean verify
```

36 game tests cover poker categories, kicker/wheel, side pots/refunds, heads-up,
minimum raises and short all-ins, stale actions, chip conservation over 3,000+
seeded bot hands, AFK, deterministic simultaneous elimination, 32 concurrent
tables, persistence, API access, actual STOMP sockets and signed JWT validation.
Production shuffling uses `SecureRandom`; seeded randomness is used in tests.

Browser checks (backend running in dev, PostgreSQL/RabbitMQ above):

```powershell
# In the FE repository
npx playwright install chromium
npm run lint
npm run build
npm run test:e2e
```

Five browser scenarios cover launcher, two-player actions and reload privacy,
complete match results, mobile layout, and Admin settings save/reload. An actual
Docker integration run persisted 11 completed matches and delivered all 22
start/finish events with no pending outbox rows.
