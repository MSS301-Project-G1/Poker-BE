# Ranking service — Hoài Anh

Elo, rank tiers, leaderboard, seasons and tutorial reward (SPEC §9.4).
Implementation batches and review dependencies: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

## Package structure

The backend follows the `game-service` layers under `com.msspoker.rankingservice`:

```text
rankingservice/
├── controller/         # HTTP and RabbitMQ entry points
├── dto/
│   ├── request/        # Request records, including the wallet credit body
│   ├── response/       # JSON responses and pagination
│   └── event/          # Consumed event payloads (envelope comes from common)
├── service/            # Application service interfaces
│   └── impl/           # Spring-managed implementations
├── entity/             # JPA entities only
├── repository/         # Spring Data repositories only
├── validator/          # Business validation
├── mapper/             # MapStruct transformations
├── enums/              # Tier, season status, game mode and error enums
├── constant/           # Shared constants and header names
├── exception/          # Exception factory and REST exception handler
├── config/             # Spring configuration and SPEC §8 parameters
├── security/           # Dev-only X-Dev-User identity filter
└── util/               # UUID v7 generation
```

## Run with PostgreSQL and RabbitMQ in Docker

The service owns PostgreSQL `ranking-db`, declared in `ranking-service/compose.yml`
on the private `ranking-net` network. RabbitMQ is the shared broker in
`docker/compose.infra.yml`. The root `docker-compose.yml` includes both, so from
the BE repository root:

```powershell
docker compose up -d ranking-db rabbitmq
.\mvnw.cmd -pl ranking-service -am -DskipTests package
java -jar ranking-service/target/ranking-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Dev uses PostgreSQL `127.0.0.1:54331/ranking_db` (user `poker`, local password
`poker_dev`) and RabbitMQ `127.0.0.1:56729` (`guest`/`guest`, management UI
`http://localhost:15679`). When changing the local password, set
`RANKING_DB_PASSWORD` for Compose and `RANKING_DEV_DB_PASSWORD` to the same value
for the Java process. Flyway creates the schema and seeds one ACTIVE season;
Hibernate validates it. H2 is a test-scope dependency only. In `dev`, rank rewards
go to a logging wallet stub until wallet-service is available.

To run the application container with the `dev` profile:

```powershell
docker compose -f docker-compose.yml -f ranking-service/compose.dev.yml up -d --build ranking-service
```

## Integration contract

See [SPEC §6](../docs/SPEC.md). Tiers use the SPEC enum names; `CARD_GOD` is
returned for `GRAND_CHAMPION` players within the top 50 of the leaderboard.
Leaderboard order is Elo descending, then account id ascending.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/internal/ranking/{accountId}` | `{ accountId, elo, tier, winStreak, lossStreak }`; unknown accounts get the starting Elo |
| GET | `/internal/ranking/top?limit=32` | Top N of the active season with `position`; limit 1–100 |
| GET | `/api/ranking/leaderboard?page=0&size=20` | `PageResponse` of ranked players; size 1–100 |
| GET | `/api/ranking/me` | Own standing from `X-User-Id`; `position` is `null` before the first record |
| GET | `/api/seasons/current` | Active season |

`/api/**` trusts `X-User-Id` set by the gateway (SPEC §5.1); a missing header
returns `401 RANK_UNAUTHORIZED`. `/internal/**` must not be exposed by the gateway.
In the `dev` profile only, `X-Dev-User` (sent by the FE shared http client, like
for game-service) stands in for `X-User-Id`, and such requests act as `ADMIN`
unless `X-User-Role` is sent.

Consumed events on exchange `poker.events`, each with its own durable queue:

| Queue | Routing key | Effect |
| --- | --- | --- |
| `ranking-service.account.registered` | `account.registered` | Creates the season Elo record (NEWBIE, 0) |
| `ranking-service.match.finished` | `match.finished` | `RANK` only: Elo by place (+20/+10/+5/−5/−10/−15, floor 0), peak, tier, streaks, history, top 3 rewards |

Players that arrive in `match.finished` before `account.registered` get a
record on the fly. `elo_history` is unique per `(accountId, matchId)`, so a
redelivered match changes nothing. Malformed or invalid payloads are logged and
dropped; database failures are retried (5 attempts).

Rank rewards (3×/2×/1× entry fee) are stored in `rank_reward_credits` in the
same transaction as the Elo update, then sent to wallet-service
`POST /internal/wallet/credit` with reason `RANK_REWARD`, `refId` = matchId and
key `rank-reward:{matchId}:{accountId}`. A wallet outage leaves the reward
pending; the dispatcher retries every 5 seconds with the same key.

### Seasons and season rewards

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/seasons/{seasonId}/rewards` | Tier rewards `[{ tier, rewardType (COIN/PRODUCT), amount, productId }]` |
| GET | `/api/seasons/rewards/me` | Ended seasons where the player reached a rewarded tier: `finalTier`, earned rewards, `claimed` |
| POST | `/api/seasons/{seasonId}/rewards/claim` | Claims every reward up to the final tier; repeating returns the same claim |
| GET | `/api/admin/seasons` | All seasons with rewards; `X-User-Role: ADMIN` |
| POST | `/api/admin/seasons` | `{ name, startAt, endAt, resetPercent, rewards[] }` → UPCOMING season |
| PUT | `/api/admin/seasons/{seasonId}/rewards` | Replaces rewards; refused once the season ended |
| POST | `/api/admin/seasons/close` | Closes the active season now; needs an UPCOMING season |

Closing a season (by the admin, or every minute once `endAt` passed and a next
season exists) records each player's final tier in leaderboard order, so
CARD_GOD is the top 50 Grand Champions at that moment. Each player starts the
next season with `resetPercent` of the final Elo (default 50%, floor 0). Players
whose final tier earns a reward receive `SEASON_REWARD_AVAILABLE` through
`notification.requested` (outbox `ranking_outbox`, batches of 500 recipients).
Without a next season the current one keeps running. Elo updates and closing
lock the active season row, so no match result lands in a closed season.

Rewards are cumulative (SPEC §2.3): reaching DIAMOND earns every reward from
IRON to DIAMOND. A claim stores `season_reward_claims` and one `reward_grants`
row per reward in one transaction; coins go to wallet-service with reason
`SEASON_REWARD` and key `season-reward:{seasonId}-{TIER}:{accountId}`, skins go to
shop-service `POST /internal/inventory/grant` with key
`season-item:{seasonId}-{productId}:{accountId}`, retried until accepted. The seed
season has proposed coin rewards from 50 (IRON) to 1000 (CARD_GOD).

## Production configuration

- PostgreSQL: `RANKING_DB_URL`, `RANKING_DB_USER`, `RANKING_DB_PASSWORD`.
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`.
- Wallet: `WALLET_SERVICE_URL` (default `http://localhost:8089`).
- Shop: `SHOP_SERVICE_URL` (default `http://localhost:8088`). In `dev`/`test` the
  wallet and shop are logging stubs.
- SPEC §8 parameters: `ranking.elo-by-place`, `ranking.tier-thresholds`,
  `ranking.reward-multipliers`, `ranking.win-streak-places`, `ranking.card-god-limit`,
  `ranking.min-elo`, `ranking.starting-elo` in `application.properties`.

## Verification

```powershell
.\mvnw.cmd -pl ranking-service -am clean verify
```

31 tests use test-only H2 and do not require Docker: tier boundaries, Elo
floor, places beyond six, Card God cut-off, duplicate events, non-RANK modes,
invalid results, reward queueing and wallet outage retry, listener parsing with
unknown fields, API contracts, leaderboard ordering, season closing and carry
over, scheduled closing and activation, cumulative claims, grant retries and
the dev identity filter.
Season tests use their own H2 database because they close the seed season.
