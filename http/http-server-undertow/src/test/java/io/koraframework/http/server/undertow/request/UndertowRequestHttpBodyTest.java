package io.koraframework.http.server.undertow.request;

import io.undertow.server.HttpServerExchange;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UndertowRequestHttpBodyTest {

    @Test
    void startsBlockingModeWhenInputStreamIsRequested() {
        var exchange = mock(HttpServerExchange.class);
        var inputStream = mock(InputStream.class);
        when(exchange.getInputStream()).thenReturn(inputStream);

        var body = new UndertowRequestHttpBody(exchange);

        assertThat(body.asInputStream()).isSameAs(inputStream);
        verify(exchange).startBlocking();
    }

    @Test
    void doesNotStartBlockingModeUntilBodyIsUsed() {
        var exchange = mock(HttpServerExchange.class);

        new UndertowRequestHttpBody(exchange);

        verify(exchange, never()).startBlocking();
        verify(exchange, never()).getInputStream();
    }

    @Test
    void closesOpenedInputStream() throws Exception {
        var exchange = mock(HttpServerExchange.class);
        var inputStream = mock(InputStream.class);
        when(exchange.isBlocking()).thenReturn(true);
        when(exchange.getInputStream()).thenReturn(inputStream);

        new UndertowRequestHttpBody(exchange).close();

        verify(inputStream).close();
    }

    @Test
    void startsBlockingModeWhenUnopenedBodyIsClosed() throws Exception {
        var exchange = mock(HttpServerExchange.class);
        var inputStream = mock(InputStream.class);
        when(exchange.getInputStream()).thenReturn(inputStream);

        new UndertowRequestHttpBody(exchange).close();

        verify(exchange).startBlocking();
        verify(inputStream).close();
    }
}
