package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockLiveSubscriptionService {

    private final StockRepository stockRepository;
    private final FinnhubWebSocketClient finnhubWebSocketClient;

    public StockLiveSubscriptionService(StockRepository stockRepository, FinnhubWebSocketClient finnhubWebSocketClient) {
        this.stockRepository = stockRepository;
        this.finnhubWebSocketClient = finnhubWebSocketClient;
    }

    @Scheduled(fixedDelayString = "${stock.live.subscription-refresh-ms:60000}")
    public void refreshSubscriptions() {
        List<String> symbols = stockRepository.findAllActiveSymbols();
        finnhubWebSocketClient.subscribeSymbols(symbols);
    }
}

