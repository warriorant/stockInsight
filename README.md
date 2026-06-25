# Stock Analysis Web App

React + Vite frontend and Spring Boot backend starter for a stock analysis service.
The app currently uses mock stock, price, financial, and AI analysis data so it can run before external stock APIs or an AI server are ready.

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
By default, it starts in mock mode without requiring PostgreSQL.

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

## AI Integration Point

Backend AI analysis is isolated behind `AiAnalysisClient`.
The current implementation is `MockAiAnalysisClient`, which returns generated mock analysis.
Later, replace it with an HTTP client that calls the real AI server while keeping the controller and service contracts stable.
