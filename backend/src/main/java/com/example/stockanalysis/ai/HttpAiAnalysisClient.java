package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnExpression("!'${app.ai.analysis-server.url:}'.isBlank()")
public class HttpAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiAnalysisClient.class);

    private final String analysisUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpAiAnalysisClient(
            @Value("${app.ai.analysis-server.url}") String analysisUrl,
            ObjectMapper objectMapper
    ) {
        this.analysisUrl = analysisUrl.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(analysisUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI analysis server returned status={}", response.statusCode());
                return unavailableResponse();
            }

            return objectMapper.readValue(response.body(), AiAnalysisResponse.class);
        } catch (Exception error) {
            log.warn("AI analysis server request failed.", error);
            return unavailableResponse();
        }
    }

    private AiAnalysisResponse unavailableResponse() {
        return new AiAnalysisResponse(
                "AI 종합 분석 서버 응답을 받지 못했습니다.",
                "요청은 실제 AI 서버로 전송하도록 구성되어 있지만 현재 응답이 실패했습니다.",
                "서버 URL, 요청/응답 DTO, 네트워크 상태를 확인해야 합니다.",
                "실패 시 mock 분석으로 대체하지 않습니다."
        );
    }
}
