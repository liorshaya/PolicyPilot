package com.liorshaya.policypilot.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;

/**
 * The test server listens on {@link Api#HOST} alone (day 15, from day 14's finding: a run's sign-ins answered 404 by
 * another listener on the same port). A server on every address would also accept a connection on the IPv6 loopback;
 * this one must refuse it, while the address the clients call answers.
 */
class ApiLoopbackIT extends ApiIntegrationTest {

    @Test
    void theServerAnswersOnTheClientsAddressAndOnNoOther() throws IOException {
        try (Socket ipv4 = new Socket()) {
            ipv4.connect(new InetSocketAddress(Api.HOST, port), 2_000);
            assertThat(ipv4.isConnected()).isTrue();
        }
        Throwable ipv6 = catchThrowable(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("::1", port), 2_000);
            }
        });
        assertThat(ipv6).isInstanceOf(ConnectException.class);
    }
}
