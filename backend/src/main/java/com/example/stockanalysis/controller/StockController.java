package com.example.stockanalysis.controller;

import com.example.stockanalysis.dto.AiAnalysisResponse;
import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.dto.MarketEventResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.service.AnalysisService;
import com.example.stockanalysis.service.MarketEventService;
import com.example.stockanalysis.service.StockService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;
    private final AnalysisService analysisService;
    private final MarketEventService marketEventService;

    public StockController(
            StockService stockService,
            AnalysisService analysisService,
            MarketEventService marketEventService
    ) {
        this.stockService = stockService;
        this.analysisService = analysisService;
        this.marketEventService = marketEventService;
    }

    @GetMapping
    public List<StockResponse> getStocks() {
        return stockService.getStocks();
    }

    @GetMapping("/search")
    public List<StockResponse> searchStocks(@RequestParam(defaultValue = "") String keyword) {
        return stockService.searchStocks(keyword);
    }

    @GetMapping("/{symbol}")
    public StockResponse getStock(@PathVariable String symbol) {
        return stockService.getStock(symbol);
    }

    @GetMapping("/{symbol}/prices")
    public List<PricePointResponse> getPrices(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3M") String range
    ) {
        return stockService.getPrices(symbol, range);
    }

    @GetMapping("/{symbol}/financials")
    public FinancialDataResponse getFinancials(@PathVariable String symbol) {
        return stockService.getFinancials(symbol);
    }

    @GetMapping("/{symbol}/events")
    public List<MarketEventResponse> getEvents(@PathVariable String symbol) {
        return marketEventService.getEventsForStock(symbol);
    }

    @PostMapping("/{symbol}/analysis")
    public AiAnalysisResponse analyze(@PathVariable String symbol) {
        return analysisService.analyze(symbol);
    }

    @GetMapping("/{symbol}/analysis/latest")
    public AiAnalysisResponse getLatestAnalysis(@PathVariable String symbol) {
        return analysisService.getLatestAnalysis(symbol);
    }
}
