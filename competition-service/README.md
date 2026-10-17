# Competition service — Hoài Anh

Custom Room and tournaments (SPEC §7.4, §7.7, §9.4).
Implementation batches and review dependencies: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## Package structure

The backend follows the `game-service` layers under `com.msspoker.competitionservice`:

```text
competitionservice/
├── controller/         # HTTP and RabbitMQ entry points
├── dto/
│   ├── request/        # Validated requests and the CreateTable body sent to game
│   ├── response/       # JSON responses and bodies read from game/social
│   └── event/          # Consumed match.finished and published notification payloads
├── service/            # Application service interfaces
│   ├── event/          # Outbox publishing interface
│   └── impl/           # Spring-managed implementations
│       └── event/      # OutboxPublisherImpl
├── entity/             # JPA entities only
├── repository/         # Spring Data repositories only
├── validator/          # Room access and settings validation
├── mapper/             # MapStruct transformations
├── enums/              # Room, notification, game mode and error enums
├── constant/           # Shared constants and header names
├── exception/          # Exception factory and REST exception handler
├── config/             # Spring configuration and room defaults
├── security/           # Dev-only X-Dev-User identity filter
└── util/               # UUID v7 generation
```

## Run with PostgreSQL and RabbitMQ in Docker

The service owns PostgreSQL `competition-db` (`competition-service/compose.yml`,
private `competition-net`). From the BE repository root:

```powershell
docker compose up -d competition-db rabbitmq
.\mvnw.cmd -pl competition-service -am -DskipTests package
java -jar competition-service/target/competition-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Dev uses PostgreSQL `127.0.0.1:54332/competition_db` (user `poker`, password
`poker_dev`), RabbitMQ `127.0.0.1:56729` and game-service at
`http://localhost:8082` (run it with its `dev` profile to start tables).
Container with the `dev` profile:

```powershell
docker compose -f docker-compose.yml -f competition-service/compose.dev.yml up -d --build competition-service
```

## Integration contract

All `/api/rooms/**` calls trust `X-User-Id` set by the gateway; a missing header
returns `401 COMP_UNAUTHORIZED`. Room codes are 6 characters and case-insensitive.
In the `dev` profile only, `X-Dev-User` (sent by the FE shared http client, like
for game-service) stands in for `X-User-Id`, and such requests act as `ADMIN`
unless `X-User-Role` is sent.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/api/rooms` | `{ visibility, password?, maxPlayers, smallBlind, bigBlind }` → `RoomResponse` |
| GET | `/api/rooms?page=0&size=20` | Waiting PUBLIC rooms, newest first, with `playerCount` |
| GET | `/api/rooms/me` | Own open room, or empty |
| GET | `/api/rooms/{code}` | Room detail; members only |
| POST | `/api/rooms/{code}/join` | Optional `{ password }`; rejoining is a no-op |
| POST | `/api/rooms/{code}/leave` | Owner leaving hands the room to the longest-waiting member |
| PUT | `/api/rooms/{code}/ready` | `{ ready }` |
| DELETE | `/api/rooms/{code}/members/{accountId}` | Owner kicks a member |
| PUT | `/api/rooms/{code}/owner` | `{ accountId }`; owner transfers the room |
| POST | `/api/rooms/{code}/invitations` | `{ accountId }`; friends only, sends `ROOM_INVITE` |
| POST | `/api/rooms/{code}/start` | Owner; everyone else ready → `CreateTable` CUSTOM → `PLAYING` with `tableId` |

`RoomResponse`: `id, roomCode, ownerId, visibility, hasPassword, maxPlayers,
smallBlind, bigBlind, status (WAITING/PLAYING/CLOSED), tableId, members[]` where a
member is `{ accountId, ready, owner, joinedAt }` in seat order.

Errors: `COMP_NOT_FOUND`, `COMP_FORBIDDEN` (not a member/owner, not friends,
restricted player), `COMP_WRONG_PASSWORD`, `COMP_ROOM_FULL`, `COMP_ALREADY_IN_ROOM`,
`COMP_CONFLICT` (room playing, not ready, too few players), `COMP_PLAYER_BUSY`,
`COMP_DEPENDENCY_UNAVAILABLE`, `COMP_INVALID_REQUEST`, `COMP_UNAUTHORIZED`.

Start sends `{ mode: CUSTOM, sourceId, playerIds (seat order), settings:
{ smallBlind, bigBlind, startingChips: 1000, turnTimeSeconds: 20 }, entryFee: 0 }`
to game-service `POST /internal/tables` with `X-Game-Service-Key` when
`GAME_SERVICE_KEY` is set. Each start uses a new `sourceId` stored on the room
before the call, so a retry after a timeout returns the same table.

### Tournaments

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/api/admin/tournaments` | `X-User-Role: ADMIN`; `{ name, type, startAt (future), entryFee, slots?, rewardCoins?, rewardProductId? }`; ELITE is free and starts at least 30 minutes later |
| POST | `/api/admin/tournaments/{id}/start` | Starts early: seats registered/confirmed players and opens the tables |
| POST | `/api/admin/tournaments/{id}/cancel` | Before start only; refunds every entry fee |
| GET | `/api/tournaments?page=0&size=20` | Tournaments by `startAt` with `entryCount` and `prizePool` |
| GET | `/api/tournaments/{id}` | Detail with `prizePool`, `prizes[]` (places 1–3), `entries[] { accountId, status, finalPlace, registeredAt, confirmBy }` and the bracket `tables[]` |
| POST | `/api/tournaments/{id}/register` | OPEN only, until `startAt`; debits the fee once |
| POST | `/api/tournaments/{id}/confirm` | ELITE invitee accepts before `confirmBy`; repeating is a no-op |

Entry status: OPEN entries are `REGISTERED`; ELITE entries are `INVITED`, then
`CONFIRMED` or `EXPIRED`; players knocked out in a qualifier become `ELIMINATED`.
`entryCount` leaves out expired invitations.

Schedule (SPEC §7.7), checked every 30 seconds:

1. ELITE, 30 minutes before `startAt`: the top `slots` players of
   ranking-service `GET /internal/ranking/top` are invited (`TOURNAMENT_INVITE`
   with `data.tournamentId`, `startAt`, `confirmBy`).
2. ELITE, 10 minutes before `startAt`: invitations not confirmed expire and the
   next ranks (33, 34, …) are invited into the open places; they may confirm until
   `startAt`. Ranking outages are retried on the next run; expiry does not wait.
3. Any type at `startAt`: remaining invitations expire; with at least 4 players
   the tournament starts, otherwise it is cancelled and entry fees are refunded.

Bracket: shuffled players fill ⌈n/8⌉ qualifier tables evenly; the top 2 of each
table reach one final table (32 players → 4 × 8 → 8, SPEC §2.4). Eight or fewer
players play the final directly; fewer than 4 cannot start. Tables are
`{ id, round (FINAL/QUALIFIER), tableIndex, status (PENDING/PLAYING/FINISHED),
tableId, seats[] { accountId, seatIndex, place } }`, final first.

Registration debits wallet-service `POST /internal/wallet/debit` with reason
`TOURNAMENT_ENTRY_FEE` and key `tournament-fee:{tournamentId}:{accountId}`; a
refusal returns `409 COMP_INSUFFICIENT_BALANCE`. Cancellation stores
`TOURNAMENT_REFUND` credits (key `tournament-refund:{tournamentId}:{accountId}`)
that are retried until the wallet accepts them.

Each tournament table calls CreateTable with `mode: TOURNAMENT`, `sourceId` = the
tournament table id, `settings: null` (game's TOURNAMENT Match Settings) and the
tournament fee as `entryFee`. Tables that game-service cannot open stay `PENDING`
and are retried every 2 seconds with the same source, then `TOURNAMENT_TABLE_READY`
is sent to their players with `data.tableId`.

Prizes, stored when the final finishes and retried until accepted:

- Pool = entry fee × entries holding a place + `rewardCoins` (an ELITE pool is
  `rewardCoins`). Places 1, 2, 3 receive 50% / 30% / 20%; smaller finals share
  the pool between the places that exist, and the rounding remainder goes to the
  champion. Coins go to wallet-service with reason `TOURNAMENT_PRIZE` and key
  `tournament-prize:{tournamentId}:{accountId}`.
- `rewardProductId` (exclusive skin) goes to the champion through shop-service
  `POST /internal/inventory/grant` with key `tournament-item:{tournamentId}:{accountId}`
  (table `item_grants`).

Errors add `COMP_TOURNAMENT_FULL`, `COMP_INSUFFICIENT_BALANCE`, `COMP_FORBIDDEN`
for non-admins, ELITE registration and confirming without an invitation, and
`COMP_CONFLICT` for expired invitations.

### Events on `poker.events`

- Consumes `match.finished` on durable queue `competition-service.match.finished`:
  CUSTOM results for the room's current `sourceId` return it to `WAITING` and clear
  ready flags; TOURNAMENT results record places, eliminate players outside the top
  2, open the final after the last qualifier and finish the tournament after the
  final. Redelivery and unknown sources change nothing.
- Publishes `notification.requested` (`ROOM_INVITE`, `ROOM_UPDATED` with
  `data.change`, `ROOM_STARTED`, `TOURNAMENT_INVITE` and `TOURNAMENT_TABLE_READY`
  with `data.tableId`)
  through the `competition_outbox` table, mandatory publishing and publisher
  confirms like game-service. Without a bound consumer queue they stay pending.

## Production configuration

- PostgreSQL: `COMPETITION_DB_URL`, `COMPETITION_DB_USER`, `COMPETITION_DB_PASSWORD`.
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`.
- `GAME_SERVICE_URL`, `GAME_SERVICE_KEY`, `SOCIAL_SERVICE_URL`, `WALLET_SERVICE_URL`,
  `RANKING_SERVICE_URL`, `SHOP_SERVICE_URL`.
- Room defaults: `competition.room.min-players`, `max-players`, `starting-chips`,
  `turn-time-seconds`, `code-length`.
- Tournament format: `competition.tournament.slots`, `table-size`,
  `advance-per-table`, `min-players`.
- Tournament schedule and prizes: `competition.tournament.invite-lead-minutes` (30),
  `confirm-deadline-minutes` (10), `schedule-millis` (30000), `prize-shares`
  (50,30,20). For a quick demo, run with smaller values such as
  `--competition.tournament.invite-lead-minutes=2 --competition.tournament.confirm-deadline-minutes=1 --competition.tournament.schedule-millis=5000`.

In `dev` and `test`, friends, the wallet and the shop are mocked (everyone is a
friend, debits, credits and item grants succeed and are logged). ranking-service
and game-service are called for real.

## Verification

```powershell
.\mvnw.cmd -pl competition-service -am clean verify
```

36 tests use test-only H2 and stub HTTP game/wallet/ranking servers; Docker is not
required. Tournament schedule tests move a tournament's timeline back instead of
waiting.
