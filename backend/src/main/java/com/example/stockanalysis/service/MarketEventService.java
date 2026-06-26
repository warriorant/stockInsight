package com.example.stockanalysis.service;

import com.example.stockanalysis.dto.MarketEventResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MarketEventService {

    private final List<MarketEventResponse> events = createEvents();

    public List<MarketEventResponse> getUpcomingEvents() {
        return events.stream()
                .sorted(Comparator.comparing(MarketEventResponse::scheduledDate))
                .toList();
    }

    public List<MarketEventResponse> getEventsForStock(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);

        return events.stream()
                .filter(event -> event.affectedSymbols().contains(normalizedSymbol))
                .sorted(Comparator.comparing(MarketEventResponse::scheduledDate))
                .toList();
    }

    private List<MarketEventResponse> createEvents() {
        return List.of(
                new MarketEventResponse(
                        "us-cpi",
                        "미국 소비자물가지수(CPI) 발표",
                        "물가",
                        LocalDate.now().plusDays(12),
                        "높음",
                        "물가가 예상보다 높으면 금리 인하 기대가 약해질 수 있습니다.",
                        "성장주와 반도체처럼 미래 기대가 큰 종목은 금리 전망 변화에 더 민감하게 움직일 수 있어요.",
                        List.of("기술", "커뮤니케이션", "경기소비재"),
                        List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI")
                ),
                new MarketEventResponse(
                        "fomc",
                        "미국 FOMC 금리 결정",
                        "금리",
                        LocalDate.now().plusDays(20),
                        "높음",
                        "기준금리와 향후 금리 방향에 대한 발언은 전 세계 증시에 영향을 줍니다.",
                        "금리가 내려갈 것 같으면 주식시장에는 대체로 우호적이고, 금리가 오래 높게 유지될 것 같으면 부담이 될 수 있어요.",
                        List.of("기술", "커뮤니케이션", "산업재", "경기소비재"),
                        List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY")
                ),
                new MarketEventResponse(
                        "kr-rate",
                        "한국은행 기준금리 결정",
                        "금리",
                        LocalDate.now().plusDays(27),
                        "보통",
                        "국내 금리 방향은 환율, 소비, 기업 자금 조달 비용에 영향을 줍니다.",
                        "금리가 높으면 기업이 돈을 빌리는 비용이 커지고, 소비 심리도 약해질 수 있어요.",
                        List.of("경기소비재", "산업재", "커뮤니케이션"),
                        List.of("KAKAO", "NAVER", "HYUNDAI", "LGENERGY")
                ),
                new MarketEventResponse(
                        "quadruple-witching",
                        "네 마녀의 날",
                        "수급",
                        LocalDate.now().plusDays(33),
                        "보통",
                        "주가지수 선물, 옵션 등 여러 파생상품 만기일이 겹치는 날입니다.",
                        "기업 가치가 갑자기 바뀌는 날이라기보다, 큰 자금의 포지션 정리 때문에 장중 변동성이 커질 수 있는 날이에요.",
                        List.of("기술", "커뮤니케이션", "산업재", "경기소비재"),
                        List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY")
                ),
                new MarketEventResponse(
                        "memory-cycle",
                        "메모리 반도체 업황 점검",
                        "업종",
                        LocalDate.now().plusDays(40),
                        "높음",
                        "DRAM, NAND 가격 전망은 국내 반도체 기업 실적 기대에 직접적으로 연결됩니다.",
                        "반도체 기업은 제품 가격이 좋아질 것이라는 기대만으로도 주가가 먼저 움직일 때가 많아요.",
                        List.of("기술"),
                        List.of("SAMSUNG", "SKHYNIX")
                )
        );
    }
}
