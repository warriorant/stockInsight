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

    private static final String CANDLE_SOURCE = "TOSS";
    private static final int MIN_CANDLES_FOR_DB_ANALYSIS = 100;

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
        return analyze(symbol, false);
    }

    public ChartPatternAnalysisResponse analyze(String symbol, boolean refresh) {
        String normalizedSymbol = normalizeSymbol(symbol);
        LocalDate targetDate = LocalDate.now();
        if (!refresh) {
            java.util.Optional<ChartPatternAnalysisResponse> cached = stockPersistencePort.findChartPatternAnalysis(normalizedSymbol, targetDate);
            if (cached.isPresent()) {
                latestAnalysisBySymbol.put(normalizedSymbol, cached.get());
                return cached.get();
            }
        }

        StockResponse stock = stockService.getStock(normalizedSymbol);
        List<StockCandleResponse> storedCandles = stockPersistencePort.findCandles(
                normalizedSymbol,
                targetDate.minusMonths(12),
                targetDate,
                CANDLE_SOURCE
        );
        boolean useStoredCandles = storedCandles.size() >= MIN_CANDLES_FOR_DB_ANALYSIS;

        Map<String, List<PricePointResponse>> priceDataByPeriod = new LinkedHashMap<>();
        if (useStoredCandles) {
            priceDataByPeriod.put("6M", pricePointsFromCandles(storedCandles, targetDate.minusMonths(6)));
            priceDataByPeriod.put("12M", pricePointsFromCandles(storedCandles, targetDate.minusMonths(12)));
        } else {
            priceDataByPeriod.put("6M", stockService.getPrices(normalizedSymbol, "6M"));
            priceDataByPeriod.put("12M", stockService.getPrices(normalizedSymbol, "1Y"));
        }
        FinancialDataResponse financialData = stockService.getFinancials(normalizedSymbol);

        ChartPatternAnalysisRequest request = new ChartPatternAnalysisRequest(
                stock.symbol(),
                stock.name(),
                stock.market(),
                targetDate,
                priceDataByPeriod,
                financialData
        );

        ChartPatternAnalysisResponse response = useStoredCandles
                ? chartPatternAnalysisClient.analyze(request, storedCandles)
                : chartPatternAnalysisClient.analyze(request);
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

    private List<PricePointResponse> pricePointsFromCandles(List<StockCandleResponse> candles, LocalDate startDate) {
        return candles.stream()
                .filter(candle -> !candle.timestamp().toLocalDate().isBefore(startDate))
                .map(candle -> new PricePointResponse(
                        candle.timestamp().toLocalDate(),
                        candle.closePrice(),
                        candle.volume()
                ))
                .toList();
    }
}
