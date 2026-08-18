package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.PatternBacktestMetricResponse;
import com.example.stockanalysis.dto.PeriodPatternAnalysisResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockCandleResponse;
import com.example.stockanalysis.service.ChartPatternCatalogService;
import com.example.stockanalysis.service.ChartPatternCatalogService.ChartPatternDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class TwoStepChartPatternAnalysisClient implements ChartPatternAnalysisClient {

    private final ChartImageClient chartImageClient;
    private final PatternPredictionClient patternPredictionClient;
    private final ChartPatternCatalogService chartPatternCatalogService;

    public TwoStepChartPatternAnalysisClient(
            ChartImageClient chartImageClient,
            PatternPredictionClient patternPredictionClient,
            ChartPatternCatalogService chartPatternCatalogService
    ) {
        this.chartImageClient = chartImageClient;
        this.patternPredictionClient = patternPredictionClient;
        this.chartPatternCatalogService = chartPatternCatalogService;
    }

    @Override
    public ChartPatternAnalysisResponse analyze(ChartPatternAnalysisRequest request) {
        List<PeriodPatternAnalysisResponse> periodAnalyses = chartImageClient.createChartImages(request)
                .stream()
                .map(chartImage -> analyzePeriod(request, chartImage))
                .toList();

        return responseFromPeriodAnalyses(request, periodAnalyses);
    }

    @Override
    public ChartPatternAnalysisResponse analyze(
            ChartPatternAnalysisRequest request,
            List<StockCandleResponse> candles
    ) {
        List<PeriodPatternAnalysisResponse> periodAnalyses = chartImageClient.createChartImages(request, candles)
                .stream()
                .map(chartImage -> analyzePeriod(request, chartImage))
                .toList();

        return responseFromPeriodAnalyses(request, periodAnalyses);
    }

    private ChartPatternAnalysisResponse responseFromPeriodAnalyses(
            ChartPatternAnalysisRequest request,
            List<PeriodPatternAnalysisResponse> periodAnalyses
    ) {
        if (periodAnalyses.isEmpty()) {
            ChartPatternDefinition unavailable = chartPatternCatalogService.getDefinition(19);
            return new ChartPatternAnalysisResponse(
                    request.symbol(),
                    request.name(),
                    unavailable.patternId(),
                    "AI_CHART_IMAGE_SERVER_NOT_CONFIGURED",
                    "waiting-for-ai-chart-image-server",
                    unavailable.name(),
                    unavailable.category(),
                    BigDecimal.ZERO,
                    "차트 이미지 생성에 필요한 실제 OHLC 데이터 연결이 아직 완료되지 않았습니다.",
                    "토스 Open API에서 12개월 일봉을 조회한 뒤 렌더 API로 전달하면 6개월/12개월 차트 이미지가 생성되고, 이후 패턴 분류가 실행됩니다.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                            "토스 WTS에 백엔드 서버 outbound IPv4 등록 필요",
                            "TOSS_CLIENT_ID, TOSS_CLIENT_SECRET 환경변수 필요",
                            "KOSPI_RENDER_API_KEY 환경변수 필요",
                            "렌더 API 응답은 image/* 원본 바디 또는 images 배열과 imageBase64 값을 포함해야 함"
                    ),
                    "실제 OHLC 데이터와 렌더 API 응답이 없을 때는 임의 패턴이나 가짜 수익률을 생성하지 않습니다."
            );
        }

        PeriodPatternAnalysisResponse representative = periodAnalyses.stream()
                .filter(item -> "12M".equalsIgnoreCase(item.period()))
                .findFirst()
                .or(() -> periodAnalyses.stream().findFirst())
                .orElseThrow();

        return new ChartPatternAnalysisResponse(
                request.symbol(),
                request.name(),
                representative.patternId(),
                representative.rawPattern(),
                "two-step-image-prediction",
                representative.patternName(),
                representative.patternCategory(),
                representative.confidence(),
                "%s의 6개월/12개월 차트를 이미지로 변환한 뒤 패턴 분류 서버로 분석했습니다. 결과는 투자 행동 지시가 아니라 패턴 참고 정보입니다."
                        .formatted(request.name()),
                representative.patternDescription(),
                representative.referenceReturns(),
                periodAnalyses,
                List.of(),
                List.of(
                        "AI 서버1의 차트 생성 기준 날짜와 기간이 의도한 값인지 확인",
                        "AI 서버2의 신뢰도가 낮으면 Other 또는 보류 결과로 해석",
                        "패턴별 참고 수익률은 전문가 견해 기반 기준표이며 실제 백테스트 DB 값이 아님"
                ),
                "이 분석은 차트 패턴 분류와 패턴별 참고 통계를 제공하며, 특정 투자 행동을 지시하지 않습니다."
        );
    }

    private PeriodPatternAnalysisResponse analyzePeriod(ChartPatternAnalysisRequest request, ChartImageResponse chartImage) {
        PatternPredictionResponse prediction = patternPredictionClient.predict(chartImage)
                .orElseGet(() -> unavailablePrediction());

        ChartPatternDefinition definition = chartPatternCatalogService.getDefinition(prediction.patternId());
        PatternBacktestMetricResponse referenceReturn = chartPatternCatalogService.referenceReturn(
                definition.patternId(),
                chartImage.period()
        );

        return new PeriodPatternAnalysisResponse(
                chartImage.period(),
                definition.patternId(),
                prediction.rawPattern(),
                definition.name(),
                definition.category(),
                prediction.confidence(),
                definition.description(),
                List.of(referenceReturn),
                chartImage.imageBytes().length > 0,
                chartImageDataUrl(chartImage)
        );
    }

    private String chartImageDataUrl(ChartImageResponse chartImage) {
        if (chartImage.imageBytes().length == 0) {
            return null;
        }
        String contentType = chartImage.contentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            contentType = "image/png";
        }
        String encodedImage = Base64.getEncoder().encodeToString(chartImage.imageBytes());
        return "data:%s;base64,%s".formatted(contentType, encodedImage);
    }

    private PatternPredictionResponse unavailablePrediction() {
        return new PatternPredictionResponse(
                19,
                "AI_PATTERN_SERVER_UNAVAILABLE",
                BigDecimal.ZERO
        );
    }

    @SuppressWarnings("unused")
    private BigDecimal trendRate(List<PricePointResponse> prices) {
        if (prices.size() < 2) {
            return BigDecimal.ZERO;
        }
        PricePointResponse first = prices.get(0);
        PricePointResponse last = prices.get(prices.size() - 1);
        if (first.close().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return last.close()
                .subtract(first.close())
                .divide(first.close(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    @SuppressWarnings("unused")
    private BigDecimal confidenceFromPrices(List<PricePointResponse> prices) {
        if (prices.size() >= 180) {
            return BigDecimal.valueOf(0.78);
        }
        if (prices.size() >= 80) {
            return BigDecimal.valueOf(0.70);
        }
        return BigDecimal.valueOf(0.58);
    }
}
