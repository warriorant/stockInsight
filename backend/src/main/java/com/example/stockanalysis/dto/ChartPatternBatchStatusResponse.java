package com.example.stockanalysis.dto;

import java.time.LocalDateTime;

public record ChartPatternBatchStatusResponse(
        String runId,
        String trigger,
        boolean running,
        boolean scheduledEnabled,
        String cron,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalCount,
        int completedCount,
        int successCount,
        int failureCount,
        String currentSymbol,
        String currentName,
        String lastSuccessSymbol,
        String lastErrorSymbol,
        String lastErrorMessage
) {
}
