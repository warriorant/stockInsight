package com.example.stockanalysis.ai;

import com.example.stockanalysis.dto.ChartPatternAnalysisRequest;
import com.example.stockanalysis.dto.ChartPatternAnalysisResponse;
import com.example.stockanalysis.dto.PatternBacktestMetricResponse;
import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.SimilarPatternCaseResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.chart-pattern.mock-enabled", havingValue = "true")
public class MockChartPatternAnalysisClient implements ChartPatternAnalysisClient {

    @Override
    public ChartPatternAnalysisResponse analyze(ChartPatternAnalysisRequest request) {
        List<PricePointResponse> oneYearPrices = request.priceDataByPeriod().getOrDefault("12M", List.of());
        BigDecimal trendRate = calculateTrendRate(oneYearPrices);
        PatternScenario scenario = selectScenario(request.symbol(), trendRate);
        BigDecimal confidence = calculateConfidence(oneYearPrices, scenario.baseConfidence());

        return new ChartPatternAnalysisResponse(
                request.symbol(),
                request.name(),
                null,
                null,
                "mock",
                scenario.patternName(),
                scenario.patternCategory(),
                confidence,
                "%s의 최근 차트는 '%s' 패턴과 가장 유사하게 분류됐습니다. 아래 값은 과거 유사 표본의 통계이며 투자 행동을 지시하지 않습니다."
                        .formatted(request.name(), scenario.patternName()),
                scenario.description(),
                scenario.backtests(),
                List.of(),
                scenario.similarCases(),
                List.of(
                        "거래량이 패턴 완성 구간에서 함께 늘었는지 확인",
                        "PER, PBR, ROE와 같은 기본 지표가 차트 흐름을 뒷받침하는지 확인",
                        "실적 발표, 금리, 환율, 지정학 이슈 같은 외부 일정을 함께 확인"
                ),
                "이 분석은 차트 모양과 과거 통계 정보를 제공하기 위한 mock 결과이며, 특정 투자 행동을 지시하지 않습니다."
        );
    }

    private PatternScenario selectScenario(String symbol, BigDecimal trendRate) {
        if (trendRate.compareTo(BigDecimal.valueOf(8)) >= 0) {
            return ascendingTriangle();
        }
        if (trendRate.compareTo(BigDecimal.valueOf(-8)) <= 0) {
            return descendingChannel();
        }

        int bucket = Math.floorMod(symbol.hashCode(), 3);
        if (bucket == 0) {
            return doubleBottom();
        }
        if (bucket == 1) {
            return boxRange();
        }
        return doubleTop();
    }

    private BigDecimal calculateTrendRate(List<PricePointResponse> priceData) {
        if (priceData.size() < 2) {
            return BigDecimal.ZERO;
        }

        PricePointResponse first = priceData.get(0);
        PricePointResponse last = priceData.get(priceData.size() - 1);
        if (first.close().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return last.close()
                .subtract(first.close())
                .divide(first.close(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateConfidence(List<PricePointResponse> priceData, BigDecimal baseConfidence) {
        if (priceData.size() >= 120) {
            return baseConfidence.add(BigDecimal.valueOf(0.05)).min(BigDecimal.valueOf(0.88));
        }
        if (priceData.size() >= 60) {
            return baseConfidence;
        }
        return baseConfidence.subtract(BigDecimal.valueOf(0.08)).max(BigDecimal.valueOf(0.52));
    }

    private PatternScenario ascendingTriangle() {
        return new PatternScenario(
                "상승 삼각형",
                "추세 지속 관찰형",
                BigDecimal.valueOf(0.74),
                "고점은 비슷한 구간에서 막히지만 저점이 점차 높아지는 형태입니다. 방향을 단정하기보다 돌파 여부와 거래량 변화를 함께 봐야 합니다.",
                List.of(
                        metric("1개월", 2.1, 1.4, 57.8, -8.6),
                        metric("3개월", 5.8, 4.2, 61.3, -14.9),
                        metric("1년", 12.4, 9.8, 64.1, -28.3)
                ),
                cases(3.2, 7.4, 16.8)
        );
    }

    private PatternScenario doubleBottom() {
        return new PatternScenario(
                "이중 바닥",
                "반전 가능성 관찰형",
                BigDecimal.valueOf(0.69),
                "비슷한 가격대에서 두 번 하락이 멈춘 형태입니다. 목선 돌파와 거래량이 확인되지 않으면 단순 횡보일 수도 있습니다.",
                List.of(
                        metric("1개월", 1.6, 0.8, 54.2, -10.4),
                        metric("3개월", 4.9, 3.1, 59.7, -16.2),
                        metric("1년", 10.7, 8.5, 62.4, -31.6)
                ),
                cases(2.5, 6.1, 13.9)
        );
    }

    private PatternScenario boxRange() {
        return new PatternScenario(
                "박스권 횡보",
                "방향성 대기형",
                BigDecimal.valueOf(0.66),
                "일정한 상단과 하단 사이에서 가격이 움직이는 형태입니다. 상단과 하단 중 어느 쪽을 벗어나는지 관찰하는 구간입니다.",
                List.of(
                        metric("1개월", 0.4, 0.2, 50.8, -7.9),
                        metric("3개월", 1.7, 1.1, 52.5, -13.7),
                        metric("1년", 6.2, 4.0, 56.3, -24.8)
                ),
                cases(0.9, 2.7, 8.1)
        );
    }

    private PatternScenario doubleTop() {
        return new PatternScenario(
                "이중 천장",
                "상승 둔화 관찰형",
                BigDecimal.valueOf(0.68),
                "비슷한 가격대에서 두 번 상승이 막힌 형태입니다. 하락을 예측한다기보다 저항 구간이 반복됐는지 확인하는 참고 신호입니다.",
                List.of(
                        metric("1개월", -0.7, -0.2, 46.9, -11.8),
                        metric("3개월", 0.8, 0.4, 49.6, -18.1),
                        metric("1년", 4.3, 3.3, 53.2, -32.4)
                ),
                cases(-1.2, 1.5, 5.7)
        );
    }

    private PatternScenario descendingChannel() {
        return new PatternScenario(
                "하락 채널",
                "변동성 확대 관찰형",
                BigDecimal.valueOf(0.71),
                "고점과 저점이 함께 낮아지는 형태입니다. 반등 가능성보다 추세가 언제 약해지는지와 외부 악재 해소 여부를 확인해야 합니다.",
                List.of(
                        metric("1개월", -1.8, -1.1, 42.7, -13.6),
                        metric("3개월", -0.3, -0.8, 47.2, -21.4),
                        metric("1년", 3.6, 2.4, 51.8, -36.9)
                ),
                cases(-2.4, -0.6, 4.8)
        );
    }

    private PatternBacktestMetricResponse metric(
            String period,
            double averageReturn,
            double medianReturn,
            double positiveRate,
            double worstReturn
    ) {
        return new PatternBacktestMetricResponse(
                period,
                BigDecimal.valueOf(averageReturn),
                BigDecimal.valueOf(medianReturn),
                BigDecimal.valueOf(positiveRate),
                BigDecimal.valueOf(worstReturn)
        );
    }

    private List<SimilarPatternCaseResponse> cases(double oneMonth, double threeMonths, double oneYear) {
        return List.of(
                new SimilarPatternCaseResponse(
                        "CASE-01",
                        "과거 유사 표본 A",
                        "2021-04-15",
                        BigDecimal.valueOf(oneMonth),
                        BigDecimal.valueOf(threeMonths),
                        BigDecimal.valueOf(oneYear)
                ),
                new SimilarPatternCaseResponse(
                        "CASE-02",
                        "과거 유사 표본 B",
                        "2022-09-21",
                        BigDecimal.valueOf(oneMonth - 2.1),
                        BigDecimal.valueOf(threeMonths - 3.4),
                        BigDecimal.valueOf(oneYear - 6.2)
                ),
                new SimilarPatternCaseResponse(
                        "CASE-03",
                        "과거 유사 표본 C",
                        "2024-02-08",
                        BigDecimal.valueOf(oneMonth + 1.3),
                        BigDecimal.valueOf(threeMonths + 2.8),
                        BigDecimal.valueOf(oneYear + 4.7)
                )
        );
    }

    private record PatternScenario(
            String patternName,
            String patternCategory,
            BigDecimal baseConfidence,
            String description,
            List<PatternBacktestMetricResponse> backtests,
            List<SimilarPatternCaseResponse> similarCases
    ) {
    }
}
