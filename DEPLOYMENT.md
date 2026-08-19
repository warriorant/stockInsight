# Deployment Guide

이 문서는 배포 담당자가 `git pull` 이후 바로 확인해야 할 실행/환경변수/운영 절차를 정리한 문서입니다.

## 배포 담당자 빠른 요약

발표용 현재 버전은 **AI 서버를 실시간 호출하지 않고**, 백엔드가 시작될 때 PostgreSQL에 20개 데모 분석 결과를 자동으로 넣은 뒤 사용자가 종목을 누르면 DB에서 바로 꺼내 보여주는 구조입니다.

배포 담당자는 아래 순서대로 진행하면 됩니다.

```text
1. PostgreSQL 준비
2. 백엔드 환경변수 설정
3. backend/Dockerfile 기준으로 백엔드 빌드/실행
4. 프론트엔드 빌드 시 VITE_API_BASE_URL을 배포된 백엔드 주소로 설정
5. 프론트엔드 정적 파일 배포
6. 백엔드 로그에서 데모 seed 삽입 확인
```

주의할 점:

- `.env`는 프로젝트 루트에 두는 파일입니다. `backend/` 안에 넣지 않습니다.
- 이 프로젝트의 DB 환경변수 이름은 `DB_URL`이 아니라 `SPRING_DATASOURCE_URL`입니다.
- 발표 모드에서는 `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false`를 유지합니다.
- 발표 모드에서는 `CHART_PATTERN_BATCH_ENABLED=false`를 유지합니다. 코스피 전체 AI 분석 배치는 돌리지 않습니다.
- 백엔드가 켜질 때 `CHART_PATTERN_DEMO_SEED_ENABLED=true`, `CHART_PATTERN_DEMO_SEED_RESET=true`면 데모 분석 20개가 자동 insert 됩니다.
- 배포 DB는 로컬 노트북 DB가 아닙니다. Render PostgreSQL, AWS RDS, Supabase, Railway, 서버 내부 PostgreSQL 등 배포 환경에서 접근 가능한 PostgreSQL을 사용해야 합니다.

## Current Architecture

현재 서비스의 기준 흐름은 다음과 같습니다.

```text
1. Backend loads KOSPI stock master data.
2. Backend seeds about 20 presentation-ready chart-pattern analysis rows into PostgreSQL.
3. User opens a stock detail page and requests chart-pattern analysis.
4. Backend reads the latest stored analysis result from PostgreSQL.
5. If a DB result exists, backend returns pattern/confidence/reference stats/chart images immediately.
6. If no DB result exists, backend returns an "analysis preparing" response.
```

AI server 1 and AI server 2 integration code remains in the backend, but the final presentation mode keeps on-demand AI calls disabled.
The AI servers can be re-enabled later by changing environment variables.

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
AI_ANALYSIS_SERVER_URL=
AI_CHART_IMAGE_MOCK_ENABLED=false
AI_CHART_PATTERN_MOCK_ENABLED=false
AI_ANALYSIS_MOCK_ENABLED=false
CHART_PATTERN_AI_ON_DEMAND_ENABLED=false
CHART_PATTERN_DEMO_SEED_ENABLED=true
CHART_PATTERN_DEMO_SEED_RESET=true

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
- Use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`; the backend does not read `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD`.
- `CORS_ALLOWED_ORIGINS` must be the frontend deployment URL.
- `VITE_API_BASE_URL` must be set when building the frontend.
- `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false` prevents user requests from calling AI server 1/2.
- `CHART_PATTERN_DEMO_SEED_ENABLED=true` inserts presentation-ready demo analysis rows on startup.
- `CHART_PATTERN_DEMO_SEED_RESET=true` clears old chart-pattern analysis rows before inserting the curated 20-row demo set.
- Register the backend server's outbound public IPv4 in Toss WTS only if collecting real candles.
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

With the default demo seed settings, these chart-pattern analysis tables are reset on backend startup and refilled with the curated presentation data.

## OHLC Pre-Collection

This is optional in the final presentation mode because user analysis reads seeded DB results.
Run it only when testing the real Toss OHLC path.

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

- If an analysis result for the symbol exists, backend returns the latest DB row immediately.
- If no stored result exists and `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false`, backend returns "analysis preparing".
- If `CHART_PATTERN_AI_ON_DEMAND_ENABLED=true`, backend can still use the preserved real AI server 1/2 flow.

Force rerun:

```powershell
Invoke-RestMethod -Uri "https://BACKEND_DOMAIN/api/stocks/005930/chart-pattern-analysis?refresh=true" -Method Post
```

With `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false`, `refresh=true` still returns DB/preparing results and does not call AI servers.
Use `refresh=true` for real AI reruns only after enabling `CHART_PATTERN_AI_ON_DEMAND_ENABLED=true`.

## Recommended Demo Checklist

1. Start PostgreSQL.
2. Start backend with `SPRING_PROFILES_ACTIVE=postgres`.
3. Confirm backend health by opening `/api/stocks`.
4. Confirm startup logs show demo chart pattern seed insertion or "already exists".
5. Open a seeded stock such as Samsung Electronics (`005930`) and confirm analysis appears immediately.
6. Open a non-seeded stock and confirm the frontend shows "분석 준비 중".
7. Keep `CHART_PATTERN_BATCH_ENABLED=false` and `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false` for presentation stability.

## Known Notes

- AI pattern quality is controlled by AI server 2. Backend only maps `pattern` and `confidence` to labels and reference stats.
- The final presentation path uses seeded DB results, not live AI server responses.
- Generated chart images are stored in `chart_pattern_period_results` as data URLs so cached results can show the exact image used for classification.
- Old cached analysis rows created before image storage may not show images. The demo seed creates image-backed rows.
- Current operation does not pre-run AI analysis for all KOSPI stocks.
