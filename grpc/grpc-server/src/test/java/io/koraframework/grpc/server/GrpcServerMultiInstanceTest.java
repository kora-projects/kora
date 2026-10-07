package io.koraframework.grpc.server;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.grpc.server.app.TwoGrpcServersApplication;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcServerMultiInstanceTest {

    @Test
    @SuppressWarnings("unchecked")
    void taggedFactoryModuleStartsSecondServerWithDefaultTelemetryFactory() throws Exception {
        // the graph class is generated in the last processing round, so it can't be referenced directly
        var graphClass = Class.forName(TwoGrpcServersApplication.class.getName() + "Graph");
        var draw = ((Supplier<ApplicationGraphDraw>) graphClass.getConstructor().newInstance()).get();
        assertThat(draw.findNodesByType(GrpcServer.class, null)).hasSize(1);
        assertThat(draw.findNodesByType(GrpcServer.class, TwoGrpcServersApplication.Admin.class)).hasSize(1);

        var graph = draw.init();
        graph.release();
    }
}
