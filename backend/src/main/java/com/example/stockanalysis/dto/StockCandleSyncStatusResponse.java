package com.example.stockanalysis.dto;

import java.time.LocalDateTime;

public record StockCandleSyncStatusResponse(
        String runId,
        String trigger,
        boolean running,
        int months,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalCount,
        int completedCount,
        int successCount,
        int failureCount,
        int savedCandleCount,
        String currentSymbol,
        String currentName,
        String lastSuccessSymbol,
        String lastErrorSymbol,
        String lastErrorMessage
) {
}
