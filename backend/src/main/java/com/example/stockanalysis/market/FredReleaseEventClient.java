package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.MarketEventResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FredReleaseEventClient implements MarketEventClient {

    private static final Logger log = LoggerFactory.getLogger(FredReleaseEventClient.class);
    private static final String RELEASE_DATES_URL = "https://api.stlouisfed.org/fred/release/dates";
    private static final List<String> ALL_SYMBOLS = List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY");
    private static final List<ReleaseDefinition> RELEASES = List.of(
            new ReleaseDefinition(10, "미국 CPI 발표", "물가", "미국 소비자물가지수 발표일입니다.", "물가가 예상보다 높으면 금리 인하 기대가 약해져 성장주와 반도체가 민감하게 움직일 수 있어요.", List.of("반도체", "플랫폼", "경기소비재"), ALL_SYMBOLS, "높음"),
            new ReleaseDefinition(46, "미국 PPI 발표", "물가", "미국 생산자물가지수 발표일입니다.", "기업 생산비 흐름을 보는 지표라 제조업과 수출주 기대에 영향을 줄 수 있어요.", List.of("반도체", "자동차·배터리", "산업재"), List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY"), "보통"),
            new ReleaseDefinition(50, "미국 고용보고서 발표", "고용", "미국 고용 상황을 보여주는 대표 발표일입니다.", "고용이 너무 강하면 금리 부담, 너무 약하면 경기 둔화 우려로 해석될 수 있어요.", List.of("전체", "미국시장"), ALL_SYMBOLS, "높음"),
            new ReleaseDefinition(53, "미국 GDP 발표", "경기", "미국 경제 성장률을 확인하는 발표일입니다.", "성장률은 기업 실적 기대와 경기민감주 흐름에 영향을 줄 수 있어요.", List.of("경기소비재", "산업재", "반도체"), List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY"), "보통")
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FredReleaseEventClient(
            ObjectMapper objectMapper,
            @Value("${app.market-events.fred.api-key:}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public List<MarketEventResponse> getEvents(LocalDate from, LocalDate to) {
        if (apiKey.isBlank()) {
            return List.of();
        }

        List<MarketEventResponse> events = new ArrayList<>();
        for (ReleaseDefinition release : RELEASES) {
            events.addAll(fetchReleaseDates(release, from, to));
        }

        return events.stream()
                .sorted(Comparator
                        .comparing(MarketEventResponse::scheduledDate)
                        .thenComparing(MarketEventResponse::title))
                .limit(24)
                .toList();
    }

    private List<MarketEventResponse> fetchReleaseDates(ReleaseDefinition release, LocalDate from, LocalDate to) {
        URI uri = UriComponentsBuilder.fromUriString(RELEASE_DATES_URL)
                .queryParam("release_id", release.releaseId)
                .queryParam("realtime_start", from)
                .queryParam("realtime_end", to)
                .queryParam("include_release_dates_with_no_data", true)
                .queryParam("file_type", "json")
                .queryParam("api_key", apiKey)
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("FRED release {} returned status {}", release.releaseId, response.statusCode());
                return List.of();
            }

            JsonNode releaseDates = objectMapper.readTree(response.body()).path("release_dates");
            if (!releaseDates.isArray()) {
                return List.of();
            }

            List<MarketEventResponse> events = new ArrayList<>();
            for (JsonNode item : releaseDates) {
                eventDate(item)
                        .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                        .map(date -> toMarketEvent(release, date))
                        .ifPresent(events::add);
            }
            return events;
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("FRED release {} request failed", release.releaseId, error);
            return List.of();
        }
    }

    private Optional<LocalDate> eventDate(JsonNode item) {
        JsonNode value = item.path("date");
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.asText("")));
        } catch (DateTimeParseException error) {
            return Optional.empty();
        }
    }

    private MarketEventResponse toMarketEvent(ReleaseDefinition release, LocalDate date) {
        return new MarketEventResponse(
                "fred-%s-%s".formatted(release.releaseId, date),
                release.title,
                release.category,
                date,
                release.importance,
                release.summary,
                release.beginnerImpact,
                release.relatedSectors,
                release.affectedSymbols
        );
    }

    private record ReleaseDefinition(
            int releaseId,
            String title,
            String category,
            String summary,
            String beginnerImpact,
            List<String> relatedSectors,
            List<String> affectedSymbols,
            String importance
    ) {
    }
}
