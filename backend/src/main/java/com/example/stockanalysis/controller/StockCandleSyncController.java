package com.example.stockanalysis.controller;

import com.example.stockanalysis.dto.StockCandleSyncStatusResponse;
import com.example.stockanalysis.service.StockCandleSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/candles")
public class StockCandleSyncController {

    private final StockCandleSyncService stockCandleSyncService;

    public StockCandleSyncController(StockCandleSyncService stockCandleSyncService) {
        this.stockCandleSyncService = stockCandleSyncService;
    }

    @PostMapping("/kospi")
    public StockCandleSyncStatusResponse startKospiCandleSync(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer months
    ) {
        return stockCandleSyncService.startKospiSync("manual", limit, months);
    }

    @GetMapping("/kospi")
    public StockCandleSyncStatusResponse getKospiCandleSyncStatus() {
        return stockCandleSyncService.getStatus();
    }
}
