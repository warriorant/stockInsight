package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.market.StockCandleClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnExpression("!'${app.ai.chart-image.url:}'.isBlank()")
public class HttpChartImageClient implements ChartImageClient {

    private static final Logger log = LoggerFactory.getLogger(HttpChartImageClient.class);

    private final String chartImageUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final StockCandleClient stockCandleClient;

    public HttpChartImageClient(
            @Value("${app.ai.chart-image.url}") String chartImageUrl,
            @Value("${app.ai.chart-image.api-key:}") String apiKey,
            ObjectMapper objectMapper,
            StockCandleClient stockCandleClient
    ) {
        this.chartImageUrl = chartImageUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
        this.stockCandleClient = stockCandleClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<ChartImageResponse> createChartImages(ChartPatternAnalysisRequest request) {
        if (apiKey.isBlank()) {
            log.warn("AI chart image render API key is empty.");
            return List.of();
        }

        List<StockCandleResponse> candles = stockCandleClient.getDailyCandles(request.symbol(), request.targetDate(), 12);
        if (candles.isEmpty()) {
            log.warn("No OHLC candles available for chart image rendering. symbol={}", request.symbol());
            return List.of();
        }

        return renderWithCandles(request, candles);
    }

    @Override
    public List<ChartImageResponse> createChartImages(
            ChartPatternAnalysisRequest request,
            List<StockCandleResponse> candles
    ) {
        if (apiKey.isBlank()) {
            log.warn("AI chart image render API key is empty.");
            return List.of();
        }
        if (candles == null || candles.isEmpty()) {
            log.warn("No OHLC candles provided for chart image rendering. symbol={}", request.symbol());
            return List.of();
        }

        return renderWithCandles(request, candles);
    }

    private List<ChartImageResponse> renderWithCandles(
            ChartPatternAnalysisRequest request,
            List<StockCandleResponse> candles
    ) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(RenderRequest.from(request, candles));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(chartImageUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI chart image server returned status={}", response.statusCode());
                return List.of();
            }

            return parseImages(response.body(), response.headers().firstValue("Content-Type").orElse(""), request.symbol());
        } catch (Exception error) {
            log.warn("AI chart image server request failed.", error);
            return List.of();
        }
    }

    private List<ChartImageResponse> parseImages(byte[] responseBody, String contentType, String symbol) throws Exception {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return List.of(new ChartImageResponse("12M", responseBody, "%s-12M.png".formatted(symbol), contentType));
        }

        String jsonBody = new String(responseBody, StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(jsonBody);
        JsonNode images = imageArray(root);
        if (!images.isArray()) {
            return List.of(singleImage(root, symbol, "12M"));
        }

        List<ChartImageResponse> result = new ArrayList<>();
        for (JsonNode image : images) {
            ChartImageResponse parsed = singleImage(image, symbol, image.path("period").asText("12M"));
            if (parsed.imageBytes().length > 0) {
                result.add(parsed);
            }
        }
        return result;
    }

    private JsonNode imageArray(JsonNode root) {
        if (root.has("images")) {
            return root.path("images");
        }
        if (root.has("chartImages")) {
            return root.path("chartImages");
        }
        return root.path("results");
    }

    private ChartImageResponse singleImage(JsonNode node, String symbol, String period) {
        String imageUrl = node.path("imageUrl").asText("");
        if (!imageUrl.isBlank()) {
            return downloadImage(imageUrl, symbol, period);
        }

        String base64 = node.path("imageBase64").asText(node.path("base64").asText(""));
        String contentType = node.path("contentType").asText("image/png");
        String filename = node.path("filename").asText("%s-%s.png".formatted(symbol, period));
        byte[] imageBytes = decodeBase64(base64);
        return new ChartImageResponse(period, imageBytes, filename, contentType);
    }

    private ChartImageResponse downloadImage(String imageUrl, String symbol, String period) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI chart image download failed. status={}, period={}", response.statusCode(), period);
                return new ChartImageResponse(period, new byte[0], "%s-%s.png".formatted(symbol, period), "image/png");
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/png");
            return new ChartImageResponse(period, response.body(), filenameFromUrl(imageUrl, symbol, period), contentType);
        } catch (Exception error) {
            log.warn("AI chart image download failed. period={}", period, error);
            return new ChartImageResponse(period, new byte[0], "%s-%s.png".formatted(symbol, period), "image/png");
        }
    }

    private String filenameFromUrl(String imageUrl, String symbol, String period) {
        int slashIndex = imageUrl.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < imageUrl.length() - 1) {
            return imageUrl.substring(slashIndex + 1);
        }
        return "%s-%s.png".formatted(symbol, period);
    }

    private byte[] decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }

        String normalized = value;
        int commaIndex = normalized.indexOf(',');
        if (commaIndex >= 0) {
            normalized = normalized.substring(commaIndex + 1);
        }

        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException error) {
            log.warn("AI chart image server returned invalid base64 image.");
            return new byte[0];
        }
    }

    private record RenderRequest(
            String symbol,
            String name,
            String market,
            String asOf,
            List<Integer> periods,
            boolean adjusted,
            List<RenderCandle> candles
    ) {

        private static RenderRequest from(ChartPatternAnalysisRequest request, List<StockCandleResponse> candles) {
            return new RenderRequest(
                    request.symbol(),
                    request.name(),
                    request.market(),
                    request.targetDate().toString(),
                    List.of(6, 12),
                    true,
                    candles.stream().map(RenderCandle::from).toList()
            );
        }
    }

    private record RenderCandle(
            String timestamp,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume
    ) {

        private static RenderCandle from(StockCandleResponse candle) {
            return new RenderCandle(
                    candle.timestamp().toString(),
                    candle.openPrice().toPlainString(),
                    candle.highPrice().toPlainString(),
                    candle.lowPrice().toPlainString(),
                    candle.closePrice().toPlainString(),
                    String.valueOf(candle.volume())
            );
        }
    }
}
