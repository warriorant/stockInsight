package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.MarketEventResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FmpMarketEventClient implements MarketEventClient {

    private static final Logger log = LoggerFactory.getLogger(FmpMarketEventClient.class);
    private static final String BASE_URL = "https://financialmodelingprep.com/stable/economic-calendar";
    private static final List<String> WATCH_COUNTRIES = List.of("US", "USA", "UNITED STATES", "KR", "KOR", "SOUTH KOREA", "KOREA");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FmpMarketEventClient(
            ObjectMapper objectMapper,
            @Value("${app.market-events.fmp.api-key:}") String apiKey
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

        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("apikey", URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
                .build(true)
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
                log.warn("FMP economic calendar returned status {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                log.warn("FMP economic calendar returned non-array payload");
                return List.of();
            }

            List<MarketEventResponse> events = new ArrayList<>();
            for (JsonNode item : root) {
                toMarketEvent(item)
                        .filter(event -> !event.scheduledDate().isBefore(from) && !event.scheduledDate().isAfter(to))
                        .filter(this::isRelevant)
                        .ifPresent(events::add);
            }

            return events.stream()
                    .sorted(Comparator
                            .comparing(MarketEventResponse::scheduledDate)
                            .thenComparing(MarketEventResponse::importance)
                            .thenComparing(MarketEventResponse::title))
                    .limit(24)
                    .toList();
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("FMP economic calendar request failed", error);
            return List.of();
        }
    }

    private Optional<MarketEventResponse> toMarketEvent(JsonNode item) {
        String rawTitle = text(item, "event")
                .or(() -> text(item, "title"))
                .or(() -> text(item, "name"))
                .orElse("");
        Optional<LocalDate> scheduledDate = eventDate(item);
        if (rawTitle.isBlank() || scheduledDate.isEmpty()) {
            return Optional.empty();
        }

        String country = text(item, "country").orElse("");
        EventKind eventKind = classify(rawTitle);
        String title = localizedTitle(country, rawTitle, eventKind);
        String id = "fmp-%s-%s".formatted(
                scheduledDate.get(),
                rawTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]+", "-").replaceAll("(^-|-$)", "")
        );

        return Optional.of(new MarketEventResponse(
                id,
                title,
                eventKind.category,
                scheduledDate.get(),
                importance(item),
                eventKind.summary,
                eventKind.beginnerImpact,
                eventKind.relatedSectors,
                eventKind.affectedSymbols
        ));
    }

    private Optional<LocalDate> eventDate(JsonNode item) {
        return text(item, "date")
                .or(() -> text(item, "releaseDate"))
                .or(() -> text(item, "time"))
                .flatMap(value -> {
                    String datePart = value.length() >= 10 ? value.substring(0, 10) : value;
                    try {
                        return Optional.of(LocalDate.parse(datePart));
                    } catch (DateTimeParseException error) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<String> text(JsonNode item, String fieldName) {
        JsonNode value = item.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }

        String text = value.asText("").trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private String importance(JsonNode item) {
        String rawImpact = text(item, "impact")
                .or(() -> text(item, "importance"))
                .orElse("")
                .toLowerCase(Locale.ROOT);

        if (rawImpact.contains("high") || rawImpact.contains("3") || rawImpact.contains("높")) {
            return "높음";
        }
        if (rawImpact.contains("low") || rawImpact.contains("1") || rawImpact.contains("낮")) {
            return "낮음";
        }
        return "보통";
    }

    private boolean isRelevant(MarketEventResponse event) {
        if ("높음".equals(event.importance())) {
            return true;
        }

        String title = event.title().toLowerCase(Locale.ROOT);
        return title.contains("cpi")
                || title.contains("물가")
                || title.contains("금리")
                || title.contains("fomc")
                || title.contains("gdp")
                || title.contains("고용")
                || title.contains("소매")
                || title.contains("ppi")
                || title.contains("실업");
    }

    private EventKind classify(String rawTitle) {
        String title = rawTitle.toLowerCase(Locale.ROOT);

        if (title.contains("cpi") || title.contains("inflation") || title.contains("consumer price")) {
            return EventKind.INFLATION;
        }
        if (title.contains("ppi") || title.contains("producer price")) {
            return EventKind.PRODUCER_PRICE;
        }
        if (title.contains("fomc") || title.contains("fed") || title.contains("interest rate") || title.contains("rate decision")) {
            return EventKind.RATE;
        }
        if (title.contains("payroll") || title.contains("employment") || title.contains("unemployment") || title.contains("jobless")) {
            return EventKind.JOBS;
        }
        if (title.contains("gdp")) {
            return EventKind.GDP;
        }
        if (title.contains("retail sales")) {
            return EventKind.RETAIL;
        }

        return EventKind.MARKET;
    }

    private String localizedTitle(String country, String rawTitle, EventKind eventKind) {
        String prefix = countryPrefix(country);
        return switch (eventKind) {
            case INFLATION -> "%s소비자물가지수(CPI) 발표".formatted(prefix);
            case PRODUCER_PRICE -> "%s생산자물가지수(PPI) 발표".formatted(prefix);
            case RATE -> "%s금리 결정 발표".formatted(prefix);
            case JOBS -> "%s고용 지표 발표".formatted(prefix);
            case GDP -> "%sGDP 성장률 발표".formatted(prefix);
            case RETAIL -> "%s소매판매 발표".formatted(prefix);
            case MARKET -> prefix.isBlank() ? rawTitle : "%s%s".formatted(prefix, rawTitle);
        };
    }

    private String countryPrefix(String country) {
        String normalized = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("US") || normalized.equals("USA") || normalized.equals("UNITED STATES")) {
            return "미국 ";
        }
        if (normalized.equals("KR") || normalized.equals("KOR") || normalized.equals("SOUTH KOREA") || normalized.equals("KOREA")) {
            return "한국 ";
        }
        return country == null || country.isBlank() ? "" : "%s ".formatted(country.trim());
    }

    private enum EventKind {
        INFLATION(
                "물가",
                "물가가 예상보다 높으면 금리 인하 기대가 약해질 수 있습니다.",
                "성장주와 반도체처럼 미래 기대가 큰 종목은 금리 전망 변화에 더 민감하게 움직일 수 있어요.",
                List.of("기술", "커뮤니케이션", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI")
        ),
        PRODUCER_PRICE(
                "물가",
                "기업이 제품을 만들 때 드는 비용 흐름을 확인할 수 있는 지표입니다.",
                "생산 비용이 오르면 기업 이익률이 눌릴 수 있어서 제조업과 수출주가 민감하게 반응할 수 있어요.",
                List.of("기술", "산업재", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY")
        ),
        RATE(
                "금리",
                "기준금리와 향후 금리 방향에 대한 발언은 전 세계 증시에 영향을 줍니다.",
                "금리가 내려갈 것 같으면 주식시장에는 대체로 우호적이고, 금리가 오래 높게 유지될 것 같으면 부담이 될 수 있어요.",
                List.of("기술", "커뮤니케이션", "산업재", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY")
        ),
        JOBS(
                "고용",
                "고용 지표는 경기 강도와 금리 전망을 함께 흔드는 주요 지표입니다.",
                "고용이 너무 강하면 금리 인하가 늦어질 수 있고, 너무 약하면 경기 둔화 걱정이 커질 수 있어요.",
                List.of("기술", "커뮤니케이션", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI")
        ),
        GDP(
                "경기",
                "경제 전체가 얼마나 성장했는지 보여주는 대표 지표입니다.",
                "성장률이 둔화되면 기업 실적 기대가 낮아질 수 있고, 예상보다 좋으면 경기민감주에 힘이 실릴 수 있어요.",
                List.of("경기소비재", "산업재", "기술"),
                List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY")
        ),
        RETAIL(
                "소비",
                "소비자가 실제로 돈을 얼마나 쓰고 있는지 보여주는 지표입니다.",
                "소비가 좋으면 경기소비재와 플랫폼 기업에 긍정적일 수 있지만, 물가 압력으로 해석되면 금리 부담도 생길 수 있어요.",
                List.of("경기소비재", "커뮤니케이션"),
                List.of("NAVER", "KAKAO", "HYUNDAI")
        ),
        MARKET(
                "시장",
                "시장 전체 투자심리에 영향을 줄 수 있는 경제 일정입니다.",
                "이벤트 당일에는 예상치와 실제 발표치 차이 때문에 장중 변동성이 커질 수 있어요.",
                List.of("기술", "커뮤니케이션", "산업재", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY")
        );

        private final String category;
        private final String summary;
        private final String beginnerImpact;
        private final List<String> relatedSectors;
        private final List<String> affectedSymbols;

        EventKind(
                String category,
                String summary,
                String beginnerImpact,
                List<String> relatedSectors,
                List<String> affectedSymbols
        ) {
            this.category = category;
            this.summary = summary;
            this.beginnerImpact = beginnerImpact;
            this.relatedSectors = relatedSectors;
            this.affectedSymbols = affectedSymbols;
        }
    }
}
