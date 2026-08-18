package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.MarketEventResponse;
import com.example.stockanalysis.market.MarketEventClient;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketEventService {

    private static final Logger log = LoggerFactory.getLogger(MarketEventService.class);

    private final List<MarketEventClient> marketEventClients;
    private final int lookAheadDays;
    private final AtomicReference<List<MarketEventResponse>> cachedEvents = new AtomicReference<>(List.of());

    public MarketEventService(
            List<MarketEventClient> marketEventClients,
            @Value("${app.market-events.look-ahead-days:60}") int lookAheadDays
    ) {
        this.marketEventClients = marketEventClients;
        this.lookAheadDays = lookAheadDays;
    }

    @PostConstruct
    public void initializeEvents() {
        refreshEvents();
    }

    @Scheduled(fixedDelayString = "${app.market-events.refresh-ms:21600000}")
    public void refreshEvents() {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(lookAheadDays);
        List<MarketEventResponse> apiEvents = new ArrayList<>();

        for (MarketEventClient client : marketEventClients) {
            try {
                List<MarketEventResponse> events = client.getEvents(today, endDate);
                apiEvents.addAll(events);
                log.info("Market event source loaded. source={}, count={}", client.sourceName(), events.size());
            } catch (RuntimeException error) {
                log.warn("Market event source failed. source={}", client.sourceName(), error);
            }
        }

        List<MarketEventResponse> nextEvents = apiEvents.isEmpty() ? fallbackEvents(today) : apiEvents;

        cachedEvents.set(deduplicate(nextEvents).stream()
                .sorted(Comparator
                        .comparing(MarketEventResponse::scheduledDate)
                        .thenComparingInt(event -> importanceRank(event.importance()))
                        .thenComparing(MarketEventResponse::title))
                .limit(60)
                .toList());

        log.info("Market events refreshed. source={}, count={}", apiEvents.isEmpty() ? "fallback" : "api", nextEvents.size());
    }

    public List<MarketEventResponse> getUpcomingEvents() {
        return cachedEvents.get().stream()
                .sorted(Comparator
                        .comparing(MarketEventResponse::scheduledDate)
                        .thenComparingInt(event -> importanceRank(event.importance()))
                        .thenComparing(MarketEventResponse::title))
                .toList();
    }

    public List<MarketEventResponse> getEventsForStock(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);

        return cachedEvents.get().stream()
                .filter(event -> event.affectedSymbols().contains(normalizedSymbol) || event.affectedSymbols().contains("ALL"))
                .sorted(Comparator
                        .comparing(MarketEventResponse::scheduledDate)
                        .thenComparingInt(event -> importanceRank(event.importance()))
                        .thenComparing(MarketEventResponse::title))
                .toList();
    }

    private List<MarketEventResponse> deduplicate(List<MarketEventResponse> events) {
        Map<String, MarketEventResponse> uniqueEvents = new LinkedHashMap<>();
        for (MarketEventResponse event : events) {
            String key = "%s|%s".formatted(event.scheduledDate(), event.title().toLowerCase(Locale.ROOT));
            uniqueEvents.putIfAbsent(key, event);
        }
        return List.copyOf(uniqueEvents.values());
    }

    private int importanceRank(String importance) {
        return switch (importance) {
            case "높음" -> 0;
            case "보통" -> 1;
            case "낮음" -> 2;
            default -> 3;
        };
    }

    private List<MarketEventResponse> fallbackEvents(LocalDate today) {
        return List.of(
                new MarketEventResponse(
                        "fallback-api-setup",
                        "경제 일정 API 키 설정 필요",
                        "연동",
                        today.plusDays(1),
                        "보통",
                        "Trading Economics, FRED, FMP 키가 없거나 호출에 실패해 기본 안내 일정만 표시하고 있습니다.",
                        "실제 CPI, 고용, 실적 발표 일정을 보려면 백엔드 실행 환경에 API 키를 넣으면 됩니다.",
                        List.of("전체"),
                        List.of("ALL")
                ),
                new MarketEventResponse(
                        "fallback-rule-based",
                        "규칙 기반 일정은 키 없이도 자동 계산",
                        "수급",
                        today.plusDays(2),
                        "보통",
                        "네마녀의 날, 한국·미국 선물옵션 동시 만기일 같은 반복 일정은 외부 API 없이 백엔드에서 계산합니다.",
                        "실제 기업 실적이나 거시경제 발표처럼 날짜가 계속 바뀌는 일정은 외부 API가 필요합니다.",
                        List.of("전체"),
                        List.of("ALL")
                )
        );
    }
}
