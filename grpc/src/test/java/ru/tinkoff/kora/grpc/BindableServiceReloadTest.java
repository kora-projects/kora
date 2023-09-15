package ru.tinkoff.kora.grpc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.grpc.ManagedChannelBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.grpc.app.Application;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.concurrent.atomic.AtomicReference;

public class BindableServiceReloadTest {
    static {
        if (LoggerFactory.getLogger("ru.tinkoff.kora") instanceof Logger log) {
            log.setLevel(Level.TRACE);
        }
    }

    @Test
    public void bindableServiceReloadTest() throws Exception {
        var cl = TestUtils.annotationProcess(Application.class, new KoraAppProcessor());
        var graphObject = cl.loadClass(Application.class.getCanonicalName() + "Impl").getConstructors()[0].newInstance();
        var graph = (ApplicationGraphDraw) cl.loadClass(Application.class.getCanonicalName() + "Graph").getMethod("graph").invoke(graphObject);
        var refreshableGraph = graph.init();
        var channel = ManagedChannelBuilder.forAddress("localhost", 8090).usePlaintext().build();

        try {
            var stub = EventsGrpc.newBlockingStub(channel);

            Assertions
                .assertThat(stub.sendEvent(SendEventRequest.newBuilder().setEvent("foo").build()).getRes())
                .isEqualTo("res1");

            var ref = (AtomicReference<String>) graph.getNodes().stream()
                .map(refreshableGraph::get)
                .filter((n) -> n instanceof AtomicReference)
                .findFirst()
                .get();

            ref.set("res2");
            var resNode = graph.getNodes().stream()
                .filter((n) -> refreshableGraph.get(n) instanceof String)
                .findFirst()
                .get();

            refreshableGraph.refresh(resNode);

            Assertions
                .assertThat(stub.sendEvent(SendEventRequest.newBuilder().setEvent("foo").build()).getRes())
                .isEqualTo("res2");

        } finally {
            channel.shutdown();
            refreshableGraph.release();
        }
    }
}
