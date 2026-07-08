# 주식 인사이트

React + Vite frontend and Spring Boot backend starter for a Korean stock analysis service.
The backend reads Korean stock current prices from Naver Finance realtime JSON and price charts from the Yahoo Finance chart endpoint, then falls back to mock data if external calls fail.
Financial metrics and AI analysis are still mock data so the app can run before official finance and AI API keys are ready.
Market events can be loaded from the FMP Economic Calendar API when `FMP_API_KEY` is configured, and otherwise fall back to beginner-friendly local events.

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

## Run PostgreSQL

```bash
docker compose up -d
```

PostgreSQL runs on `localhost:5432`.

- Database: `stock_app`
- User: `stock`
- Password: `stock`

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

The backend runs at `http://localhost:8080`.
By default, it starts without requiring PostgreSQL.

To run with PostgreSQL, start Docker first and enable the `postgres` profile:

```bash
docker compose up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Useful environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stock_app
SPRING_DATASOURCE_USERNAME=stock
SPRING_DATASOURCE_PASSWORD=stock
CORS_ALLOWED_ORIGINS=http://localhost:5173
FMP_API_KEY=your_fmp_api_key
MARKET_EVENTS_LOOK_AHEAD_DAYS=60
MARKET_EVENTS_REFRESH_MS=21600000
```

## Run Frontend

```bash
cd frontend
npm install
npm run dev
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
- `POST /api/stocks/{symbol}/analysis`
- `GET /api/stocks/{symbol}/analysis/latest`
- `GET /api/market-events`
- `POST /api/market-events/refresh`

## Stock Price Integration

The backend maps internal symbols to Korean market symbols:

- `SAMSUNG` -> `005930.KS`
- `SKHYNIX` -> `000660.KS`
- `NAVER` -> `035420.KS`
- `KAKAO` -> `035720.KS`
- `HYUNDAI` -> `005380.KS`
- `LGENERGY` -> `373220.KS`

Current price data is fetched from Naver Finance realtime URLs such as:

```text
https://polling.finance.naver.com/api/realtime/domestic/stock/005930
```

Historical chart data is fetched from Yahoo Finance chart URLs such as:

```text
https://query1.finance.yahoo.com/v8/finance/chart/005930.KS?range=5d&interval=1d
```

This is useful for local testing without an API key, but it should be replaced with an official provider such as KIS Developers before production use.

## Market Event Integration

Economic calendar data is fetched through `MarketEventClient`.
The current real provider implementation is `FmpMarketEventClient`, which uses the FMP economic calendar endpoint:

```text
https://financialmodelingprep.com/stable/economic-calendar
```

Set `FMP_API_KEY` before running the backend to load real upcoming events. The backend refreshes its in-memory market event cache on startup and every `MARKET_EVENTS_REFRESH_MS` milliseconds. If the API key is missing or the external call fails, the app keeps serving curated fallback events so the UI remains usable.

Useful settings:

```bash
FMP_API_KEY=your_fmp_api_key
MARKET_EVENTS_LOOK_AHEAD_DAYS=60
MARKET_EVENTS_REFRESH_MS=21600000
```

Use `POST /api/market-events/refresh` to force a refresh during local testing.

## AI Integration Point

Backend AI analysis is isolated behind `AiAnalysisClient`.
The current implementation is `MockAiAnalysisClient`, which returns generated mock analysis.
Later, replace it with an HTTP client that calls the real AI server while keeping the controller and service contracts stable.
