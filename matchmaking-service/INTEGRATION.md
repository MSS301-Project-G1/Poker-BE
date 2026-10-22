# Matchmaking integration

The Normal and Rank workflows use real Game, Ranking, Wallet and Auth APIs by
default in root Compose. Matchmaking, Game, Ranking, Wallet, Shop and Competition
register their container hostnames in Eureka. The gateway preserves
`/api/matchmaking/**` and `/api/party/**` and derives identity from its JWT cookie.

## Run locally

Open Docker Desktop, then run from Poker-BE:

```powershell
docker compose up -d --build
```

If `AUTH_MAIL_HOST=auth-mailpit`, also run
`docker compose --profile mailpit up -d auth-mailpit`. Keep the existing database
and broker volumes. The base Compose Auth client uses the shared broker's
`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`; the separate auth dev override keeps its
own matching `AUTH_RABBIT_*` credentials. Game/Ranking/Competition/Matchmaking
accept `RABBITMQ_USER` with `RABBITMQ_USERNAME` as its fallback.

Match `VITE_API_BASE_URL` in FE to the published gateway port. The default is
8080; a stack publishing 18080 needs `http://localhost:18080`. Restart Vite after
changing env files. `GameIntegration` supplies an in-memory token from
`GET /api/auth/ws-token` for STOMP; Game REST uses the cookie gateway client.
The existing `/ws/game` Vite/nginx proxy still connects directly to Game.

## Contracts

| Consumer | Provider API | Contract |
|---|---|---|
| Matchmaking | Game `GET /internal/match-settings/{mode}` | minPlayers/maxPlayers |
| Matchmaking | Game `POST /internal/tables` | mode, sourceId, playerIds, settings, entryFee -> tableId/matchId |
| Matchmaking | Game `GET /internal/players/{id}/active-table` | tableId/matchId or null |
| Matchmaking | Ranking `GET /internal/ranking/{id}` | accountId/elo/winStreak/lossStreak |
| Matchmaking, Game | Auth `GET /internal/accounts/{id}/restrictions` | banned/chatBannedUntil/rankBannedUntil |
| Matchmaking | Wallet `POST /internal/wallet/debit` | reason RANK_ENTRY_FEE; stable rank-fee key |
| Matchmaking | Wallet `POST /internal/wallet/credit` | reason RANK_ENTRY_REFUND; stable rank-refund key |

Game and Auth internal requests require `X-Game-Service-Key`; configure the same
`GAME_SERVICE_KEY` for callers and providers. Auth also allows a separate
`AUTH_INTERNAL_SERVICE_KEY` override, which must then match caller configuration.
The gateway blocks `/internal/**`. Unknown/deleted Auth accounts fail closed;
unverified, inactive or banned accounts cannot enter a new game. Timed bans are
stored on the account; a moderation UI is outside this integration.

Wallet insufficient funds is HTTP 400 / `WALLET_INSUFFICIENT_BALANCE`. A 409
concurrent update is retryable and must not apply a player cooldown as if it were
insufficient funds. Legacy databases initialized by wallet `init.sql` are
normalized by the Flyway beforeMigrate callback before V2; published V1/V2
migrations are unchanged. New databases are initialized exclusively by Flyway.

## Frontend flow

`/ranked` uses Rank; Lobby Quick Play opens `/ranked?mode=NORMAL`. The queue page
loads existing status on entry, joins only on user action, polls server status,
cancels through DELETE, accepts/declines a proposal and navigates to
`/table/{tableId}` only after READY. Accept countdown uses the server deadline.
Party membership can be restored with `GET /api/party/me` (object or null).
Realtime MATCH_FOUND/MATCH_READY publishing is still pending; polling currently
provides status delivery. Other players' profiles/chat/cosmetics are separate
integration work.

FE contract/browser tests: `npm run test:matchmaking`. These use mocked HTTP to
exercise UI state and CSRF; they do not replace a real backend integration run.
