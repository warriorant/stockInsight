package com.example.stockanalysis.service;

import com.example.stockanalysis.ai.ChartPatternAnalysisClient;
import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.dto.StockResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ChartPatternAnalysisService {

    private final StockService stockService;
    private final ChartPatternAnalysisClient chartPatternAnalysisClient;
    private final StockPersistencePort stockPersistencePort;
    private final Map<String, ChartPatternAnalysisResponse> latestAnalysisBySymbol = new ConcurrentHashMap<>();

    public ChartPatternAnalysisService(
            StockService stockService,
            ChartPatternAnalysisClient chartPatternAnalysisClient,
            StockPersistencePort stockPersistencePort
    ) {
        this.stockService = stockService;
        this.chartPatternAnalysisClient = chartPatternAnalysisClient;
        this.stockPersistencePort = stockPersistencePort;
    }

    public ChartPatternAnalysisResponse analyze(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockResponse stock = stockService.getStock(normalizedSymbol);
        Map<String, List<PricePointResponse>> priceDataByPeriod = new LinkedHashMap<>();
        priceDataByPeriod.put("6M", stockService.getPrices(normalizedSymbol, "6M"));
        priceDataByPeriod.put("12M", stockService.getPrices(normalizedSymbol, "1Y"));
        FinancialDataResponse financialData = stockService.getFinancials(normalizedSymbol);

        ChartPatternAnalysisRequest request = new ChartPatternAnalysisRequest(
                stock.symbol(),
                stock.name(),
                stock.market(),
                LocalDate.now(),
                priceDataByPeriod,
                financialData
        );

        ChartPatternAnalysisResponse response = chartPatternAnalysisClient.analyze(request);
        latestAnalysisBySymbol.put(normalizedSymbol, response);
        stockPersistencePort.saveChartPatternAnalysis(response);
        return response;
    }

    public ChartPatternAnalysisResponse analyzeWithCandles(
            String symbol,
            LocalDate targetDate,
            List<StockCandleResponse> candles
    ) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockResponse stock = stockService.getStock(normalizedSymbol);
        FinancialDataResponse financialData = stockService.getFinancials(normalizedSymbol);

        ChartPatternAnalysisRequest request = new ChartPatternAnalysisRequest(
                stock.symbol(),
                stock.name(),
                stock.market(),
                targetDate == null ? LocalDate.now() : targetDate,
                Map.of(),
                financialData
        );

        return chartPatternAnalysisClient.analyze(request, candles == null ? List.of() : candles);
    }

    public ChartPatternAnalysisResponse getLatestAnalysis(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        java.util.Optional<ChartPatternAnalysisResponse> persisted = stockPersistencePort.findLatestChartPatternAnalysis(normalizedSymbol);
        if (persisted.isPresent()) {
            return persisted.get();
        }
        ChartPatternAnalysisResponse cachedAnalysis = latestAnalysisBySymbol.get(normalizedSymbol);
        return cachedAnalysis != null ? cachedAnalysis : analyze(normalizedSymbol);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
