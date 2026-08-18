package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableChartImageClient implements ChartImageClient {

    @Override
    public List<ChartImageResponse> createChartImages(ChartPatternAnalysisRequest request) {
        return List.of();
    }
}
