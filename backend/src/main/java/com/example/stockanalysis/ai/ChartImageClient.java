package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.StockCandleResponse;
import java.util.List;

public interface ChartImageClient {

    List<ChartImageResponse> createChartImages(ChartPatternAnalysisRequest request);

    default List<ChartImageResponse> createChartImages(
            ChartPatternAnalysisRequest request,
            List<StockCandleResponse> candles
    ) {
        return createChartImages(request);
    }
}
