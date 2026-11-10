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
├── service/            # Business service interfaces (Elo, ranking, seasons, tutorial)
│   ├── client/         # Calls to wallet, shop and social services
│   ├── event/          # Outbox publishing
│   ├── job/            # Scheduled reward and grant dispatchers
│   └── impl/           # Exactly one implementation per interface, like game-service
│       ├── client/
│       ├── event/
│       └── job/
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

Dev uses PostgreSQL `127.0.0.1:15431/ranking_db` (user `poker`, local password
`poker_dev`) and RabbitMQ `127.0.0.1:5679` (`guest`/`guest`, management UI
`http://localhost:15679`). When changing the local password, set
`RANKING_DB_PASSWORD` for Compose and `RANKING_DEV_DB_PASSWORD` to the same value
for the Java process. Flyway creates the schema and seeds one ACTIVE season;
Hibernate validates it. H2 is a test-scope dependency only. The `dev` profile and
the tests turn on `ranking.wallet.mock`, `ranking.shop.mock` and
`ranking.social.mock` (SPEC §9.4): wallet and shop calls are logged instead of
sent, and the season's top 200 players are everyone's friends. Set a flag to
`false` to call the real service once it runs locally.

To run the application container with the `dev` profile:

```powershell
docker compose -f docker-compose.yml -f ranking-service/compose.dev.yml up -d --build ranking-service
```

## Service discovery and gateway

The service registers in Eureka (`discovery-service`, 8761) as `ranking-service` and the
API gateway routes `/api/ranking`, `/api/seasons`, `/api/tutorial` and `/api/admin/seasons` to it (`lb://ranking-service`). Calls to other services use the
`@LoadBalanced RestClient.Builder` from `config/RestClientConfig`: base URLs are
service names such as `http://wallet-service`, resolved through Eureka. The `dev`
profile runs the jar on the host without Eureka (like game-service) and resolves
the names from `spring.cloud.discovery.client.simple.instances` to the local
ports (wallet 8089, shop 8088, social 8084); with `EUREKA_CLIENT_ENABLED=true` Eureka is asked first.
`compose.dev.yml` turns Eureka on because the container shares the stack with
discovery-service. To check the route, follow `api-gateway/ONBOARDING.md` with
`-f ranking-service/compose.yml`.

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
| GET | `/api/ranking/friends` | The player and up to 199 friends: `[{ rank, position, accountId, elo, tier, winStreak, matchesPlayed, you }]` |
| GET | `/api/ranking/me/matches/{matchId}` | Own result of one RANK match: `{ matchId, place, eloDelta, eloAfter, rewardCoins }`; `eloDelta` is the applied change, `rewardCoins` is 0 for unpaid places; `404` until `match.finished` is processed |
| GET | `/api/seasons/current` | Active season |
| GET | `/api/tutorial/me` | `{ completed, completedAt, rewardCoins }` |
| POST | `/api/tutorial/complete` | Records the completion and grants `TUTORIAL_REWARD` once; repeating returns the first completion |

`/api/**` trusts `X-User-Id` set by the gateway (SPEC §5.1); a missing header
returns `401 RANK_UNAUTHORIZED`. `/internal/**` must not be exposed by the gateway.
In the `dev` profile only, `X-Dev-User` (the FE `?devUser` identity, read like
game-service does) wins over `X-User-Id`, because the shared FE http client also
sends a default `X-User-Id` in dev; any dev request without `X-User-Role` acts as
`ADMIN`.

Consumed events on exchange `poker.events`, each with its own durable queue:

| Queue | Routing key | Effect |
| --- | --- | --- |
| `ranking-service.account.registered` | `account.registered` | Creates the season Elo record (NEWBIE, 0) |
| `ranking-service.match.finished` | `match.finished` | `RANK` only: Elo by place and table size (floor 0), peak, tier, streaks, history, rewards |

Players that arrive in `match.finished` before `account.registered` get a
record on the fly. `elo_history` is unique per `(accountId, matchId)`, so a
redelivered match changes nothing. Malformed or invalid payloads are logged and
dropped; database failures are retried (5 attempts).

Elo by place (SPEC §8): a 6-player table gets +20/+10/+5/−5/−10/−15. A smaller
table splits in half: the top half takes the first values, the bottom half the
last ones, and the middle seat of an odd table keeps its Elo. 2 players: +20/−15;
3: +20/0/−15; 4: +20/+10/−10/−15; 5: +20/+10/0/−10/−15. Places that gain Elo
(at most the top 3) extend the win streak, places that lose Elo extend the loss
streak, and the middle seat ends both.

Rank rewards are 3×/2×/1× the entry fee from the top, but never more than the fees
the table paid in: 2 players 2×, 3 players 3×, 4 players 3×/1×, 5 players 3×/2×.
They are stored in `rank_reward_credits` in the
same transaction as the Elo update, then sent to wallet-service
`POST /internal/wallet/credit` with reason `RANK_REWARD`, `refId` = matchId and
key `rank-reward:{matchId}:{accountId}`. A wallet outage leaves the reward
pending; the dispatcher retries every 5 seconds with the same key.

### Friends leaderboard

`GET /api/ranking/friends` reads the friend ids from social-service
`GET /internal/friends/{accountId}` (raw JSON array, SPEC §6.1) before touching
the database, then reads Elo records and season positions from one snapshot.
Ranked players come first by season `position`; friends without a ranked match
this season follow with the starting Elo and `position: null`. `rank` numbers the
whole list, `you` marks the caller. A social-service failure returns
`503 RANK_DEPENDENCY_UNAVAILABLE`.

### Tutorial reward

The tutorial itself runs in the FE (SPEC §9.4). `POST /api/tutorial/complete`
inserts one `tutorial_completions` row per account; only the request that inserts
it queues a `reward_grants` row, so concurrent calls grant once. Coins
(`ranking.tutorial-reward-coins`, 200) go to wallet-service with reason
`TUTORIAL_REWARD`, no `refId` and key `tutorial:{accountId}`, retried until
accepted like season rewards. The amount granted is stored with the completion.

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
- Eureka: `EUREKA_CLIENT_ENABLED` (`true`), `EUREKA_SERVICE_URL`, `EUREKA_INSTANCE_HOSTNAME`.
- Wallet: `WALLET_SERVICE_URL` (default `http://wallet-service`).
- Shop: `SHOP_SERVICE_URL` (default `http://shop-service`).
- Social: `SOCIAL_SERVICE_URL` (default `http://social-service`) and `SOCIAL_INTERNAL_KEY`, sent as
  `X-Social-Internal-Key` and equal to social-service's `SOCIAL_INTERNAL_KEY`. social-service answers 403
  without it, so the Friends leaderboard fails with `RANK_DEPENDENCY_UNAVAILABLE` until both sides set it.
- Mocks for services not running locally: `ranking.wallet.mock`, `ranking.shop.mock`,
  `ranking.social.mock` (`false`; `true` in `application-dev.properties` and tests).
- SPEC §8 parameters: `ranking.elo-by-place`, `ranking.tier-thresholds`,
  `ranking.reward-multipliers`, `ranking.win-streak-places`, `ranking.card-god-limit`,
  `ranking.min-elo`, `ranking.starting-elo` in `application.properties`.
- Tutorial reward: `ranking.tutorial-reward-coins` (200).

## Verification

```powershell
.\mvnw.cmd -pl ranking-service -am clean verify
```

39 tests use test-only H2 and do not require Docker: tier boundaries, Elo
floor, places beyond six, Card God cut-off, duplicate events, non-RANK modes,
invalid results, reward queueing and wallet outage retry, listener parsing with
unknown fields, API contracts, leaderboard ordering, season closing and carry
over, scheduled closing and activation, cumulative claims, grant retries, the
dev identity filter, the friends leaderboard (order, cap, social outage, HTTP
client) and the tutorial reward (once, also under concurrent calls).
Season tests use their own H2 database because they close the seed season.
