package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.MarketEventResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
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
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FmpMarketEventClient implements MarketEventClient {

    private static final Logger log = LoggerFactory.getLogger(FmpMarketEventClient.class);
    private static final String BASE_URL = "https://financialmodelingprep.com/stable";
    private static final Map<String, List<String>> WATCH_SYMBOL_MAP = Map.ofEntries(
            Map.entry("005930", List.of("SAMSUNG")),
            Map.entry("005930.KS", List.of("SAMSUNG")),
            Map.entry("000660", List.of("SKHYNIX")),
            Map.entry("000660.KS", List.of("SKHYNIX")),
            Map.entry("035420", List.of("NAVER")),
            Map.entry("035420.KS", List.of("NAVER")),
            Map.entry("035720", List.of("KAKAO")),
            Map.entry("035720.KS", List.of("KAKAO")),
            Map.entry("005380", List.of("HYUNDAI")),
            Map.entry("005380.KS", List.of("HYUNDAI")),
            Map.entry("373220", List.of("LGENERGY")),
            Map.entry("373220.KS", List.of("LGENERGY")),
            Map.entry("NVDA", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("AMD", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("INTC", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("MU", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("TSM", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("ASML", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("AVGO", List.of("SAMSUNG", "SKHYNIX")),
            Map.entry("AAPL", List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO")),
            Map.entry("MSFT", List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO")),
            Map.entry("GOOGL", List.of("NAVER", "KAKAO")),
            Map.entry("META", List.of("NAVER", "KAKAO")),
            Map.entry("AMZN", List.of("NAVER", "KAKAO")),
            Map.entry("TSLA", List.of("HYUNDAI", "LGENERGY")),
            Map.entry("GM", List.of("HYUNDAI", "LGENERGY")),
            Map.entry("F", List.of("HYUNDAI", "LGENERGY")),
            Map.entry("TM", List.of("HYUNDAI"))
    );

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

        List<MarketEventResponse> events = new ArrayList<>();
        events.addAll(fetchEvents("earnings-calendar", CorporateEventType.EARNINGS, from, to));
        events.addAll(fetchEvents("dividends-calendar", CorporateEventType.DIVIDEND, from, to));
        events.addAll(fetchEvents("ipos-calendar", CorporateEventType.IPO, from, to));

        return events.stream()
                .sorted(Comparator
                        .comparing(MarketEventResponse::scheduledDate)
                        .thenComparing(MarketEventResponse::title))
                .limit(30)
                .toList();
    }

    private List<MarketEventResponse> fetchEvents(String endpoint, CorporateEventType type, LocalDate from, LocalDate to) {
        URI uri = UriComponentsBuilder.fromUriString("%s/%s".formatted(BASE_URL, endpoint))
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("apikey", apiKey)
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
                log.warn("FMP {} returned status {}", endpoint, response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                log.warn("FMP {} returned non-array payload", endpoint);
                return List.of();
            }

            List<MarketEventResponse> events = new ArrayList<>();
            for (JsonNode item : root) {
                toMarketEvent(item, type)
                        .filter(event -> !event.scheduledDate().isBefore(from) && !event.scheduledDate().isAfter(to))
                        .ifPresent(events::add);
            }
            return events.stream().limit(type == CorporateEventType.IPO ? 6 : 18).toList();
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("FMP {} request failed", endpoint, error);
            return List.of();
        }
    }

    private Optional<MarketEventResponse> toMarketEvent(JsonNode item, CorporateEventType type) {
        Optional<LocalDate> scheduledDate = eventDate(item);
        if (scheduledDate.isEmpty()) {
            return Optional.empty();
        }

        String symbol = text(item, "symbol").orElse("");
        String company = text(item, "companyName")
                .or(() -> text(item, "company"))
                .or(() -> text(item, "name"))
                .orElse(symbol);
        List<String> affectedSymbols = affectedSymbols(symbol, company, type);
        if (affectedSymbols.isEmpty()) {
            return Optional.empty();
        }

        String id = "fmp-%s-%s-%s".formatted(type.name().toLowerCase(Locale.ROOT), scheduledDate.get(), normalizeId(symbol, company));
        return Optional.of(new MarketEventResponse(
                id,
                title(type, company, symbol),
                type.category,
                scheduledDate.get(),
                importance(type),
                summary(type, item, company, symbol),
                beginnerImpact(type),
                relatedSectors(affectedSymbols, type),
                affectedSymbols
        ));
    }

    private List<String> affectedSymbols(String symbol, String company, CorporateEventType type) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        List<String> mappedSymbols = WATCH_SYMBOL_MAP.getOrDefault(normalizedSymbol, List.of());
        if (!mappedSymbols.isEmpty()) {
            return mappedSymbols;
        }

        String normalizedCompany = company == null ? "" : company.toLowerCase(Locale.ROOT);
        if (normalizedCompany.contains("nvidia") || normalizedCompany.contains("amd") || normalizedCompany.contains("micron")
                || normalizedCompany.contains("tsmc") || normalizedCompany.contains("asml")) {
            return List.of("SAMSUNG", "SKHYNIX");
        }
        if (normalizedCompany.contains("tesla") || normalizedCompany.contains("general motors") || normalizedCompany.contains("ford")) {
            return List.of("HYUNDAI", "LGENERGY");
        }
        if (normalizedCompany.contains("alphabet") || normalizedCompany.contains("meta") || normalizedCompany.contains("microsoft")
                || normalizedCompany.contains("amazon")) {
            return List.of("NAVER", "KAKAO");
        }

        return type == CorporateEventType.IPO ? List.of("ALL") : List.of();
    }

    private List<String> relatedSectors(List<String> affectedSymbols, CorporateEventType type) {
        if (type == CorporateEventType.IPO || affectedSymbols.contains("ALL")) {
            return List.of("전체", "시장심리");
        }

        List<String> sectors = new ArrayList<>();
        if (affectedSymbols.stream().anyMatch(symbol -> symbol.equals("SAMSUNG") || symbol.equals("SKHYNIX"))) {
            sectors.add("반도체");
        }
        if (affectedSymbols.stream().anyMatch(symbol -> symbol.equals("NAVER") || symbol.equals("KAKAO"))) {
            sectors.add("플랫폼");
        }
        if (affectedSymbols.stream().anyMatch(symbol -> symbol.equals("HYUNDAI") || symbol.equals("LGENERGY"))) {
            sectors.add("자동차·배터리");
        }
        return sectors.isEmpty() ? List.of("전체") : sectors;
    }

    private Optional<LocalDate> eventDate(JsonNode item) {
        return text(item, "date")
                .or(() -> text(item, "paymentDate"))
                .or(() -> text(item, "recordDate"))
                .or(() -> text(item, "fiscalDateEnding"))
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

    private String title(CorporateEventType type, String company, String symbol) {
        String displayName = company.isBlank() ? symbol : company;
        return switch (type) {
            case EARNINGS -> "%s 실적 발표".formatted(displayName);
            case DIVIDEND -> "%s 배당 일정".formatted(displayName);
            case IPO -> "%s IPO 일정".formatted(displayName);
        };
    }

    private String summary(CorporateEventType type, JsonNode item, String company, String symbol) {
        return switch (type) {
            case EARNINGS -> {
                String eps = text(item, "epsEstimated").or(() -> text(item, "eps")).orElse("");
                String revenue = text(item, "revenueEstimated").or(() -> text(item, "revenue")).orElse("");
                String detail = details("예상 EPS", eps, "예상 매출", revenue);
                yield "%s(%s)의 실적 발표 일정입니다.%s".formatted(company, symbol, detail);
            }
            case DIVIDEND -> {
                String dividend = text(item, "dividend").or(() -> text(item, "adjDividend")).orElse("");
                String detail = dividend.isBlank() ? "" : " 배당금은 %s로 공시되어 있습니다.".formatted(dividend);
                yield "%s(%s)의 배당 기준일 또는 지급 관련 일정입니다.%s".formatted(company, symbol, detail);
            }
            case IPO -> {
                String exchange = text(item, "exchange").orElse("");
                String priceRange = text(item, "priceRange").orElse("");
                String detail = details("거래소", exchange, "공모가 범위", priceRange);
                yield "새로 상장되는 기업 일정입니다.%s".formatted(detail);
            }
        };
    }

    private String details(String firstLabel, String firstValue, String secondLabel, String secondValue) {
        List<String> parts = new ArrayList<>();
        if (!firstValue.isBlank()) {
            parts.add("%s: %s".formatted(firstLabel, formatValue(firstValue)));
        }
        if (!secondValue.isBlank()) {
            parts.add("%s: %s".formatted(secondLabel, formatValue(secondValue)));
        }
        return parts.isEmpty() ? "" : " " + String.join(", ", parts) + ".";
    }

    private String formatValue(String value) {
        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException error) {
            return value;
        }
    }

    private String beginnerImpact(CorporateEventType type) {
        return switch (type) {
            case EARNINGS -> "같은 업종의 대표 기업 실적은 국내 관련 종목의 기대감에도 영향을 줄 수 있어요.";
            case DIVIDEND -> "배당 일정은 주주환원 흐름을 보는 참고 자료입니다. 단기 주가보다 현금흐름 성향을 같이 보세요.";
            case IPO -> "큰 IPO는 시장의 관심과 자금을 일부 흡수할 수 있어서 단기 수급에 영향을 줄 수 있어요.";
        };
    }

    private String importance(CorporateEventType type) {
        return switch (type) {
            case EARNINGS -> "높음";
            case DIVIDEND, IPO -> "보통";
        };
    }

    private String normalizeId(String symbol, String company) {
        String raw = symbol.isBlank() ? company : symbol;
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]+", "-").replaceAll("(^-|-$)", "");
    }

    private enum CorporateEventType {
        EARNINGS("실적"),
        DIVIDEND("배당"),
        IPO("IPO");

        private final String category;

        CorporateEventType(String category) {
            this.category = category;
        }
    }
}
