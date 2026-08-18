package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.service.StockPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnExpression("!'${app.market-data.toss.client-id:}'.isBlank() && !'${app.market-data.toss.client-secret:}'.isBlank()")
public class TossStockCandleClient implements StockCandleClient {

    private static final Logger log = LoggerFactory.getLogger(TossStockCandleClient.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BEFORE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String candlesUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final StockPersistencePort stockPersistencePort;
    private volatile TokenState tokenState;

    public TossStockCandleClient(
            @Value("${app.market-data.toss.client-id}") String clientId,
            @Value("${app.market-data.toss.client-secret}") String clientSecret,
            @Value("${app.market-data.toss.token-url:https://openapi.tossinvest.com/oauth2/token}") String tokenUrl,
            @Value("${app.market-data.toss.candles-url:https://openapi.tossinvest.com/api/v1/candles}") String candlesUrl,
            ObjectMapper objectMapper,
            StockPersistencePort stockPersistencePort
    ) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.tokenUrl = tokenUrl.trim();
        this.candlesUrl = candlesUrl.trim();
        this.objectMapper = objectMapper;
        this.stockPersistencePort = stockPersistencePort;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<StockCandleResponse> getDailyCandles(String symbol, LocalDate asOf, int months) {
        Optional<String> accessToken = accessToken();
        if (accessToken.isEmpty()) {
            return List.of();
        }

        LocalDate endDate = asOf == null ? LocalDate.now(SEOUL) : asOf;
        LocalDate startDate = endDate.minusMonths(months);
        String before = endDate.atTime(23, 59, 59).atZone(SEOUL).toOffsetDateTime().format(BEFORE_FORMATTER);
        List<StockCandleResponse> candles = new ArrayList<>();

        for (int page = 0; page < 8; page += 1) {
            Optional<CandlePage> candlePage = fetchCandlePage(symbol, before, accessToken.get());
            if (candlePage.isEmpty()) {
                break;
            }

            candles.addAll(candlePage.get().candles().stream()
                    .filter(candle -> !candle.timestamp().toLocalDate().isBefore(startDate))
                    .filter(candle -> !candle.timestamp().toLocalDate().isAfter(endDate))
                    .toList());

            Optional<LocalDate> oldestDate = candlePage.get().candles().stream()
                    .map(candle -> candle.timestamp().toLocalDate())
                    .min(Comparator.naturalOrder());
            String nextBefore = candlePage.get().nextBefore();
            if (nextBefore == null || nextBefore.isBlank() || oldestDate.map(date -> !date.isAfter(startDate)).orElse(true)) {
                break;
            }
            before = nextBefore;
        }

        List<StockCandleResponse> result = candles.stream()
                .sorted(Comparator.comparing(StockCandleResponse::timestamp))
                .toList();
        stockPersistencePort.saveCandles(symbol, result, "TOSS", true);
        return result;
    }

    private Optional<String> accessToken() {
        TokenState current = tokenState;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return Optional.of(current.accessToken());
        }

        synchronized (this) {
            if (tokenState != null && tokenState.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return Optional.of(tokenState.accessToken());
            }
            tokenState = fetchAccessToken().orElse(null);
            return tokenState == null ? Optional.empty() : Optional.of(tokenState.accessToken());
        }
    }

    private Optional<TokenState> fetchAccessToken() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Toss token request failed. status={}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String accessToken = root.path("access_token").asText("");
            long expiresIn = root.path("expires_in").asLong(1800);
            if (accessToken.isBlank()) {
                log.warn("Toss token response did not include access_token.");
                return Optional.empty();
            }
            return Optional.of(new TokenState(accessToken, Instant.now().plusSeconds(Math.max(60, expiresIn))));
        } catch (Exception error) {
            log.warn("Toss token request failed.", error);
            return Optional.empty();
        }
    }

    private Optional<CandlePage> fetchCandlePage(String symbol, String before, String accessToken) {
        URI uri = URI.create(candlesUrl
                + "?symbol=" + encode(symbol)
                + "&interval=1d"
                + "&count=200"
                + "&before=" + encode(before)
                + "&adjusted=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Toss candles request failed. symbol={}, status={}", symbol, response.statusCode());
                return Optional.empty();
            }

            JsonNode result = objectMapper.readTree(response.body()).path("result");
            JsonNode candleNodes = result.path("candles");
            if (!candleNodes.isArray()) {
                return Optional.empty();
            }

            List<StockCandleResponse> candles = new ArrayList<>();
            for (JsonNode item : candleNodes) {
                candle(item).ifPresent(candles::add);
            }
            return Optional.of(new CandlePage(candles, result.path("nextBefore").asText("")));
        } catch (Exception error) {
            log.warn("Toss candles request failed. symbol={}", symbol, error);
            return Optional.empty();
        }
    }

    private Optional<StockCandleResponse> candle(JsonNode item) {
        Optional<OffsetDateTime> timestamp = timestamp(item);
        Optional<BigDecimal> openPrice = decimal(item.path("openPrice"));
        Optional<BigDecimal> highPrice = decimal(item.path("highPrice"));
        Optional<BigDecimal> lowPrice = decimal(item.path("lowPrice"));
        Optional<BigDecimal> closePrice = decimal(item.path("closePrice"));
        Long volume = decimal(item.path("volume")).map(BigDecimal::longValue).orElse(0L);

        if (timestamp.isEmpty() || openPrice.isEmpty() || highPrice.isEmpty() || lowPrice.isEmpty() || closePrice.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new StockCandleResponse(
                timestamp.get(),
                openPrice.get(),
                highPrice.get(),
                lowPrice.get(),
                closePrice.get(),
                volume
        ));
    }

    private Optional<OffsetDateTime> timestamp(JsonNode item) {
        String value = item.path("timestamp").asText(item.path("date").asText(""));
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value));
        } catch (Exception ignored) {
            try {
                return Optional.of(LocalDate.parse(value).atStartOfDay(SEOUL).toOffsetDateTime());
            } catch (Exception error) {
                return Optional.empty();
            }
        }
    }

    private Optional<BigDecimal> decimal(JsonNode node) {
        if (node.isNumber()) {
            return Optional.of(BigDecimal.valueOf(node.asDouble()));
        }
        if (!node.isTextual()) {
            return Optional.empty();
        }
        String value = node.asText().replace(",", "").trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record TokenState(String accessToken, Instant expiresAt) {
    }

    private record CandlePage(List<StockCandleResponse> candles, String nextBefore) {
    }
}
