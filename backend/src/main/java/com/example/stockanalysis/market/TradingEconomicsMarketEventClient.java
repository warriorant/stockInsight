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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class TradingEconomicsMarketEventClient implements MarketEventClient {

    private static final Logger log = LoggerFactory.getLogger(TradingEconomicsMarketEventClient.class);
    private static final String BASE_URL = "https://api.tradingeconomics.com/calendar/country/All";
    private static final Set<String> WATCH_COUNTRIES = Set.of(
            "UNITED STATES",
            "SOUTH KOREA",
            "KOREA",
            "CHINA",
            "JAPAN",
            "EURO AREA"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public TradingEconomicsMarketEventClient(
            ObjectMapper objectMapper,
            @Value("${app.market-events.trading-economics.api-key:}") String apiKey
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

        URI uri = UriComponentsBuilder.fromUriString("%s/%s/%s".formatted(BASE_URL, from, to))
                .queryParam("c", apiKey)
                .queryParam("f", "json")
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Trading Economics calendar returned status {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                log.warn("Trading Economics calendar returned non-array payload");
                return List.of();
            }

            List<MarketEventResponse> events = new ArrayList<>();
            for (JsonNode item : root) {
                toMarketEvent(item)
                        .filter(event -> !event.scheduledDate().isBefore(from) && !event.scheduledDate().isAfter(to))
                        .ifPresent(events::add);
            }

            return events.stream()
                    .sorted(Comparator
                            .comparing(MarketEventResponse::scheduledDate)
                            .thenComparing(MarketEventResponse::title))
                    .limit(30)
                    .toList();
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Trading Economics calendar request failed", error);
            return List.of();
        }
    }

    private Optional<MarketEventResponse> toMarketEvent(JsonNode item) {
        String country = text(item, "Country").or(() -> text(item, "country")).orElse("");
        if (!isWatchCountry(country)) {
            return Optional.empty();
        }

        String eventName = text(item, "Event").or(() -> text(item, "event")).orElse("");
        String category = text(item, "Category").or(() -> text(item, "category")).orElse("");
        Optional<LocalDate> scheduledDate = eventDate(item);
        EventKind kind = classify("%s %s".formatted(eventName, category));

        if (eventName.isBlank() || scheduledDate.isEmpty() || kind == EventKind.OTHER) {
            return Optional.empty();
        }

        int rawImportance = intValue(item, "Importance").or(() -> intValue(item, "importance")).orElse(0);
        if (rawImportance < 2 && !kind.alwaysKeep) {
            return Optional.empty();
        }

        String id = "te-%s-%s-%s".formatted(
                scheduledDate.get(),
                country.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]+", "-"),
                eventName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]+", "-")
        ).replaceAll("(^-|-$)", "");

        return Optional.of(new MarketEventResponse(
                id,
                localizedTitle(country, eventName, kind),
                kind.category,
                scheduledDate.get(),
                importance(rawImportance, kind),
                summary(item, country, eventName, kind),
                kind.beginnerImpact,
                kind.relatedSectors,
                kind.affectedSymbols
        ));
    }

    private Optional<LocalDate> eventDate(JsonNode item) {
        return text(item, "Date")
                .or(() -> text(item, "date"))
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

    private Optional<Integer> intValue(JsonNode item, String fieldName) {
        JsonNode value = item.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (value.isInt()) {
            return Optional.of(value.asInt());
        }
        try {
            return Optional.of(Integer.parseInt(value.asText("").trim()));
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
    }

    private boolean isWatchCountry(String country) {
        String normalized = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        return WATCH_COUNTRIES.contains(normalized);
    }

    private EventKind classify(String rawText) {
        String text = rawText.toLowerCase(Locale.ROOT);
        if (text.contains("cpi") || text.contains("inflation") || text.contains("consumer price")) {
            return EventKind.INFLATION;
        }
        if (text.contains("ppi") || text.contains("producer price")) {
            return EventKind.PRODUCER_PRICE;
        }
        if (text.contains("interest rate") || text.contains("rate decision") || text.contains("fomc")
                || text.contains("fed") || text.contains("boj") || text.contains("bok")) {
            return EventKind.RATE;
        }
        if (text.contains("payroll") || text.contains("employment") || text.contains("unemployment")
                || text.contains("jobless")) {
            return EventKind.JOBS;
        }
        if (text.contains("gdp")) {
            return EventKind.GDP;
        }
        if (text.contains("retail sales")) {
            return EventKind.RETAIL;
        }
        if (text.contains("pmi") || text.contains("manufacturing")) {
            return EventKind.PMI;
        }
        return EventKind.OTHER;
    }

    private String localizedTitle(String country, String eventName, EventKind kind) {
        String prefix = countryPrefix(country);
        return switch (kind) {
            case INFLATION -> "%sCPI·물가 발표".formatted(prefix);
            case PRODUCER_PRICE -> "%sPPI·생산자물가 발표".formatted(prefix);
            case RATE -> "%s금리 결정·중앙은행 발표".formatted(prefix);
            case JOBS -> "%s고용 지표 발표".formatted(prefix);
            case GDP -> "%sGDP 성장률 발표".formatted(prefix);
            case RETAIL -> "%s소매판매 발표".formatted(prefix);
            case PMI -> "%sPMI·제조업 지표 발표".formatted(prefix);
            case OTHER -> prefix + eventName;
        };
    }

    private String countryPrefix(String country) {
        String normalized = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "UNITED STATES" -> "미국 ";
            case "SOUTH KOREA", "KOREA" -> "한국 ";
            case "CHINA" -> "중국 ";
            case "JAPAN" -> "일본 ";
            case "EURO AREA" -> "유로존 ";
            default -> country == null || country.isBlank() ? "" : country.trim() + " ";
        };
    }

    private String importance(int rawImportance, EventKind kind) {
        if (rawImportance >= 3 || kind == EventKind.RATE || kind == EventKind.INFLATION || kind == EventKind.JOBS) {
            return "높음";
        }
        if (rawImportance <= 1) {
            return "낮음";
        }
        return "보통";
    }

    private String summary(JsonNode item, String country, String eventName, EventKind kind) {
        String actual = text(item, "Actual").or(() -> text(item, "actual")).orElse("");
        String forecast = text(item, "Forecast").or(() -> text(item, "forecast")).orElse("");
        String previous = text(item, "Previous").or(() -> text(item, "previous")).orElse("");
        List<String> details = new ArrayList<>();
        if (!forecast.isBlank()) {
            details.add("예상 " + forecast);
        }
        if (!previous.isBlank()) {
            details.add("이전 " + previous);
        }
        if (!actual.isBlank()) {
            details.add("실제 " + actual);
        }

        String detailText = details.isEmpty() ? "" : " 확인값: %s.".formatted(String.join(", ", details));
        return "%s의 %s 일정입니다.%s %s".formatted(countryPrefix(country).trim(), eventName, detailText, kind.summary);
    }

    private enum EventKind {
        INFLATION(
                "물가",
                "물가가 예상보다 높으면 금리 인하 기대가 약해질 수 있습니다.",
                "물가가 높게 나오면 성장주와 반도체처럼 미래 기대가 큰 종목은 금리 전망 변화에 민감하게 움직일 수 있어요.",
                List.of("반도체", "플랫폼", "경기소비재"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI"),
                true
        ),
        PRODUCER_PRICE(
                "물가",
                "기업의 생산 비용 흐름을 보여주는 지표입니다.",
                "생산 비용이 오르면 기업 이익률이 낮아질 수 있어서 제조업과 수출주가 민감하게 반응할 수 있어요.",
                List.of("반도체", "자동차·배터리", "산업재"),
                List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY"),
                false
        ),
        RATE(
                "금리",
                "기준금리와 향후 금리 방향은 전세계 증시에 영향을 줍니다.",
                "금리가 내려갈 것 같으면 주식시장에는 대체로 우호적이고, 오래 높게 유지될 것 같으면 부담이 될 수 있어요.",
                List.of("전체", "성장주"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY"),
                true
        ),
        JOBS(
                "고용",
                "고용 지표는 경기 강도와 금리 전망을 같이 움직이는 주요 지표입니다.",
                "고용이 너무 강하면 금리 인하가 늦어질 수 있고, 너무 약하면 경기 둔화 우려가 커질 수 있어요.",
                List.of("전체", "미국시장"),
                List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI"),
                true
        ),
        GDP(
                "경기",
                "경제 전체가 얼마나 성장했는지 보여주는 대표 지표입니다.",
                "성장률이 예상보다 낮으면 기업 실적 기대가 약해질 수 있고, 좋게 나오면 경기민감주에 힘이 실릴 수 있어요.",
                List.of("경기소비재", "산업재", "반도체"),
                List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY"),
                false
        ),
        RETAIL(
                "소비",
                "소비자가 실제로 돈을 얼마나 쓰고 있는지 보여주는 지표입니다.",
                "소비가 좋으면 플랫폼과 경기소비재에는 긍정적일 수 있지만, 물가 압력으로 해석되면 금리 부담도 생길 수 있어요.",
                List.of("플랫폼", "경기소비재"),
                List.of("NAVER", "KAKAO", "HYUNDAI"),
                false
        ),
        PMI(
                "경기",
                "기업 구매 담당자들의 체감 경기를 보여주는 선행 지표입니다.",
                "제조업 심리가 좋아지면 반도체와 자동차처럼 경기 흐름을 타는 업종 기대가 커질 수 있어요.",
                List.of("반도체", "자동차·배터리", "산업재"),
                List.of("SAMSUNG", "SKHYNIX", "HYUNDAI", "LGENERGY"),
                false
        ),
        OTHER(
                "시장",
                "",
                "",
                List.of("전체"),
                List.of("ALL"),
                false
        );

        private final String category;
        private final String summary;
        private final String beginnerImpact;
        private final List<String> relatedSectors;
        private final List<String> affectedSymbols;
        private final boolean alwaysKeep;

        EventKind(
                String category,
                String summary,
                String beginnerImpact,
                List<String> relatedSectors,
                List<String> affectedSymbols,
                boolean alwaysKeep
        ) {
            this.category = category;
            this.summary = summary;
            this.beginnerImpact = beginnerImpact;
            this.relatedSectors = relatedSectors;
            this.affectedSymbols = affectedSymbols;
            this.alwaysKeep = alwaysKeep;
        }
    }
}
