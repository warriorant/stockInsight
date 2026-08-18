package com.example.stockanalysis.controller;

import com.example.stockanalysis.dto.ChartPatternBatchStatusResponse;
import com.example.stockanalysis.service.ChartPatternBatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chart-pattern-batch")
public class ChartPatternBatchController {

    private final ChartPatternBatchService chartPatternBatchService;

    public ChartPatternBatchController(ChartPatternBatchService chartPatternBatchService) {
        this.chartPatternBatchService = chartPatternBatchService;
    }

    @PostMapping("/kospi")
    public ChartPatternBatchStatusResponse startKospiBatch(@RequestParam(required = false) Integer limit) {
        return chartPatternBatchService.startKospiBatch("manual", limit);
    }

    @GetMapping("/kospi")
    public ChartPatternBatchStatusResponse getKospiBatchStatus() {
        return chartPatternBatchService.getStatus();
    }
}
