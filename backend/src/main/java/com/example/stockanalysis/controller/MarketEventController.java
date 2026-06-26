package com.example.stockanalysis.controller;

import com.example.stockanalysis.dto.MarketEventResponse;
import com.example.stockanalysis.service.MarketEventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-events")
public class MarketEventController {

    private final MarketEventService marketEventService;

    public MarketEventController(MarketEventService marketEventService) {
        this.marketEventService = marketEventService;
    }

    @GetMapping
    public List<MarketEventResponse> getUpcomingEvents() {
        return marketEventService.getUpcomingEvents();
    }
}
