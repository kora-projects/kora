package ru.tinkoff.kora.soap.client.annotation.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sun.net.httpserver.HttpServer;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.http_jetty.JettyHTTPDestination;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.asynchttpclient.Dsl;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.http.client.async.AsyncHttpClient;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.telemetry.DefaultHttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.Sl4fjHttpClientLogger;
import ru.tinkoff.kora.soap.client.common.SoapServiceConfig;
import ru.tinkoff.kora.soap.client.common.telemetry.DefaultSoapClientTelemetryFactory;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetryFactory;

import javax.xml.ws.Endpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebServiceClientAnnotationProcessorTest {
    private final AsyncHttpClient httpClient = new AsyncHttpClient(Dsl.asyncHttpClient());
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebServiceClientAnnotationProcessorTest.class);

    @AfterEach
    void afterEach() {
        httpClient.release().block();
    }

    @Test
    void testGenerate() throws Exception {
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-jakarta-simple-service/"), new WebServiceClientAnnotationProcessor());
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-javax-simple-service/"), new WebServiceClientAnnotationProcessor());
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-jakarta-service-with-multipart-response/"), new WebServiceClientAnnotationProcessor());
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-javax-service-with-multipart-response/"), new WebServiceClientAnnotationProcessor());
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-jakarta-service-with-rpc/"), new WebServiceClientAnnotationProcessor());
        TestUtils.annotationProcessFiles(files("build/generated/wsdl-javax-service-with-rpc/"), new WebServiceClientAnnotationProcessor());
    }

    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.INFO);
        }
        if (log instanceof Logger log) {
            log.setLevel(Level.OFF);
        }
    }

    @Test
    void testCxfServer() throws Throwable {
        var cl = TestUtils.annotationProcessFiles(files("build/generated/wsdl-javax-simple-service/"), new WebServiceClientAnnotationProcessor());
        var serviceClass = cl.loadClass("ru.tinkoff.kora.simple.service.SimpleService");
        enum RsKind {SUCCESS, FAILURE1, FAILURE2}
        var rsKind = new AtomicReference<RsKind>(RsKind.SUCCESS);
        var invocationHandler = (InvocationHandler) Proxy.newProxyInstance(cl, new Class<?>[]{serviceClass, InvocationHandler.class}, (proxy, method, args) -> switch (rsKind.get()) {
            case SUCCESS -> {
                var i = instance(cl, "ru.tinkoff.kora.simple.service.TestResponse");
                set(i, "val1", "test");
                yield i;
            }
            case FAILURE1 -> {
                var error = instance(cl, "ru.tinkoff.kora.simple.service.TestError1");
                set(error, "val1", "test");
                throw (Exception) instance(cl, "ru.tinkoff.kora.simple.service.TestError1Msg", "error", error);
            }
            case FAILURE2 -> {
                var error = instance(cl, "ru.tinkoff.kora.simple.service.TestError2");
                set(error, "val1", "test");
                throw (Exception) instance(cl, "ru.tinkoff.kora.simple.service.TestError2Msg", "error", error);
            }
        });
        var server = Proxy.newProxyInstance(cl, new Class<?>[]{serviceClass}, invocationHandler);
        var endpoint = new EndpointImpl(server);
        endpoint.publish("http://localhost:0/test");

        try (endpoint) {
            var port = this.getEndpointPort(endpoint);
            var client = createClient(cl, "ru.tinkoff.kora.simple.service.$SimpleService_SoapClientImpl", "http://localhost:" + port + "/test");
            var request = instance(cl, "ru.tinkoff.kora.simple.service.TestRequest");
            set(request, "val1", "test1");
            set(request, "val2", "test2");
            var responseType = cl.loadClass("ru.tinkoff.kora.simple.service.TestResponse");

            var response = invoke(client, "test", responseType, request);
            assertThat(response)
                .hasFieldOrPropertyWithValue("val1", "test");

            var monoResponse = (Mono<?>) invoke(client, "testReactive", Mono.class, request);
            var monoResolvedResponse = monoResponse.block();
            assertThat(monoResolvedResponse)
                .hasFieldOrPropertyWithValue("val1", "test");


            rsKind.set(RsKind.FAILURE1);
            assertThatThrownBy(() -> invoke(client, "test", responseType, request))
                .isInstanceOf(cl.loadClass("ru.tinkoff.kora.simple.service.TestError1Msg"));
            assertThatThrownBy(() -> ((Mono<?>) invoke(client, "testReactive", Mono.class, request)).block())
                .extracting(Exceptions::unwrap, InstanceOfAssertFactories.throwable(Exception.class))
                .isInstanceOf(cl.loadClass("ru.tinkoff.kora.simple.service.TestError1Msg"));

            rsKind.set(RsKind.FAILURE2);
            assertThatThrownBy(() -> invoke(client, "test", responseType, request))
                .isInstanceOf(cl.loadClass("ru.tinkoff.kora.simple.service.TestError2Msg"));
            assertThatThrownBy(() -> ((Mono<?>) invoke(client, "testReactive", Mono.class, request)).block())
                .extracting(Exceptions::unwrap, InstanceOfAssertFactories.throwable(Exception.class))
                .isInstanceOf(cl.loadClass("ru.tinkoff.kora.simple.service.TestError2Msg"));
        }
    }

    private int getEndpointPort(Endpoint endpoint) {
        var destination = (JettyHTTPDestination) ((EndpointImpl) endpoint).getServer().getDestination();
        var engine = (JettyHTTPServerEngine) destination.getEngine();
        var connector = (ServerConnector) engine.getConnector();
        return connector.getLocalPort();
    }

    @Test
    void testMultipartResponse() throws Throwable {
        var cl = TestUtils.annotationProcessFiles(files("build/generated/wsdl-jakarta-service-with-multipart-response"), new WebServiceClientAnnotationProcessor());
        var httpServer = HttpServer.create(new InetSocketAddress(0), 0);

        var b = new ByteArrayOutputStream();
        var w = new OutputStreamWriter(b, StandardCharsets.UTF_8);
        w.write("--uuid:503a0c8a-82a4-4c6d-843e-5c3c1389048c\r\n");
        w.write("Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\"\r\n");
        w.write("Content-ID: <root.message@cxf.apache.org>\r\n");
        w.write("\r\n");
        w.write("""
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <ns2:TestResponse xmlns:ns2="http://kora.tinkoff.ru/service/with/multipart" xmlns:xop="http://www.w3.org/2004/08/xop/include">
                        <content><xop:Include href="cid:a8a4ff6c-bc63-41d5-a729-64ef9edd06a0-1472185"/></content>
                    </ns2:TestResponse>
                </soap:Body>
            </soap:Envelope>
            """);
        w.write("\r\n");
        w.write("--uuid:503a0c8a-82a4-4c6d-843e-5c3c1389048c\r\n");
        w.write("Content-Type: application/octet-stream\r\n");
        w.write("Content-Transfer-Encoding: binary\r\n");
        w.write("Content-ID: <a8a4ff6c-bc63-41d5-a729-64ef9edd06a0-1472185>\r\n");
        w.write("\r\n");
        w.write("some-binary-data\r\n");
        w.write("--uuid:503a0c8a-82a4-4c6d-843e-5c3c1389048c--\r\n");
        w.flush();
        w.close();
        var bytes = b.toByteArray();
        httpServer.createContext("/test", exchange -> {
            exchange.getResponseHeaders().add("content-type", "multipart/related; type=\"application/xop+xml\"; boundary=\"uuid:503a0c8a-82a4-4c6d-843e-5c3c1389048c\"; " +
                "start=\"<root.message@cxf.apache.org>\"; start-info=\"text/xml\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        httpServer.start();

        var client = createClient(cl, "ru.tinkoff.kora.service.with.multipart.$ServiceWithMultipart_SoapClientImpl", "http://localhost:" + httpServer.getAddress().getPort() + "/test");
        var request = instance(cl, "ru.tinkoff.kora.service.with.multipart.TestRequest");
        var responseType = cl.loadClass("ru.tinkoff.kora.service.with.multipart.TestResponse");

        var response = invoke(client, "test", responseType, request);
        assertThat(response)
            .extracting("content")
            .isNotNull()
            .isExactlyInstanceOf(byte[].class)
            .asInstanceOf(new InstanceOfAssertFactory<>(byte[].class, Assertions::assertThat))
            .asString().isEqualTo("some-binary-data");

        httpServer.stop(1);
    }

    @Test
    void testRcpResponse() throws Throwable {
        var cl = TestUtils.annotationProcessFiles(files("build/generated/wsdl-javax-service-with-rpc/"), new WebServiceClientAnnotationProcessor());
        var serviceClass = cl.loadClass("ru.tinkoff.kora.service.with.rpc.ServiceWithRpc");
        var invocationHandler = (InvocationHandler) Proxy.newProxyInstance(cl, new Class<?>[]{serviceClass, InvocationHandler.class}, (proxy, method, args) -> {
            args = (Object[]) args[2];
            assertThat(args[0]).isEqualTo("test");
            ((javax.xml.ws.Holder) args[1]).value = "rs1";
            ((javax.xml.ws.Holder) args[2]).value = "rs2";
            return null;
        });
        var server = Proxy.newProxyInstance(cl, new Class<?>[]{serviceClass}, invocationHandler);

        try (var endpoint = new EndpointImpl(server)) {
            endpoint.publish("http://localhost:0/test");
            var port = this.getEndpointPort(endpoint);
            var client = createClient(cl, "ru.tinkoff.kora.service.with.rpc.$ServiceWithRpc_SoapClientImpl", "http://localhost:" + port + "/test");
            var arg2 = new javax.xml.ws.Holder<>();
            var arg3 = new javax.xml.ws.Holder<>();

            invoke(client, "test", void.class, "test", arg2, arg3);
            assertThat(arg2.value).isEqualTo("rs1");
            assertThat(arg3.value).isEqualTo("rs2");
        }

    }

    private Object instance(ClassLoader cl, String type, Object... args) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        var instanceClass = cl.loadClass(type);
        var argTypes = Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new);
        var constructor = MethodHandles.publicLookup().findConstructor(instanceClass, MethodType.methodType(void.class, argTypes));
        try {
            return constructor.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private Object invoke(Object object, String methodName, Class<?> returnType, Object... args) throws Throwable {
        var objectType = object.getClass();
        var argTypes = Arrays.stream(args).<Class<?>>map(Object::getClass).toList();
        if (Mono.class.isAssignableFrom(objectType)) {
            objectType = Mono.class;
        }
        var mt = MethodType.methodType(returnType, argTypes);
        return MethodHandles.lookup()
            .findVirtual(objectType, methodName, mt)
            .bindTo(object)
            .invokeWithArguments(args);
    }

    private void set(Object object, String fieldName, Object value) throws IllegalAccessException {
        for (var f : object.getClass().getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                f.setAccessible(true);
                f.set(object, value);
                return;
            }
        }
        throw new RuntimeException("Field was not found: " + fieldName);
    }

    private Object get(Object object, String fieldName) throws IllegalAccessException {
        for (var f : object.getClass().getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                f.setAccessible(true);
                return f.get(object);
            }
        }
        throw new RuntimeException("Field was not found: " + fieldName);
    }

    private Object createClient(ClassLoader cl, String className, String url) throws Exception {
        var type = cl.loadClass(className);
        var constructor = type.getConstructor(HttpClient.class, SoapClientTelemetryFactory.class, SoapServiceConfig.class);
        var httpClient = this.httpClient.with(new TelemetryInterceptor(new DefaultHttpClientTelemetry(null, null, new Sl4fjHttpClientLogger(log, log))));
        var telemetry = new DefaultSoapClientTelemetryFactory(null);
        return constructor.newInstance(httpClient, telemetry, new SoapServiceConfig(url, Duration.ofSeconds(60)));
    }

    private List<String> files(String path) {
        try (var s = Files.walk(Paths.get(path))) {
            return s.filter(p -> p.getFileName().toString().endsWith(".java"))
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
