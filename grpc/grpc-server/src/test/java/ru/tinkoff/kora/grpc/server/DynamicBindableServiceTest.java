package ru.tinkoff.kora.grpc.server;

import io.grpc.*;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.grpc.server.app.EventService;
import ru.tinkoff.kora.grpc.server.events.SendEventRequest;
import ru.tinkoff.kora.grpc.server.events.SendEventResponse;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicBindableServiceTest {
    @Test
    void testReload() {
        var ref = new AtomicReference<BindableService>();
        ref.set(new EventService("test1"));
        var bindableService = new DynamicBindableService(new ValueOf<BindableService>() {
            @Override
            public BindableService get() {
                return ref.get();
            }

            @Override
            public void refresh() {

            }
        });

        var service = bindableService.bindService();
        var request = SendEventRequest.getDefaultInstance();
        assertThat(this.<SendEventRequest, SendEventResponse>call(service, "Events/sendEvent", request).getRes()).isEqualTo("test1");

        ref.set(new EventService("test2"));
        bindableService.graphRefreshed();
        assertThat(this.<SendEventRequest, SendEventResponse>call(service, "Events/sendEvent", request).getRes()).isEqualTo("test2");
    }

    private <Request, Response> Response call(ServerServiceDefinition service, String methodName, Request request) {
        @SuppressWarnings("unchecked")
        var method = (ServerMethodDefinition<Request, Response>) service.getMethod(methodName);
        var methodDescriptor = service.getServiceDescriptor().getMethods().iterator().next();
        var q = new ArrayBlockingQueue<Response>(2);
        var listener = method.getServerCallHandler().startCall(new ServerCall<Request, Response>() {
            @Override
            public void request(int numMessages) {

            }

            @Override
            public void sendHeaders(Metadata headers) {

            }

            @Override
            public void sendMessage(Response message) {
                q.add(message);
            }

            @Override
            public void close(Status status, Metadata trailers) {

            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            @SuppressWarnings("unchecked")
            public MethodDescriptor<Request, Response> getMethodDescriptor() {
                return (MethodDescriptor<Request, Response>) methodDescriptor;
            }
        }, new Metadata());
        listener.onMessage(request);
        listener.onHalfClose();
        listener.onComplete();

        try {
            return q.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
