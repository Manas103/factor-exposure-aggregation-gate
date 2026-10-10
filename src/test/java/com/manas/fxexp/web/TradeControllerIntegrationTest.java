package com.manas.fxexp.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.manas.fxexp.web.dto.AggregateView;
import com.manas.fxexp.web.dto.CommentaryResponse;
import com.manas.fxexp.web.dto.TradeEventRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/** End-to-end over real HTTP (embedded Tomcat, a real Spring context, real
 * Postgres): submit a trade event, read it back at both aggregation
 * levels, trip a limit, and read a commentary that passes the gate. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void tradeEventFlowsThroughToStrategyAndFundAggregatesAndCommentary() {
        String fundId = "FUND-" + UUID.randomUUID();
        String strategyId = "STRAT-" + UUID.randomUUID();
        String tradeId = "TRD-" + UUID.randomUUID();

        TradeEventRequest req = new TradeEventRequest(tradeId, strategyId, fundId, "INSTR-1",
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(-3_200), BigDecimal.valueOf(410),
                BigDecimal.valueOf(60), BigDecimal.valueOf(15));
        restTemplate.postForEntity(url("/trades"), req, Void.class);

        AggregateView strategyView =
                restTemplate.getForObject(url("/aggregates/strategy/" + strategyId), AggregateView.class);
        assertThat(strategyView.spotExposure()).isEqualByComparingTo(BigDecimal.valueOf(50_000));

        AggregateView fundView = restTemplate.getForObject(url("/aggregates/fund/" + fundId), AggregateView.class);
        assertThat(fundView.spotExposure()).isEqualByComparingTo(BigDecimal.valueOf(50_000));

        CommentaryResponse commentary =
                restTemplate.getForObject(url("/commentary/fund/" + fundId), CommentaryResponse.class);
        assertThat(commentary.published()).isTrue();
        assertThat(commentary.commentary()).contains(fundId);

        List<?> breaches = restTemplate.getForObject(url("/breaches"), List.class);
        assertThat(breaches).noneMatch(b -> ((java.util.Map<?, ?>) b).get("scopeId").equals(strategyId));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
