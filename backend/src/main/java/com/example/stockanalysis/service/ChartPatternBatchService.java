package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.ChartPatternBatchStatusResponse;
import com.example.stockanalysis.dto.StockResponse;
import jakarta.annotation.PreDestroy;
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
public class ChartPatternBatchService {

    private static final Logger log = LoggerFactory.getLogger(ChartPatternBatchService.class);

    private final StockService stockService;
    private final ChartPatternAnalysisService chartPatternAnalysisService;
    private final boolean scheduledEnabled;
    private final String cron;
    private final long delayMs;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile BatchState currentState = BatchState.idle();

    public ChartPatternBatchService(
            StockService stockService,
            ChartPatternAnalysisService chartPatternAnalysisService,
            @Value("${app.chart-pattern-batch.enabled:false}") boolean scheduledEnabled,
            @Value("${app.chart-pattern-batch.cron:0 0 22 * * MON-FRI}") String cron,
            @Value("${app.chart-pattern-batch.delay-ms:1000}") long delayMs
    ) {
        this.stockService = stockService;
        this.chartPatternAnalysisService = chartPatternAnalysisService;
        this.scheduledEnabled = scheduledEnabled;
        this.cron = cron;
        this.delayMs = Math.max(0, delayMs);
        this.executorService = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "chart-pattern-kospi-batch");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Scheduled(cron = "${app.chart-pattern-batch.cron:0 0 22 * * MON-FRI}", zone = "Asia/Seoul")
    public void runScheduledKospiBatch() {
        if (!scheduledEnabled) {
            return;
        }
        startKospiBatch("scheduled");
    }

    public ChartPatternBatchStatusResponse startKospiBatch(String trigger) {
        return startKospiBatch(trigger, null);
    }

    public ChartPatternBatchStatusResponse startKospiBatch(String trigger, Integer limit) {
        if (!running.compareAndSet(false, true)) {
            return getStatus();
        }

        List<StockResponse> stocks = limitedStocks(limit);
        BatchState state = BatchState.started(trigger, stocks.size());
        currentState = state;
        executorService.submit(() -> runBatch(state, stocks));
        return getStatus();
    }

    public ChartPatternBatchStatusResponse getStatus() {
        return currentState.toResponse(running.get(), scheduledEnabled, cron);
    }

    private void runBatch(BatchState state, List<StockResponse> stocks) {
        log.info("KOSPI chart pattern batch started. runId={}, total={}", state.runId(), stocks.size());
        try {
            for (StockResponse stock : stocks) {
                state.markCurrent(stock.symbol(), stock.name());
                try {
                    chartPatternAnalysisService.analyze(stock.symbol());
                    state.markSuccess(stock.symbol());
                } catch (Exception error) {
                    state.markFailure(stock.symbol(), rootMessage(error));
                    log.warn("KOSPI chart pattern batch item failed. symbol={}, name={}", stock.symbol(), stock.name(), error);
                }

                pauseBetweenRequests();
            }
        } finally {
            state.finish();
            running.set(false);
            log.info(
                    "KOSPI chart pattern batch finished. runId={}, success={}, failure={}",
                    state.runId(),
                    state.successCount(),
                    state.failureCount()
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

    private static final class BatchState {

        private final String runId;
        private final String trigger;
        private final LocalDateTime startedAt;
        private final int totalCount;
        private LocalDateTime finishedAt;
        private int completedCount;
        private int successCount;
        private int failureCount;
        private String currentSymbol;
        private String currentName;
        private String lastSuccessSymbol;
        private String lastErrorSymbol;
        private String lastErrorMessage;

        private BatchState(String runId, String trigger, LocalDateTime startedAt, int totalCount) {
            this.runId = runId;
            this.trigger = trigger;
            this.startedAt = startedAt;
            this.totalCount = totalCount;
        }

        static BatchState idle() {
            return new BatchState(null, "idle", null, 0);
        }

        static BatchState started(String trigger, int totalCount) {
            return new BatchState(UUID.randomUUID().toString(), trigger, LocalDateTime.now(), totalCount);
        }

        synchronized void markCurrent(String symbol, String name) {
            this.currentSymbol = symbol;
            this.currentName = name;
        }

        synchronized void markSuccess(String symbol) {
            this.completedCount += 1;
            this.successCount += 1;
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

        synchronized ChartPatternBatchStatusResponse toResponse(boolean running, boolean scheduledEnabled, String cron) {
            return new ChartPatternBatchStatusResponse(
                    runId,
                    trigger,
                    running,
                    scheduledEnabled,
                    cron,
                    startedAt,
                    finishedAt,
                    totalCount,
                    completedCount,
                    successCount,
                    failureCount,
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
    }
}
