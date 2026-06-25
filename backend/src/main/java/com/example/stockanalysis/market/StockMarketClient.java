package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.PricePointResponse;
import com.example.stockanalysis.dto.StockResponse;
import com.example.stockanalysis.service.StockDefinition;
import java.util.List;
import java.util.Optional;

public interface StockMarketClient {

    Optional<StockResponse> getStock(StockDefinition stock);

    Optional<List<PricePointResponse>> getPrices(StockDefinition stock, String range);
}
