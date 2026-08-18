package com.example.stockanalysis.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiServerPatternPredictionClient implements PatternPredictionClient {

    private static final Logger log = LoggerFactory.getLogger(AiServerPatternPredictionClient.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final String predictUrl;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiServerPatternPredictionClient(
            @Value("${app.ai.pattern-server.predict-url:}") String predictUrl,
            @Value("${app.ai.pattern-server.timeout-seconds:180}") int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.predictUrl = predictUrl == null ? "" : predictUrl.trim();
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, this.timeoutSeconds)))
                .build();
    }

    @Override
    public Optional<PatternPredictionResponse> predict(ChartImageResponse chartImage) {
        if (predictUrl.isBlank() || chartImage.imageBytes().length == 0) {
            return Optional.empty();
        }

        String boundary = "----stock-insight-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, chartImage);

        HttpRequest request = HttpRequest.newBuilder(URI.create(predictUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI pattern server returned status={}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String rawPattern = root.path("pattern").asText("");
            BigDecimal confidence = root.hasNonNull("confidence")
                    ? BigDecimal.valueOf(root.path("confidence").asDouble())
                    : BigDecimal.ZERO;
            Integer patternId = parsePatternId(rawPattern).orElse(null);
            return Optional.of(new PatternPredictionResponse(patternId, rawPattern, confidence));
        } catch (Exception error) {
            log.warn("AI pattern server request failed. period={}", chartImage.period(), error);
            return Optional.empty();
        }
    }

    private byte[] multipartBody(String boundary, ChartImageResponse chartImage) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + chartImage.filename() + "\"\r\n"
                + "Content-Type: " + chartImage.contentType() + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";

        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = chartImage.imageBytes();
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[headBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(headBytes, 0, result, 0, headBytes.length);
        System.arraycopy(fileBytes, 0, result, headBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, result, headBytes.length + fileBytes.length, tailBytes.length);
        return result;
    }

    private Optional<Integer> parsePatternId(String rawPattern) {
        Matcher matcher = NUMBER_PATTERN.matcher(rawPattern == null ? "" : rawPattern);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }
}
