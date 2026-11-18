package com.rednote.gateway.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.reactor.context.SaTokenContextForSpringReactor;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenConfigureTest {

    private SaReactorFilter saReactorFilter;

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenContext(new SaTokenContextForSpringReactor());
        saReactorFilter = new SaTokenConfigure().getSaReactorFilter();
    }

    @AfterEach
    void tearDown() {
        StpUtil.logout(10001L);
    }

    @Test
    void publicAuthEndpointsPassWithoutToken() {
        for (String path : new String[]{"/auth/user/login", "/auth/verification/code/send"}) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainInvoked.set(true);
                return Mono.empty();
            };

            saReactorFilter.filter(exchange, chain).block();

            assertThat(chainInvoked).isTrue();
        }
    }

    @Test
    void protectedEndpointRejectsUnauthenticatedRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/alarm").build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        saReactorFilter.filter(exchange, chain).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":500");
    }

    @Test
    void protectedEndpointAllowsAuthenticatedRequest() {
        StpUtil.login(10001L);
        String token = StpUtil.getTokenValue();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/alarm")
                        .header(StpUtil.getTokenName(), token)
                        .build()
        );
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        saReactorFilter.filter(exchange, chain).block();

        assertThat(chainInvoked).isTrue();
    }
}
