package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.netty.NettySystemHttpServerModule.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.channel.*;
import io.netty.channel.nio.*;
import io.netty.channel.socket.nio.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

class NettyHttpServerRefreshTest {

    private static NettyResources resources;

    @BeforeAll
    static void setup() {
        resources = new NettyResources(
            new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()),
            new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()),
            NioServerSocketChannel.class
        );
    }

    @AfterAll
    static void teardown() {
        if (resources != null) {
            resources.bossGroup().shutdownGracefully();
            resources.workerGroup().shutdownGracefully();
        }
    }

    @Test
    void servesRequestsWithHandlerReplacedByGraphRefresh() throws Exception {
        var config = config();
        var currentRouter = new AtomicReference<HttpServerRouter>();

        var firstHandler = HttpServerRequestHandlerImpl.get("/", _ -> HttpServerResponse.of(418));
        currentRouter.set(new HttpServerRouter(List.of(firstHandler), List.of(), config.get()));

        var routerDelegate = new TestingRouterDelegate(currentRouter, config.get());

        var server = new NettyHttpServer(
            "test",
            resources,
            config,
            routerDelegate,
            () -> new KoraVirtualThreadHandler(config, false),
            null,
            null
        );

        server.init();
        try {
            assertThat(status(server.port())).isEqualTo(418);

            var secondHandler = HttpServerRequestHandlerImpl.get("/", _ -> HttpServerResponse.of(204));
            currentRouter.set(new HttpServerRouter(List.of(secondHandler), List.of(), config.get()));

            assertThat(status(server.port())).isEqualTo(204);
        } finally {
            server.release();
        }
    }

    private static ValueOf<HttpServerConfig> config() {
        var telemetry = $HttpServerConfig_ConfigValueMapper.DEFAULTS.telemetry();
        var config = new HttpServerConfig() {
            @Override
            public int port() {
                return 0;
            }

            @Override
            public HttpServerTelemetryConfig telemetry() {
                return telemetry;
            }
        };
        return () -> config;
    }

    private static int status(int port) throws IOException {
        var connection = (HttpURLConnection) URI.create("http://localhost:" + port + "/").toURL().openConnection();
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static class TestingRouterDelegate extends HttpServerRouter {
        private final AtomicReference<HttpServerRouter> delegate;

        public TestingRouterDelegate(AtomicReference<HttpServerRouter> delegate, HttpServerConfig config) {
            super(List.of(), List.of(), config);
            this.delegate = delegate;
        }

        @Override
        public HttpServerInvocation route(UnroutedHttpRequest unroutedHttpRequest) {
            return delegate.get().route(unroutedHttpRequest);
        }
    }
}
