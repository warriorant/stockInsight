package com.example.stockanalysis.market;

import com.example.stockanalysis.dto.MarketEventResponse;
import java.time.LocalDate;
import java.util.List;

public interface MarketEventClient {

    List<MarketEventResponse> getEvents(LocalDate from, LocalDate to);
}
