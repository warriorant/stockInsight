package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.dto.StockCandleSyncStatusResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.market.StockCandleClient;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StockCandleSyncService {

    private static final Logger log = LoggerFactory.getLogger(StockCandleSyncService.class);
    private static final int DEFAULT_MONTHS = 12;

    private final StockService stockService;
    private final StockCandleClient stockCandleClient;
    private final boolean scheduledEnabled;
    private final String cron;
    private final long delayMs;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile SyncState currentState = SyncState.idle(DEFAULT_MONTHS);

    public StockCandleSyncService(
            StockService stockService,
            StockCandleClient stockCandleClient,
            @Value("${app.candle-sync.enabled:false}") boolean scheduledEnabled,
            @Value("${app.candle-sync.cron:0 30 21 * * MON-FRI}") String cron,
            @Value("${app.candle-sync.delay-ms:500}") long delayMs
    ) {
        this.stockService = stockService;
        this.stockCandleClient = stockCandleClient;
        this.scheduledEnabled = scheduledEnabled;
        this.cron = cron;
        this.delayMs = Math.max(0, delayMs);
        this.executorService = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "stock-candle-kospi-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(cron = "${app.candle-sync.cron:0 30 21 * * MON-FRI}", zone = "Asia/Seoul")
    public void runScheduledKospiSync() {
        if (!scheduledEnabled) {
            return;
        }
        startKospiSync("scheduled", null, DEFAULT_MONTHS);
    }

    public StockCandleSyncStatusResponse startKospiSync(String trigger, Integer limit, Integer months) {
        if (!running.compareAndSet(false, true)) {
            return getStatus();
        }

        int normalizedMonths = normalizeMonths(months);
        List<StockResponse> stocks = limitedStocks(limit);
        SyncState state = SyncState.started(trigger, stocks.size(), normalizedMonths);
        currentState = state;
        executorService.submit(() -> runSync(state, stocks, normalizedMonths));
        return getStatus();
    }

    public StockCandleSyncStatusResponse getStatus() {
        return currentState.toResponse(running.get());
    }

    private void runSync(SyncState state, List<StockResponse> stocks, int months) {
        LocalDate asOf = LocalDate.now();
        log.info("KOSPI candle sync started. runId={}, total={}, months={}", state.runId(), stocks.size(), months);
        try {
            for (StockResponse stock : stocks) {
                state.markCurrent(stock.symbol(), stock.name());
                try {
                    List<StockCandleResponse> candles = stockCandleClient.getDailyCandles(stock.symbol(), asOf, months);
                    state.markSuccess(stock.symbol(), candles.size());
                } catch (Exception error) {
                    state.markFailure(stock.symbol(), rootMessage(error));
                    log.warn("KOSPI candle sync item failed. symbol={}, name={}", stock.symbol(), stock.name(), error);
                }
                pauseBetweenRequests();
            }
        } finally {
            state.finish();
            running.set(false);
            log.info(
                    "KOSPI candle sync finished. runId={}, success={}, failure={}, savedCandles={}",
                    state.runId(),
                    state.successCount(),
                    state.failureCount(),
                    state.savedCandleCount()
            );
        }
    }

    private List<StockResponse> limitedStocks(Integer limit) {
        List<StockResponse> stocks = stockService.getStocks();
        if (limit == null || limit <= 0 || limit >= stocks.size()) {
            return stocks;
        }
        return stocks.subList(0, limit);
    }

    private int normalizeMonths(Integer months) {
        if (months == null || months <= 0) {
            return DEFAULT_MONTHS;
        }
        return Math.min(months, 60);
    }

    private void pauseBetweenRequests() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private static final class SyncState {

        private final String runId;
        private final String trigger;
        private final int months;
        private final LocalDateTime startedAt;
        private final int totalCount;
        private LocalDateTime finishedAt;
        private int completedCount;
        private int successCount;
        private int failureCount;
        private int savedCandleCount;
        private String currentSymbol;
        private String currentName;
        private String lastSuccessSymbol;
        private String lastErrorSymbol;
        private String lastErrorMessage;

        private SyncState(String runId, String trigger, int months, LocalDateTime startedAt, int totalCount) {
            this.runId = runId;
            this.trigger = trigger;
            this.months = months;
            this.startedAt = startedAt;
            this.totalCount = totalCount;
        }

        static SyncState idle(int months) {
            return new SyncState(null, "idle", months, null, 0);
        }

        static SyncState started(String trigger, int totalCount, int months) {
            return new SyncState(UUID.randomUUID().toString(), trigger, months, LocalDateTime.now(), totalCount);
        }

        synchronized void markCurrent(String symbol, String name) {
            this.currentSymbol = symbol;
            this.currentName = name;
        }

        synchronized void markSuccess(String symbol, int candleCount) {
            this.completedCount += 1;
            this.successCount += 1;
            this.savedCandleCount += candleCount;
            this.lastSuccessSymbol = symbol;
            this.lastErrorMessage = null;
        }

        synchronized void markFailure(String symbol, String message) {
            this.completedCount += 1;
            this.failureCount += 1;
            this.lastErrorSymbol = symbol;
            this.lastErrorMessage = message;
        }

        synchronized void finish() {
            this.finishedAt = LocalDateTime.now();
            this.currentSymbol = null;
            this.currentName = null;
        }

        synchronized StockCandleSyncStatusResponse toResponse(boolean running) {
            return new StockCandleSyncStatusResponse(
                    runId,
                    trigger,
                    running,
                    months,
                    startedAt,
                    finishedAt,
                    totalCount,
                    completedCount,
                    successCount,
                    failureCount,
                    savedCandleCount,
                    currentSymbol,
                    currentName,
                    lastSuccessSymbol,
                    lastErrorSymbol,
                    lastErrorMessage
            );
        }

        String runId() {
            return runId;
        }

        synchronized int successCount() {
            return successCount;
        }

        synchronized int failureCount() {
            return failureCount;
        }

        synchronized int savedCandleCount() {
            return savedCandleCount;
        }
    }
}
