package com.expensetracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void usesTheSocketAddressWhenNoProxyIsTrusted() {
        assertThat(new ClientIpResolver(0).resolve(request("1.2.3.4"))).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToTheSocketAddressWithoutTheHeader() {
        assertThat(new ClientIpResolver(1).resolve(request(null))).isEqualTo("10.0.0.1");
        assertThat(new ClientIpResolver(1).resolve(request("  "))).isEqualTo("10.0.0.1");
    }

    @Test
    void ignoresClientSuppliedEntriesOnTheLeft() {
        // The client sent "6.6.6.6"; the single trusted proxy appended the address it actually saw.
        assertThat(new ClientIpResolver(1).resolve(request("6.6.6.6, 198.51.100.7"))).isEqualTo("198.51.100.7");
    }

    @Test
    void countsTrustedHopsFromTheRight() {
        assertThat(new ClientIpResolver(2).resolve(request("6.6.6.6, 198.51.100.7, 172.16.0.1")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    void tooFewEntriesFallBackToTheLeftmostAvailable() {
        assertThat(new ClientIpResolver(3).resolve(request("198.51.100.7"))).isEqualTo("198.51.100.7");
    }
}
