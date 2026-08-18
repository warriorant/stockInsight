package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.MarketEventResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedMarketEventClient implements MarketEventClient {

    private static final List<String> ALL_SYMBOLS = List.of("SAMSUNG", "SKHYNIX", "NAVER", "KAKAO", "HYUNDAI", "LGENERGY");
    private static final List<Month> QUARTERLY_EXPIRY_MONTHS = List.of(Month.MARCH, Month.JUNE, Month.SEPTEMBER, Month.DECEMBER);

    @Override
    public List<MarketEventResponse> getEvents(LocalDate from, LocalDate to) {
        List<MarketEventResponse> events = new ArrayList<>();

        for (int year = from.getYear(); year <= to.getYear(); year++) {
            for (Month month : Month.values()) {
                LocalDate koreaExpiry = nthWeekday(year, month, DayOfWeek.THURSDAY, 2);
                addIfInRange(
                        events,
                        koreaExpiry,
                        from,
                        to,
                        QUARTERLY_EXPIRY_MONTHS.contains(month) ? koreaQuarterlyExpiryEvent(koreaExpiry) : koreaMonthlyExpiryEvent(koreaExpiry)
                );

                LocalDate usExpiry = nthWeekday(year, month, DayOfWeek.FRIDAY, 3);
                addIfInRange(
                        events,
                        usExpiry,
                        from,
                        to,
                        QUARTERLY_EXPIRY_MONTHS.contains(month) ? usQuarterlyExpiryEvent(usExpiry) : usMonthlyExpiryEvent(usExpiry)
                );
            }
        }

        return events;
    }

    private void addIfInRange(
            List<MarketEventResponse> events,
            LocalDate date,
            LocalDate from,
            LocalDate to,
            MarketEventResponse event
    ) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
            events.add(event);
        }
    }

    private MarketEventResponse koreaQuarterlyExpiryEvent(LocalDate date) {
        return new MarketEventResponse(
                "rule-kr-expiry-%s".formatted(date),
                "한국 선물·옵션 동시 만기일",
                "수급",
                date,
                "보통",
                "KOSPI200 선물과 옵션 만기가 겹치는 날로, 기관과 외국인의 포지션 정리가 늘어날 수 있습니다.",
                "기업 가치가 갑자기 바뀌는 날이라기보다 큰 자금의 정산 때문에 장중 변동성이 커질 수 있는 날로 보면 됩니다.",
                List.of("전체", "수급"),
                ALL_SYMBOLS
        );
    }

    private MarketEventResponse koreaMonthlyExpiryEvent(LocalDate date) {
        return new MarketEventResponse(
                "rule-kr-option-expiry-%s".formatted(date),
                "한국 옵션 만기일",
                "수급",
                date,
                "낮음",
                "KOSPI200 옵션 만기일로, 장중 수급 변화가 평소보다 커질 수 있습니다.",
                "기업 실적이 바뀌는 이벤트는 아니지만, 단기 거래 물량이 몰리면 가격이 흔들릴 수 있어요.",
                List.of("전체", "수급"),
                ALL_SYMBOLS
        );
    }

    private MarketEventResponse usQuarterlyExpiryEvent(LocalDate date) {
        return new MarketEventResponse(
                "rule-us-expiry-%s".formatted(date),
                "미국 네마녀의 날",
                "수급",
                date,
                "보통",
                "미국 주가지수 선물·옵션 등 주요 파생상품 만기가 겹치는 날입니다.",
                "미국 장 변동성이 커지면 다음 거래일 국내 대형주와 성장주 심리에도 영향을 줄 수 있어요.",
                List.of("전체", "미국시장", "수급"),
                ALL_SYMBOLS
        );
    }

    private MarketEventResponse usMonthlyExpiryEvent(LocalDate date) {
        return new MarketEventResponse(
                "rule-us-option-expiry-%s".formatted(date),
                "미국 옵션 만기일",
                "수급",
                date,
                "낮음",
                "미국 주식·지수 옵션 만기일로, 주요 종목과 지수의 단기 수급이 달라질 수 있습니다.",
                "미국 장 변동성이 커지면 다음 거래일 국내 대형주 심리에도 영향을 줄 수 있어 참고 일정으로 보면 됩니다.",
                List.of("전체", "미국시장", "수급"),
                ALL_SYMBOLS
        );
    }

    private LocalDate nthWeekday(int year, Month month, DayOfWeek dayOfWeek, int occurrence) {
        LocalDate date = LocalDate.of(year, month, 1);
        int matched = 0;
        while (date.getMonth() == month) {
            if (date.getDayOfWeek() == dayOfWeek) {
                matched++;
                if (matched == occurrence) {
                    return date;
                }
            }
            date = date.plusDays(1);
        }
        throw new IllegalStateException("Could not calculate expiry date.");
    }
}
