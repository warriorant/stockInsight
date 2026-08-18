package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.FinancialDataResponse;
import com.example.stockanalysis.service.StockDefinition;
import java.util.Optional;

public interface FinancialDataClient {

    Optional<FinancialDataResponse> getFinancials(StockDefinition stock, FinancialDataResponse fallback);
}
