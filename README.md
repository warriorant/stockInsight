# 주식 인사이트

React + Vite frontend and Spring Boot backend starter for a Korean stock analysis service.
The backend now reads stock prices and price charts from the Yahoo Finance chart endpoint first, then falls back to mock data if the external call fails.
Financial metrics and AI analysis are still mock data so the app can run before official finance and AI API keys are ready.

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
- `POST /api/stocks/{symbol}/analysis`
- `GET /api/stocks/{symbol}/analysis/latest`

## Stock Price Integration

The backend maps internal symbols to Korean market symbols:

- `SAMSUNG` -> `005930.KS`
- `SKHYNIX` -> `000660.KS`
- `NAVER` -> `035420.KS`
- `KAKAO` -> `035720.KS`
- `HYUNDAI` -> `005380.KS`
- `LGENERGY` -> `373220.KS`

Live price data is fetched from Yahoo Finance chart URLs such as:

```text
https://query1.finance.yahoo.com/v8/finance/chart/005930.KS?range=5d&interval=1d
```

This is useful for local testing without an API key, but it should be replaced with an official provider such as KIS Developers before production use.

## AI Integration Point

Backend AI analysis is isolated behind `AiAnalysisClient`.
The current implementation is `MockAiAnalysisClient`, which returns generated mock analysis.
Later, replace it with an HTTP client that calls the real AI server while keeping the controller and service contracts stable.
