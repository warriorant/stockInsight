package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.StockCandleResponse;
import java.time.LocalDate;
import java.util.List;

public interface StockCandleClient {

    List<StockCandleResponse> getDailyCandles(String symbol, LocalDate asOf, int months);
}
