package com.portfolio_management.portfolio.investments.stock.service;

import com.portfolio_management.portfolio.investments.stock.repository.StockRepository;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLiveSubscriptionServiceUnitTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private FinnhubWebSocketClient finnhubWebSocketClient;

    private StockLiveSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new StockLiveSubscriptionService(stockRepository, finnhubWebSocketClient);
    }

    @Test
    void refreshSubscriptions_subscribesAllActiveSymbols() {
        when(stockRepository.findAllActiveSymbols()).thenReturn(List.of("AAPL", "MSFT"));

        service.refreshSubscriptions();

        verify(finnhubWebSocketClient, times(1)).subscribeSymbols(List.of("AAPL", "MSFT"));
    }

    @Test
    void refreshSubscriptions_callsRepositoryOnce() {
        when(stockRepository.findAllActiveSymbols()).thenReturn(List.of());

        service.refreshSubscriptions();

        verify(stockRepository, times(1)).findAllActiveSymbols();
    }

    @Test
    void refreshSubscriptions_stillCallsSubscribe_whenNoSymbols() {
        when(stockRepository.findAllActiveSymbols()).thenReturn(List.of());

        service.refreshSubscriptions();

        verify(finnhubWebSocketClient, times(1)).subscribeSymbols(List.of());
    }
}

