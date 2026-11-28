package io.koraframework.http.client.common;

import ch.qos.logback.classic.*;
import io.koraframework.application.graph.*;
import io.koraframework.common.context.KoraContext;
import io.koraframework.http.client.common.request.*;
import io.koraframework.logging.common.MDC;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockserver.configuration.*;
import org.mockserver.integration.*;
import org.slf4j.*;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;

import static java.time.Duration.*;

@TestMethodOrder(MethodOrderer.Random.class)
@ExtendWith(HttpClientTestBase.MdcInterceptor.class)
public abstract class HttpClientTestBase {
    public static class MdcInterceptor implements InvocationInterceptor {
        private final MDC mdc = new MDC();

        private void runInContext(Invocation<Void> invocation) throws Throwable {
            var context = KoraContext.empty()
                .with(MDC.MDC_KEY, mdc);
            try {
                KoraContext.runWith(context, () -> {
                    try {
                        invocation.proceed();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (RuntimeException e) {
                if (e.getCause() != null) throw e.getCause();
                throw e;
            }
        }

        @Override
        public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            runInContext(invocation);
        }

        @Override
        public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            runInContext(invocation);
        }

        @Override
        public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            runInContext(invocation);
        }

        @Override
        public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            runInContext(invocation);
        }

        @Override
        public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
            runInContext(invocation);
        }
    }

    protected static final LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

    static {
        ConfigurationProperties.maxRequestBodySize(64 * 1024 * 1024);
    }

    protected final ClientAndServer server = ClientAndServer.startClientAndServer(0);

    private final HttpClient baseClient = this.createClient(new $HttpClientConfig_ConfigValueMapper.HttpClientConfig_Impl(ofMillis(100), ofMillis(500000), null, false));
    private final HttpClient client = this.baseClient
        .with((chain, request) -> chain.process(HttpClientRequest.of(
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
        ctx.getLogger("io.koraframework.http.client").setLevel(Level.ALL);
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
