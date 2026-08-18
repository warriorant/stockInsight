package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.service.StockDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class YahooFinanceStockMarketClient implements StockMarketClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceStockMarketClient.class);
    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final String NAVER_REALTIME_URL = "https://polling.finance.naver.com/api/realtime/domestic/stock";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YahooFinanceStockMarketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Optional<StockResponse> getStock(StockDefinition stock) {
        return fetchNaverRealtimeStock(stock)
                .or(() -> fetchChart(stock.externalSymbol(), "5d")
                        .flatMap(chart -> toStockResponse(stock, chart)));
    }

    @Override
    public Optional<List<PricePointResponse>> getPrices(StockDefinition stock, String range) {
        return fetchChart(stock.externalSymbol(), yahooRange(range))
                .map(this::toPricePoints)
                .filter(points -> !points.isEmpty());
    }

    private Optional<JsonNode> fetchChart(String externalSymbol, String range) {
        String encodedSymbol = URLEncoder.encode(externalSymbol, StandardCharsets.UTF_8);
        URI uri = URI.create("%s/%s?range=%s&interval=1d".formatted(BASE_URL, encodedSymbol, range));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Yahoo Finance returned status {} for {}", response.statusCode(), externalSymbol);
                return Optional.empty();
            }

            JsonNode result = objectMapper.readTree(response.body())
                    .path("chart")
                    .path("result")
                    .path(0);

            return result.isMissingNode() || result.isNull() ? Optional.empty() : Optional.of(result);
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Yahoo Finance request failed for {}", externalSymbol, error);
            return Optional.empty();
        }
    }

    private Optional<StockResponse> fetchNaverRealtimeStock(StockDefinition stock) {
        String naverCode = naverCode(stock.externalSymbol());
        if (naverCode.isBlank()) {
            return Optional.empty();
        }

        URI uri = URI.create("%s/%s".formatted(NAVER_REALTIME_URL, naverCode));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Naver Finance returned status {} for {}", response.statusCode(), naverCode);
                return Optional.empty();
            }

            JsonNode item = objectMapper.readTree(response.body()).path("datas").path(0);
            JsonNode overMarketPriceInfo = item.path("overMarketPriceInfo");
            Optional<BigDecimal> price = decimalText(overMarketPriceInfo.path("overPrice"))
                    .or(() -> decimalText(item.path("closePriceRaw")))
                    .or(() -> decimalText(item.path("closePrice")));
            if (price.isEmpty()) {
                return Optional.empty();
            }

            BigDecimal changeRate = decimalText(overMarketPriceInfo.path("fluctuationsRatio"))
                    .or(() -> decimalText(item.path("fluctuationsRatioRaw")))
                    .or(() -> decimalText(item.path("fluctuationsRatio")))
                    .orElse(stock.fallbackChangeRate());
            BigDecimal normalizedChangeRate = changeRate == null
                    ? null
                    : changeRate.setScale(2, RoundingMode.HALF_UP);

            return Optional.of(new StockResponse(
                    stock.symbol(),
                    stock.name(),
                    stock.market(),
                    stock.sector(),
                    stock.industry(),
                    price.get().setScale(0, RoundingMode.HALF_UP),
                    normalizedChangeRate,
                    stock.description()
            ));
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Naver Finance request failed for {}", naverCode, error);
            return Optional.empty();
        }
    }

    private Optional<StockResponse> toStockResponse(StockDefinition stock, JsonNode chart) {
        JsonNode meta = chart.path("meta");
        Optional<BigDecimal> price = decimal(meta.path("regularMarketPrice"));
        if (price.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal changeRate = decimal(meta.path("chartPreviousClose"))
                .filter(previousClose -> previousClose.compareTo(BigDecimal.ZERO) > 0)
                .map(previousClose -> price.get()
                        .subtract(previousClose)
                        .divide(previousClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP))
                .orElse(stock.fallbackChangeRate());

        return Optional.of(new StockResponse(
                stock.symbol(),
                stock.name(),
                stock.market(),
                stock.sector(),
                stock.industry(),
                price.get().setScale(0, RoundingMode.HALF_UP),
                changeRate,
                stock.description()
        ));
    }

    private List<PricePointResponse> toPricePoints(JsonNode chart) {
        JsonNode timestamps = chart.path("timestamp");
        JsonNode quote = chart.path("indicators").path("quote").path(0);
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");
        List<PricePointResponse> points = new ArrayList<>();

        for (int index = 0; index < timestamps.size(); index += 1) {
            JsonNode closeNode = closes.path(index);
            if (!closeNode.isNumber()) {
                continue;
            }

            long timestamp = timestamps.path(index).asLong();
            BigDecimal close = BigDecimal.valueOf(closeNode.asDouble()).setScale(0, RoundingMode.HALF_UP);
            long volume = volumes.path(index).isNumber() ? volumes.path(index).asLong() : 0L;
            points.add(new PricePointResponse(
                    Instant.ofEpochSecond(timestamp).atZone(SEOUL).toLocalDate(),
                    close,
                    volume
            ));
        }

        return points;
    }

    private Optional<BigDecimal> decimal(JsonNode node) {
        return node.isNumber() ? Optional.of(BigDecimal.valueOf(node.asDouble())) : Optional.empty();
    }

    private Optional<BigDecimal> decimalText(JsonNode node) {
        if (node.isNumber()) {
            return decimal(node);
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

    private String naverCode(String externalSymbol) {
        if (externalSymbol == null) {
            return "";
        }

        int marketSeparator = externalSymbol.indexOf('.');
        return marketSeparator > 0 ? externalSymbol.substring(0, marketSeparator) : externalSymbol;
    }

    private String yahooRange(String range) {
        return switch (range == null ? "3M" : range.toUpperCase(Locale.ROOT)) {
            case "1M" -> "1mo";
            case "6M" -> "6mo";
            case "1Y" -> "1y";
            case "3M" -> "3mo";
            default -> "3mo";
        };
    }
}
