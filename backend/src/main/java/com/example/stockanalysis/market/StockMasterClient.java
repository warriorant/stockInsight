package com.example.stockanalysis.market;

import com.example.stockanalysis.service.StockDefinition;
import java.util.Map;

public interface StockMasterClient {

    Map<String, StockDefinition> getKospiStocks(Map<String, StockDefinition> fallbackStocks);
}
