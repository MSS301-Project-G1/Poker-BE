# Start all services with clear containment groups in Docker Desktop
Write-Host "Starting Wallet Service group (wallet-service + wallet-postgres)..." -ForegroundColor Cyan
docker compose -f wallet-service/docker-compose.yml up -d

Write-Host "Starting Shop Service group (shop-service + shop-postgres + shop-redis)..." -ForegroundColor Cyan
docker compose -f shop-service/docker-compose.yml up -d

Write-Host "Starting Team Gateway & Services group (api-gateway + other microservices)..." -ForegroundColor Cyan
docker compose up -d

Write-Host "`nAll services successfully started! Check Docker Desktop for clean contained groups." -ForegroundColor Green
