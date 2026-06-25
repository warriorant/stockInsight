package com.example.stockanalysis.service;

import com.example.stockanalysis.ai.AiAnalysisClient;
import com.example.stockanalysis.dto.AiAnalysisRequest;
import com.example.stockanalysis.dto.AiAnalysisResponse;
import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private final StockService stockService;
    private final AiAnalysisClient aiAnalysisClient;
    private final Map<String, AiAnalysisResponse> latestAnalysisBySymbol = new ConcurrentHashMap<>();

    public AnalysisService(StockService stockService, AiAnalysisClient aiAnalysisClient) {
        this.stockService = stockService;
        this.aiAnalysisClient = aiAnalysisClient;
    }

    public AiAnalysisResponse analyze(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        StockResponse stock = stockService.getStock(normalizedSymbol);
        List<PricePointResponse> priceData = stockService.getPrices(normalizedSymbol, "3M");
        FinancialDataResponse financialData = stockService.getFinancials(normalizedSymbol);

        AiAnalysisRequest request = new AiAnalysisRequest(
                stock.symbol(),
                stock.name(),
                stock.market(),
                priceData,
                financialData
        );

        AiAnalysisResponse response = aiAnalysisClient.analyze(request);
        latestAnalysisBySymbol.put(normalizedSymbol, response);
        return response;
    }

    public AiAnalysisResponse getLatestAnalysis(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        AiAnalysisResponse cachedAnalysis = latestAnalysisBySymbol.get(normalizedSymbol);
        return cachedAnalysis != null ? cachedAnalysis : analyze(normalizedSymbol);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
