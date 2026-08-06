package com.portfolio_management.portfolio.investments.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portfolio_management.portfolio.investments.stock.client.FinnhubRestClient;
import com.portfolio_management.portfolio.investments.stock.dto.StockNewsItemResponse;
import com.portfolio_management.portfolio.investments.stock.websocket.FinnhubWebSocketClient;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockMarketServiceTest {

    private final FinnhubRestClient finnhubRestClient = mock(FinnhubRestClient.class);
    private final FinnhubWebSocketClient finnhubWebSocketClient = mock(FinnhubWebSocketClient.class);
    private final StockMarketService stockMarketService = new StockMarketService(finnhubRestClient, finnhubWebSocketClient);

    @Test
    void getNewsMapsFinnhubNewsItemsToResponseDto() {
        when(finnhubRestClient.getCompanyNews("AAPL", 7, 3)).thenReturn(List.of(
                new FinnhubRestClient.FinnhubNewsItem(
                        "AAPL",
                        "Apple launches portfolio feature",
                        "MockWire",
                        "https://example.com/aapl-news",
                        "Headline summary",
                        LocalDate.of(2026, 8, 6)
                )
        ));

        List<StockNewsItemResponse> news = stockMarketService.getNews("aapl", 3);

        assertThat(news).hasSize(1);
        assertThat(news.getFirst().symbol()).isEqualTo("AAPL");
        assertThat(news.getFirst().headline()).contains("Apple launches");
        assertThat(news.getFirst().source()).isEqualTo("MockWire");
        assertThat(news.getFirst().publishedDate()).isEqualTo(LocalDate.of(2026, 8, 6));
    }
}

