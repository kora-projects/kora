package ru.tinkoff.kora.http.client.common;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.http.client.common.request.DefaultHttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.logging.common.MDC;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;

import static java.time.Duration.ofMillis;

@TestMethodOrder(MethodOrderer.Random.class)
@ExtendWith(HttpClientTestBase.MdcInterceptor.class)
public abstract class HttpClientTestBase {
    public static class MdcInterceptor implements InvocationInterceptor {
        private final MDC mdc = new MDC();

        @Override
        public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            ScopedValue.where(MDC.VALUE, mdc).call(invocation::proceed);
        }

        @Override
        public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            ScopedValue.where(MDC.VALUE, mdc).call(invocation::proceed);
        }

        @Override
        public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            ScopedValue.where(MDC.VALUE, mdc).call(invocation::proceed);
        }

        @Override
        public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            ScopedValue.where(MDC.VALUE, mdc).call(invocation::proceed);
        }

        @Override
        public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            ScopedValue.where(MDC.VALUE, mdc).call(invocation::proceed);
        }
    }

    protected static final LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    protected final ClientAndServer server = ClientAndServer.startClientAndServer(0);

    private final HttpClient baseClient = this.createClient(new $HttpClientConfig_ConfigValueExtractor.HttpClientConfig_Impl(ofMillis(100), ofMillis(500000), null, false));
    private final HttpClient client = this.baseClient
        .with((chain, request) -> chain.process(new DefaultHttpClientRequest(
            request.method(),
            URI.create("http://localhost:" + server.getPort() + request.uri().toString()),
            request.uriTemplate(),
            request.headers(),
            request.body(),
            request.requestTimeout()
        )));

    protected abstract HttpClient createClient(HttpClientConfig config);

    @BeforeEach
    void setUp() throws Exception {
        ctx.getLogger("ROOT").setLevel(Level.OFF);
        ctx.getLogger("ru.tinkoff.kora.http.client").setLevel(Level.ALL);
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.init();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.baseClient instanceof Lifecycle lifecycle) {
            lifecycle.release();
        }
        server.stop();
    }

    protected ResponseWithBody call(HttpClient client, HttpClientRequest request) {
        try (var response = client.execute(request);
             var body = response.body();
             var is = body.asInputStream()) {
            return new ResponseWithBody(response, is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ResponseWithBody call(HttpClientRequest request) {
        try (var response = client.execute(request);
             var body = response.body();
             var is = body.asInputStream()) {
            return new ResponseWithBody(response, is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
