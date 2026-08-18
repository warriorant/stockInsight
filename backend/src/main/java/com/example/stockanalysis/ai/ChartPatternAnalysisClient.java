package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import java.util.List;

public interface ChartPatternAnalysisClient {

    ChartPatternAnalysisResponse analyze(ChartPatternAnalysisRequest request);

    default ChartPatternAnalysisResponse analyze(
            ChartPatternAnalysisRequest request,
            List<StockCandleResponse> candles
    ) {
        return analyze(request);
    }
}
