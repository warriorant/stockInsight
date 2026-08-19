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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChartPatternAnalysisService {

    private static final String CANDLE_SOURCE = "TOSS";
    private static final int MIN_CANDLES_FOR_DB_ANALYSIS = 100;

    private final StockService stockService;
    private final ChartPatternAnalysisClient chartPatternAnalysisClient;
    private final StockPersistencePort stockPersistencePort;
    private final boolean onDemandEnabled;
    private final Map<String, ChartPatternAnalysisResponse> latestAnalysisBySymbol = new ConcurrentHashMap<>();

    public ChartPatternAnalysisService(
            StockService stockService,
            ChartPatternAnalysisClient chartPatternAnalysisClient,
            StockPersistencePort stockPersistencePort,
            @Value("${app.ai.chart-pattern.on-demand-enabled:false}") boolean onDemandEnabled
    ) {
        this.stockService = stockService;
        this.chartPatternAnalysisClient = chartPatternAnalysisClient;
        this.stockPersistencePort = stockPersistencePort;
        this.onDemandEnabled = onDemandEnabled;
    }

    public ChartPatternAnalysisResponse analyze(String symbol) {
        return analyze(symbol, false);
    }

    public ChartPatternAnalysisResponse analyze(String symbol, boolean refresh) {
        String normalizedSymbol = normalizeSymbol(symbol);
        LocalDate targetDate = LocalDate.now();
        if (!onDemandEnabled) {
            java.util.Optional<ChartPatternAnalysisResponse> cached = stockPersistencePort.findLatestChartPatternAnalysis(normalizedSymbol);
            if (cached.isPresent()) {
                latestAnalysisBySymbol.put(normalizedSymbol, cached.get());
                return cached.get();
            }
            return preparingResponse(normalizedSymbol);
        }

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
        if (!onDemandEnabled) {
            return preparingResponse(normalizedSymbol);
        }
        ChartPatternAnalysisResponse cachedAnalysis = latestAnalysisBySymbol.get(normalizedSymbol);
        return cachedAnalysis != null ? cachedAnalysis : analyze(normalizedSymbol);
    }

    private ChartPatternAnalysisResponse preparingResponse(String symbol) {
        StockResponse stock = stockService.getStock(symbol);
        return new ChartPatternAnalysisResponse(
                stock.symbol(),
                stock.name(),
                null,
                "ANALYSIS_NOT_PREPARED",
                "preparing",
                "분석 준비 중",
                "분석 대기",
                null,
                "%s의 차트 패턴 분석 결과는 아직 준비되지 않았습니다. 발표 모드에서는 미리 저장된 분석 결과만 표시합니다."
                        .formatted(stock.name()),
                "아직 DB에 저장된 차트 패턴 분석 결과가 없습니다.",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "발표용 DB seed 대상 종목인지 확인",
                        "운영자가 분석 결과를 DB에 적재한 뒤 다시 확인",
                        "실시간 AI 호출은 현재 발표 안정성을 위해 비활성화"
                ),
                "현재 화면은 분석 준비 상태 안내이며, 특정 투자 행동을 지시하지 않습니다."
        );
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
