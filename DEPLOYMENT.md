# Deployment Guide

이 문서는 배포 담당자가 `git pull` 이후 바로 확인해야 할 실행/환경변수/운영 절차를 정리한 문서입니다.

## Current Architecture

현재 서비스의 기준 흐름은 다음과 같습니다.

```text
1. Backend loads KOSPI stock master data.
2. Operator or scheduler collects Toss OHLC candles for KOSPI stocks.
3. Backend stores OHLC rows in PostgreSQL stock_candles.
4. User opens a stock detail page and requests chart-pattern analysis.
5. Backend checks today's cached analysis result first.
6. If no cached result exists, backend reads OHLC from PostgreSQL.
7. Backend sends the same candle JSON as before to AI server 1.
8. AI server 1 returns chart images.
9. Backend sends those images to AI server 2.
10. AI server 2 returns pattern/confidence.
11. Backend stores and returns the final enriched result, including generated chart images.
```

AI server 1 and AI server 2 do not need API contract changes for this backend update.
Only the OHLC source changed from "Toss during user request" to "PostgreSQL first".

## Repository Layout

```text
stockInsight/
  .env
  docker-compose.yml
  README.md
  DEPLOYMENT.md
  frontend/
  backend/
    Dockerfile
    pom.xml
```

Place the real `.env` file at the project root, next to `docker-compose.yml`.
Do not put it inside `backend/`.

## Required Environment Variables

Use these variables for backend and frontend deployment.
Replace local URLs with deployed service URLs.

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=postgres
CORS_ALLOWED_ORIGINS=https://FRONTEND_DOMAIN

SPRING_DATASOURCE_URL=jdbc:postgresql://DB_HOST:5432/DB_NAME
SPRING_DATASOURCE_USERNAME=DB_USER
SPRING_DATASOURCE_PASSWORD=DB_PASSWORD

OPENDART_API_KEY=YOUR_OPENDART_KEY

TOSS_CLIENT_ID=YOUR_TOSS_CLIENT_ID
TOSS_CLIENT_SECRET=YOUR_TOSS_CLIENT_SECRET
TOSS_TOKEN_URL=https://openapi.tossinvest.com/oauth2/token
TOSS_CANDLES_URL=https://openapi.tossinvest.com/api/v1/candles

AI_CHART_IMAGE_URL=https://kospi-data-api.onrender.com/v1/render
KOSPI_RENDER_API_KEY=YOUR_RENDER_API_KEY

AI_PATTERN_SERVER_PREDICT_URL=https://stock-api-server-r63u.onrender.com/predict/
AI_PATTERN_SERVER_TIMEOUT_SECONDS=180

FMP_API_KEY=
FRED_API_KEY=
TRADING_ECONOMICS_API_KEY=

MARKET_EVENTS_LOOK_AHEAD_DAYS=60
MARKET_EVENTS_REFRESH_MS=21600000

CANDLE_SYNC_ENABLED=false
CANDLE_SYNC_CRON=0 30 21 * * MON-FRI
CANDLE_SYNC_DELAY_MS=500

CHART_PATTERN_BATCH_ENABLED=false
CHART_PATTERN_BATCH_CRON=0 0 22 * * MON-FRI
CHART_PATTERN_BATCH_DELAY_MS=1000

VITE_API_BASE_URL=https://BACKEND_DOMAIN/api
```

Important:

- `SPRING_PROFILES_ACTIVE=postgres` is required for PostgreSQL persistence.
- `CORS_ALLOWED_ORIGINS` must be the frontend deployment URL.
- `VITE_API_BASE_URL` must be set when building the frontend.
- Register the backend server's outbound public IPv4 in Toss WTS before collecting candles.
- Keep `CHART_PATTERN_BATCH_ENABLED=false`; current operation does not run whole-market AI analysis.

## Backend Deployment

Build context should be the `backend/` folder because `backend/Dockerfile` expects `pom.xml` and `mvnw` in the same folder.

```powershell
cd backend
docker build -t stock-insight-backend .
```

Run example:

```powershell
docker run --env-file ../.env -p 8080:8080 stock-insight-backend
```

For cloud platforms, enter the same `.env` values in the platform's environment variable settings.

## Frontend Deployment

Install and build from the `frontend/` folder.

```powershell
cd frontend
npm install
npm run build
```

The frontend build must receive:

```env
VITE_API_BASE_URL=https://BACKEND_DOMAIN/api
```

## PostgreSQL

Local test DB:

```powershell
docker compose up -d
```

Default local DB:

```text
host: localhost
port: 5432
database: stock_app
username: stock
password: stock
```

Production DB can be any PostgreSQL-compatible service.
The backend creates/updates tables with Hibernate `ddl-auto=update`.

Important tables:

- `stocks`: KOSPI stock master
- `stock_candles`: Toss OHLC candles
- `chart_pattern_analysis_runs`: cached/enriched chart-pattern analysis results
- `chart_pattern_period_results`: 6M/12M classification details and generated chart image data URLs

## OHLC Pre-Collection

Run this before the demo or after market data needs refreshing.

Small test:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/admin/candles/kospi?limit=5&months=12" -Method Post
```

Full KOSPI collection:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/admin/candles/kospi?months=12" -Method Post
```

Status:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/admin/candles/kospi" -Method Get
```

Expected scale:

- About 829 KOSPI stocks
- About 240 to 250 daily candles per stock for 12 months
- About 200,000 rows for one full-year KOSPI collection

This is safe for PostgreSQL.

## User Analysis And Cache

Normal user analysis:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/stocks/005930/chart-pattern-analysis" -Method Post
```

Behavior:

- If today's analysis for the symbol already exists, backend returns DB cache immediately.
- If not cached, backend reads OHLC from `stock_candles` and calls AI server 1 and AI server 2.
- If fewer than 100 stored candles are available, backend falls back to direct Toss fetch for local/dev convenience.

Force rerun:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/stocks/005930/chart-pattern-analysis?refresh=true" -Method Post
```

Use `refresh=true` only for debugging because it calls AI servers again.

## Recommended Demo Checklist

1. Start PostgreSQL.
2. Start backend with `SPRING_PROFILES_ACTIVE=postgres`.
3. Confirm backend health by opening `/api/stocks`.
4. Run candle pre-collection with `limit=5`.
5. Confirm `GET /api/admin/candles/kospi` shows `failureCount=0`.
6. Run one stock analysis with `refresh=true`.
7. Run the same stock analysis again without `refresh=true`.
8. Confirm the second request is much faster because DB cache is used.
9. If small test is stable, run full KOSPI candle collection.

## Known Notes

- AI pattern quality is controlled by AI server 2. Backend only maps `pattern` and `confidence` to labels and reference stats.
- Many real KOSPI charts currently return `패턴19 / Other(노이즈)`.
- Generated chart images are stored in `chart_pattern_period_results` as data URLs so cached results can show the exact image used for classification.
- Old cached analysis rows created before image storage may not show images. Run analysis with `refresh=true` once to create a new image-backed cache row.
- Current operation does not pre-run AI analysis for all KOSPI stocks. Only OHLC is collected in advance.
