package com.axioma.loyalty.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class ClientIpTest {

    @Test
    void usesFirstForwardedAddress() {
        var request = MockServerHttpRequest.post("/").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1").build();
        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void acceptsIpv6() {
        var request = MockServerHttpRequest.post("/").header("X-Forwarded-For", "2001:db8::1").build();
        assertThat(ClientIp.of(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void fallsBackToRemoteAddress() {
        var request = MockServerHttpRequest.post("/").remoteAddress(new InetSocketAddress("198.51.100.4", 5555)).build();
        assertThat(ClientIp.of(request)).isEqualTo("198.51.100.4");
    }

    @Test
    void ignoresValuesThatAreNotAnIp() {
        var request = MockServerHttpRequest.post("/").header("X-Forwarded-For", "<script>, 10.0.0.1").build();
        assertThat(ClientIp.of(request)).isNull();
        assertThat(ClientIp.of(MockServerHttpRequest.post("/").build())).isNull();
    }
}
