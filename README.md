# 주식 인사이트

React + Vite frontend and Spring Boot backend for a Korean stock insight service.
The product goal is to provide the information a beginner needs before making their own trading decision, without giving direct investment action recommendations.
The backend reads KOSPI stock master data from KRX KIND and stores Toss OHLC candles in PostgreSQL before user analysis.
Financial metrics show only values that can be calculated from OpenDART when `OPENDART_API_KEY` is configured.
Market events combine rule-based expiry dates with optional FMP corporate calendars, Trading Economics macro calendars, and FRED release dates.
The AI area focuses on chart pattern classification and reference return statistics for similar patterns.

For deployment details, see [DEPLOYMENT.md](./DEPLOYMENT.md).

## Project Structure

```text
.
|-- frontend
|-- backend
|-- docker-compose.yml
`-- README.md
```

## Requirements

- Node.js 18+
- Java 17+
- Maven 3.9+
- Docker Desktop, for PostgreSQL

## Quick Start

Create a local environment file first:

```powershell
Copy-Item .env.example .env
```

Then open `.env` and fill only the keys you have. `OPENDART_API_KEY` is optional, but enables real OpenDART financial statement enrichment.

Start both backend and frontend in separate PowerShell windows:

```powershell
.\start-dev.bat
```

or:

```powershell
.\start-dev.ps1
```

Open the app:

```text
http://127.0.0.1:5173
```

You can also run each side manually:

```powershell
.\start-backend.bat
.\start-frontend.bat
```

Stop each server with `Ctrl+C` in its terminal window.

## Run PostgreSQL

```bash
docker compose up -d
```

PostgreSQL runs on `localhost:5432`.

- Database: `stock_app`
- User: `stock`
- Password: `stock`

The `postgres` profile creates the core service tables through JPA/Hibernate:

- `stocks`: KRX KOSPI stock master data
- `stock_candles`: Toss OHLC daily candles, keyed by symbol/date/source
- `chart_pattern_analysis_runs`: one chart-pattern analysis execution
- `chart_pattern_period_results`: 6M/12M pattern classification result rows for each run
- `analysis_results`: legacy/general AI summary result table

Local mode does not require PostgreSQL. Postgres mode enables persistence:

```powershell
docker compose up -d
.\start-backend.ps1 -Postgres
```

## Run Backend

Recommended:

```powershell
.\start-backend.ps1
```

The backend runs at `http://localhost:8080`.
By default, it starts without requiring PostgreSQL.

To run with PostgreSQL, start Docker first and enable the `postgres` profile:

```powershell
docker compose up -d
.\start-backend.ps1 -Postgres
```

Useful environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stock_app
SPRING_DATASOURCE_USERNAME=stock
SPRING_DATASOURCE_PASSWORD=stock
CORS_ALLOWED_ORIGINS=http://localhost:5173
FMP_API_KEY=your_fmp_api_key
TRADING_ECONOMICS_API_KEY=your_trading_economics_key
FRED_API_KEY=your_fred_api_key
OPENDART_API_KEY=your_opendart_key
TOSS_CLIENT_ID=your_toss_client_id
TOSS_CLIENT_SECRET=your_toss_client_secret
KOSPI_RENDER_API_KEY=your_render_api_key
AI_CHART_IMAGE_URL=https://kospi-data-api.onrender.com/v1/render
AI_PATTERN_SERVER_PREDICT_URL=https://stock-api-server-r63u.onrender.com/predict/
AI_PATTERN_SERVER_TIMEOUT_SECONDS=180
CHART_PATTERN_AI_ON_DEMAND_ENABLED=false
CHART_PATTERN_DEMO_SEED_ENABLED=true
CHART_PATTERN_DEMO_SEED_RESET=true
CANDLE_SYNC_ENABLED=false
CANDLE_SYNC_CRON=0 30 21 * * MON-FRI
CANDLE_SYNC_DELAY_MS=500
CHART_PATTERN_BATCH_ENABLED=false
CHART_PATTERN_BATCH_CRON=0 0 22 * * MON-FRI
CHART_PATTERN_BATCH_DELAY_MS=1000
MARKET_EVENTS_LOOK_AHEAD_DAYS=60
MARKET_EVENTS_REFRESH_MS=21600000
```

## Run Frontend

Recommended:

```powershell
.\start-frontend.ps1
```

The frontend runs at `http://localhost:5173`.

Optional frontend API URL:

```bash
VITE_API_BASE_URL=http://localhost:8080/api
```

## API Endpoints

- `GET /api/stocks`
- `GET /api/stocks/search?keyword=`
- `GET /api/stocks/{symbol}`
- `GET /api/stocks/{symbol}/prices?range=1M|3M|6M|1Y`
- `GET /api/stocks/{symbol}/financials`
- `GET /api/stocks/{symbol}/events`
- `POST /api/stocks/{symbol}/chart-pattern-analysis`
- `GET /api/stocks/{symbol}/chart-pattern-analysis/latest`
- `POST /api/admin/candles/kospi`
- `GET /api/admin/candles/kospi`
- `GET /api/market-events`
- `POST /api/market-events/refresh`

## Chart Pattern Analysis

The detail page shows an AI chart pattern area instead of a suitability score.
The two-step AI integration code is still present, but the default demo mode does not call AI servers from user requests.
Instead, the backend seeds about 20 presentation-ready chart-pattern results into PostgreSQL and serves those DB results immediately.
By default, `CHART_PATTERN_DEMO_SEED_RESET=true` clears old chart-pattern analysis rows on startup so the demo DB contains only the curated seed set.

It provides:

- detected chart pattern name and category
- pattern recognition confidence, not an investment score
- 6 month and 12 month AI classification results
- expert-reference return tendencies for each detected pattern
- checkpoints for data/API readiness

The service intentionally does not provide direct action recommendations. It presents data that users can use as one input for their own judgment.

Expected production flow:

```text
Startup -> Seed demo chart-pattern results into PostgreSQL
User request -> Backend reads latest chart_pattern_analysis_runs result
Cached result exists -> Backend returns stored pattern/confidence/chart image immediately
Cached result missing -> Backend returns "analysis preparing"
```

To re-enable the real on-demand AI flow later:

```bash
CHART_PATTERN_AI_ON_DEMAND_ENABLED=true
```

Toss Open API requires the backend server's outbound IPv4 to be registered in Toss WTS. For local testing, register the IPv4 from:

```powershell
curl -4 https://api.ipify.org
```

With `CHART_PATTERN_AI_ON_DEMAND_ENABLED=false`, chart pattern analysis never calls Toss or the AI servers during the user request.
If the requested stock has no DB result, the frontend shows an analysis-preparing state.
Use `refresh=true` only after enabling on-demand AI flow:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/stocks/005930/chart-pattern-analysis?refresh=true" -Method Post
```

## KOSPI Candle Pre-Collection

Before chart-pattern analysis, collect and store OHLC candles for KOSPI stocks:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/candles/kospi?months=12" -Method Post
```

Small test:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/candles/kospi?limit=3&months=12" -Method Post
```

Status:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/admin/candles/kospi -Method Get
```

Automatic candle collection is prepared but disabled by default:

```bash
CANDLE_SYNC_ENABLED=true
CANDLE_SYNC_CRON=0 30 21 * * MON-FRI
CANDLE_SYNC_DELAY_MS=500
```

With that setting, the backend collects KOSPI OHLC data at 21:30 Asia/Seoul on weekdays.

## KOSPI Stock Data And Candle Sync

Stock search uses the `stocks` table first when PostgreSQL mode is enabled.
The backend still loads the KOSPI stock master from KRX KIND on startup and mirrors it into `stocks`, but user-facing list, search, and detail lookups are DB-first.
If PostgreSQL is not enabled or the table is empty, the service falls back to the in-memory KRX list for local development.

The same DB-first stock list is used for OHLC candle pre-collection.
Current operation does not run whole-market AI chart analysis in advance.
The presentation mode serves seeded DB analysis rows and shows "analysis preparing" for stocks without stored analysis.

The legacy chart-pattern batch settings remain disabled by default:

```bash
CHART_PATTERN_BATCH_ENABLED=false
```

## Stock Price Integration

The backend loads KOSPI stock master data from KRX KIND on startup.
Symbols are six-digit Korean stock codes such as `005930`.
For Naver/Yahoo requests, the backend maps them to exchange-specific forms such as `005930.KS`.

Current price data is fetched from Naver Finance realtime URLs such as:

```text
https://polling.finance.naver.com/api/realtime/domestic/stock/005930
```

Historical chart data is fetched from Yahoo Finance chart URLs such as:

```text
https://query1.finance.yahoo.com/v8/finance/chart/005930.KS?range=5d&interval=1d
```

This is useful for local testing without an API key, but it should be replaced with an official provider such as KIS Developers before production use.

## Financial Data Integration

When `OPENDART_API_KEY` is set, `OpenDartFinancialDataClient` tries to load official Korean financial statements:

```text
https://opendart.fss.or.kr/api/corpCode.xml
https://opendart.fss.or.kr/api/fnlttSinglAcnt.json
```

OpenDART requires `corp_code`, so the backend first downloads the official corp code ZIP and maps it to each stock code.
The current implementation shows accounting-derived values such as ROE, revenue growth, and debt ratio when DART data is available.
Market-derived values such as PER, PBR, EPS, dividend yield, and market cap remain blank until an official market-data provider is connected.

## Market Event Integration

Market event data is fetched through multiple `MarketEventClient` implementations and merged in memory:

- `RuleBasedMarketEventClient`: calculates Korean futures/options expiry and US witching dates without any API key.
- `FmpMarketEventClient`: uses FMP free-access corporate calendars such as earnings, dividends, and IPOs.
- `TradingEconomicsMarketEventClient`: uses Trading Economics for macro events such as CPI, rates, GDP, employment, retail sales, and PMI.
- `FredReleaseEventClient`: uses FRED release dates as a backup source for major US economic releases.

Relevant external endpoints:

```text
https://financialmodelingprep.com/stable/earnings-calendar
https://financialmodelingprep.com/stable/dividends-calendar
https://financialmodelingprep.com/stable/ipos-calendar
https://api.tradingeconomics.com/calendar/country/All/{from}/{to}
https://api.stlouisfed.org/fred/release/dates
```

The backend refreshes its in-memory market event cache on startup and every `MARKET_EVENTS_REFRESH_MS` milliseconds. If API keys are missing or a provider fails, the app still serves rule-based or fallback events so the UI remains usable.

Useful settings:

```bash
FMP_API_KEY=your_fmp_api_key
TRADING_ECONOMICS_API_KEY=your_trading_economics_key
FRED_API_KEY=your_fred_api_key
MARKET_EVENTS_LOOK_AHEAD_DAYS=60
MARKET_EVENTS_REFRESH_MS=21600000
```

Use `POST /api/market-events/refresh` to force a refresh during local testing.

## AI Integration Point

Backend AI analysis is isolated behind `AiAnalysisClient`.
By default, mock AI summaries are disabled. Set `AI_ANALYSIS_SERVER_URL` when the real AI summary server is ready.
Chart pattern image rendering uses Toss candles plus the render API when `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`, and `KOSPI_RENDER_API_KEY` are configured.
