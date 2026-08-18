package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.StockCandleResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableStockCandleClient implements StockCandleClient {

    @Override
    public List<StockCandleResponse> getDailyCandles(String symbol, LocalDate asOf, int months) {
        return List.of();
    }
}
