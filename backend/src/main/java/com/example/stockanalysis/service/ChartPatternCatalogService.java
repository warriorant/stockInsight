package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.PatternBacktestMetricResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChartPatternCatalogService {

    private final Map<Integer, ChartPatternDefinition> definitions;

    public ChartPatternCatalogService() {
        this.definitions = createDefinitions();
    }

    public ChartPatternDefinition getDefinition(Integer patternId) {
        if (patternId == null) {
            return definitions.get(19);
        }
        return definitions.getOrDefault(patternId, definitions.get(19));
    }

    public PatternBacktestMetricResponse referenceReturn(Integer patternId, String period) {
        ChartPatternDefinition definition = getDefinition(patternId);
        return definition.referenceReturns()
                .stream()
                .filter(item -> item.period().equalsIgnoreCase(period))
                .findFirst()
                .orElseGet(() -> neutralMetric(period));
    }

    public List<PatternBacktestMetricResponse> referenceReturns(Integer patternId) {
        return getDefinition(patternId).referenceReturns();
    }

    private Map<Integer, ChartPatternDefinition> createDefinitions() {
        Map<Integer, ChartPatternDefinition> result = new LinkedHashMap<>();
        result.put(0, definition(0, "수평 채널", "방향성 대기형", "상단 저항과 하단 지지 사이에서 움직이는 박스권 흐름입니다. 돌파 방향이 확인되기 전까지는 중립 성격이 강합니다.", 0.8, 0.4, 51.0, -10.5, 2.2, 1.2, 52.0, -20.0));
        result.put(1, definition(1, "상승 채널", "상승 추세 관찰형", "고점과 저점이 함께 높아지는 추세 흐름입니다. 채널 하단 지지와 추세 유지 여부가 핵심입니다.", 4.2, 2.9, 59.0, -11.0, 9.5, 6.6, 62.0, -21.0));
        result.put(2, definition(2, "하락 채널", "하락 추세 관찰형", "고점과 저점이 함께 낮아지는 흐름입니다. 단기 반등보다 추세 전환 확인이 더 중요합니다.", -1.6, -1.1, 44.0, -16.5, 1.0, 0.3, 48.0, -30.0));
        result.put(3, definition(3, "대칭 삼각수렴", "돌파 방향 관찰형", "고점은 낮아지고 저점은 높아지며 변동폭이 줄어드는 흐름입니다. 방향성은 돌파가 나온 뒤 판단하는 쪽이 안전합니다.", 1.2, 0.8, 52.0, -13.0, 4.0, 2.8, 55.0, -25.0));
        result.put(4, definition(4, "상승 삼각수렴", "상방 돌파 관찰형", "상단 저항은 비슷하고 저점이 높아지는 흐름입니다. 저항 돌파가 확인되면 강세 해석이 붙는 경우가 많습니다.", 3.2, 2.2, 57.0, -12.0, 8.3, 5.8, 61.0, -23.0));
        result.put(5, definition(5, "하락 삼각수렴", "하방 이탈 관찰형", "하단 지지는 비슷하고 고점이 낮아지는 흐름입니다. 지지선 이탈 여부를 특히 조심해서 봐야 합니다.", -1.4, -0.9, 43.0, -17.0, 0.6, 0.0, 47.0, -31.0));
        result.put(6, definition(6, "하락 쐐기형", "반전 가능성 관찰형", "하락 중 변동폭이 좁아지는 흐름입니다. 상방 돌파가 확인되면 반전 가능성을 보는 패턴입니다.", 3.6, 2.4, 58.0, -13.0, 9.1, 6.1, 62.0, -24.0));
        result.put(7, definition(7, "상승 쐐기형", "상승 피로 관찰형", "상승 중 변동폭이 좁아지는 흐름입니다. 상승 피로와 하방 이탈 위험을 함께 봅니다.", -1.8, -1.1, 42.0, -16.0, -0.2, -0.5, 46.0, -29.0));
        result.put(8, definition(8, "확장형", "변동성 확대형", "고점과 저점의 폭이 커지며 방향성이 불안정한 흐름입니다. 예측보다는 변동성 관리가 중요한 구간입니다.", -0.6, -0.3, 47.0, -20.0, 1.6, 0.7, 50.0, -35.0));
        result.put(9, definition(9, "헤드앤숄더", "약세 반전 관찰형", "세 개의 봉우리 중 가운데가 가장 높은 대표적인 약세 반전 관찰 패턴입니다. neckline 이탈 확인이 중요합니다.", -3.2, -2.0, 39.0, -22.0, -1.0, -1.4, 44.0, -34.0));
        result.put(10, definition(10, "역헤드앤숄더", "강세 반전 관찰형", "세 개의 바닥 중 가운데가 가장 낮은 대표적인 강세 반전 관찰 패턴입니다. 저항선 돌파와 거래량 확인이 중요합니다.", 4.6, 3.1, 61.0, -12.0, 11.5, 7.8, 65.0, -23.0));
        result.put(11, definition(11, "쌍봉", "상승 둔화 관찰형", "비슷한 저항대에서 두 번 상승이 막힌 흐름입니다. 지지선 이탈 시 약세 전환 신호로 해석될 수 있습니다.", -2.4, -1.6, 41.0, -19.0, -0.4, -0.8, 45.0, -31.0));
        result.put(12, definition(12, "쌍바닥", "하락 둔화 관찰형", "비슷한 지지대에서 두 번 하락이 멈춘 흐름입니다. 저항 돌파가 나오면 반등 신뢰도가 높아집니다.", 3.8, 2.6, 59.0, -13.0, 9.6, 6.5, 63.0, -25.0));
        result.put(13, definition(13, "삼산", "저항 반복 관찰형", "비슷한 가격대에서 세 번 상승이 막힌 흐름입니다. 쌍봉보다 저항 확인 횟수가 많아 약세 반전 성격을 더 강하게 봅니다.", -2.8, -1.9, 40.0, -21.0, -0.8, -1.1, 44.0, -33.0));
        result.put(14, definition(14, "삼천", "지지 반복 관찰형", "비슷한 가격대에서 세 번 하락이 멈춘 흐름입니다. 반복 지지 후 저항 돌파가 나오면 반등 관찰 가치가 커집니다.", 4.0, 2.7, 60.0, -13.0, 10.2, 6.9, 64.0, -25.0));
        result.put(15, definition(15, "컵 앤 핸들", "누적 후 돌파 관찰형", "둥근 바닥 이후 짧은 조정이 붙는 흐름입니다. 손잡이 구간 돌파와 거래량 증가가 핵심 확인 요소입니다.", 5.0, 3.6, 62.0, -12.0, 12.6, 8.8, 66.0, -24.0));
        result.put(16, definition(16, "원형 바닥", "장기 반전 관찰형", "긴 기간에 걸쳐 완만하게 바닥을 형성하는 흐름입니다. 급등보다 추세 회복을 천천히 확인하는 패턴입니다.", 3.2, 2.1, 57.0, -12.0, 10.8, 7.2, 64.0, -23.0));
        result.put(17, definition(17, "상승 깃발형", "상승 지속 관찰형", "강한 상승 이후 짧은 조정 구간이 나타나는 흐름입니다. 조정 상단 돌파 시 기존 상승 추세 지속으로 해석되는 경우가 많습니다.", 4.4, 3.2, 60.0, -11.0, 9.8, 7.0, 63.0, -21.0));
        result.put(18, definition(18, "하락 깃발형", "하락 지속 관찰형", "강한 하락 이후 짧은 반등 또는 조정이 나타나는 흐름입니다. 조정 하단 이탈 시 기존 하락 추세 지속을 경계합니다.", -3.0, -2.1, 39.0, -20.0, -1.2, -1.5, 43.0, -33.0));
        result.put(19, new ChartPatternDefinition(
                19,
                "Other(노이즈)",
                "분류 보류",
                "학습된 차트 패턴과 충분히 유사하지 않아 해석을 보류하는 결과입니다.",
                List.of(neutralMetric("6M"), neutralMetric("12M"))
        ));
        return Map.copyOf(result);
    }

    private ChartPatternDefinition definition(
            int patternId,
            String name,
            String category,
            String description,
            double sixMonthAverage,
            double sixMonthMedian,
            double sixMonthPositiveRate,
            double sixMonthWorst,
            double twelveMonthAverage,
            double twelveMonthMedian,
            double twelveMonthPositiveRate,
            double twelveMonthWorst
    ) {
        return new ChartPatternDefinition(
                patternId,
                name,
                category,
                description,
                List.of(
                        metric("6M", sixMonthAverage, sixMonthMedian, sixMonthPositiveRate, sixMonthWorst),
                        metric("12M", twelveMonthAverage, twelveMonthMedian, twelveMonthPositiveRate, twelveMonthWorst)
                )
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
                BigDecimal.valueOf(averageReturn).setScale(1, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(medianReturn).setScale(1, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(Math.max(35, Math.min(72, positiveRate))).setScale(1, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(worstReturn).setScale(1, java.math.RoundingMode.HALF_UP)
        );
    }

    private PatternBacktestMetricResponse neutralMetric(String period) {
        return new PatternBacktestMetricResponse(
                period,
                BigDecimal.ZERO.setScale(1),
                BigDecimal.ZERO.setScale(1),
                BigDecimal.valueOf(50).setScale(1),
                BigDecimal.ZERO.setScale(1)
        );
    }

    public record ChartPatternDefinition(
            Integer patternId,
            String name,
            String category,
            String description,
            List<PatternBacktestMetricResponse> referenceReturns
    ) {
    }
}
