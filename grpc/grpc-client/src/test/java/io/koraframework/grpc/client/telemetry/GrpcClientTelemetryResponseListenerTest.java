package io.koraframework.grpc.client.telemetry;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.client.telemetry.impl.NoopGrpcClientObservation;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcClientTelemetryResponseListenerTest {

    @Test
    void forwardsAllCallbacksToDelegate() {
        var events = new ArrayList<String>();
        var delegate = new ClientCall.Listener<String>() {
            @Override
            public void onHeaders(Metadata headers) {events.add("headers");}

            @Override
            public void onMessage(String message) {events.add("message:" + message);}

            @Override
            public void onClose(Status status, Metadata trailers) {events.add("close:" + status.getCode());}

            @Override
            public void onReady() {events.add("ready");}
        };
        var listener = new GrpcClientTelemetryResponseListener<>(Context.root(), NoopGrpcClientObservation.INSTANCE, delegate);

        listener.onReady();
        listener.onHeaders(new Metadata());
        listener.onMessage("m");
        listener.onClose(Status.OK, new Metadata());

        assertThat(events).isEqualTo(List.of("ready", "headers", "message:m", "close:OK"));
    }
}
