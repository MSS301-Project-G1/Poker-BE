# Integration scope on develop

This branch contains changes owned by Duy: matchmaking-service, chat-service,
social-service and the matchmaking feature in Poker-FE. Changes to Auth, Game,
Ranking, Wallet, Shop, Competition, and the FE Auth/Game runtime are retained
only on the separate testing branch.

## Work included here

- Eureka client configuration for Matchmaking, Chat and Social. Each Compose
  service advertises its container hostname; tests disable discovery.
- Matchmaking Game client uses the existing internal settings, create-table and
  active-table APIs with X-Game-Service-Key.
- Wallet client sends RANK_ENTRY_FEE/RANK_ENTRY_REFUND and recognizes HTTP 400 /
  WALLET_INSUFFICIENT_BALANCE. Concurrent update errors remain retryable.
- Restrictions client targets Auth and sends the internal key, ready for its
  owner to provide the account-restrictions API.
- GET /api/party/me restores current Party state after reload.
- FE matchmaking uses the existing shared gatewayRequest client for Normal/Rank
  join/status/leave/accept/decline. It polls server status, uses server deadlines,
  and hands READY results to /table/{tableId}. Lobby Quick Play selects NORMAL.

Chat and Social are currently scaffolds in this checkout. Registering them in
Eureka does not implement friends, messaging or notifications. Their existing
Gateway route files are unchanged; business APIs must be aligned when added.

## Dependencies to coordinate with their owners

| Owner | Required work |
|---|---|
| Auth | GET /internal/accounts/{id}/restrictions returning banned/chatBannedUntil/rankBannedUntil; agree on X-Game-Service-Key |
| Wallet | Repair legacy schema migration and confirm debit/credit contract and insufficient-funds error |
| Game/Ranking | Register with Eureka, expose current contracts, match internal key and broker configuration |
| Shared FE/Auth + Game owner | Supply GameRuntimeContext token from /api/auth/ws-token; route authenticated Game REST through Gateway |

Root Gateway routes already include /api/matchmaking/** and /api/party/**.
Routes for other owners are not changed here. Keep service registration and
network configuration with the owner of that service.

Compose defaults Wallet/Restriction stubs to true on develop because the original
owner dependencies are not yet ready. Set MATCHMAKING_WALLET_STUB_ENABLED=false
and MATCHMAKING_RESTRICTION_STUB_ENABLED=false only when the real dependencies
are available. Stub wallets do not debit real funds; stub restrictions do not
check bans. Code-level defaults remain false so running Java without Compose
must explicitly choose its development stubs.

## Verification and branch use

Run the owned BE modules with Java 21:

```powershell
.\mvnw.cmd -pl matchmaking-service,chat-service,social-service -am clean verify
```

FE: npm run lint, npm run build, npm run test:matchmaking. Browser tests mock
HTTP and verify matchmaking's API calls, CSRF, deadlines and navigation. They do
not certify a working Game screen; that belongs to the shared Auth/Game adapter.

Full cross-service integration, database fixes and Game authentication adapters
are saved on testing in both repositories. Do not merge that whole branch into
develop as Duy's scoped work. Running Docker containers and database migrations
do not switch when Git branches change; the existing local containers/volumes
still belong to the previously tested integration snapshot.
