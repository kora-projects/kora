package io.koraframework.grpc.server.telemetry;

import io.grpc.*;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.koraframework.common.util.Size;
import io.koraframework.grpc.server.GrpcServer;
import io.koraframework.grpc.server.GrpcServerConfig;
import io.koraframework.grpc.server.GrpcServerFactoryModule;
import io.koraframework.grpc.server.events.EventsGrpc;
import io.koraframework.grpc.server.events.SendEventRequest;
import io.koraframework.grpc.server.events.SendEventResponse;
import io.koraframework.grpc.server.handler.DynamicBindableService;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcServerTelemetryCallTest {

    static final class TestObservation implements GrpcServerObservation {
        final CountDownLatch ended = new CountDownLatch(1);
        final AtomicInteger endCount = new AtomicInteger();
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile @Nullable Status status;
        volatile @Nullable Throwable error;

        @Override
        public void observeHeaders(Metadata headers) {}

        @Override
        public void observeRequest(int numMessages) {}

        @Override
        public void observeSendMessage(Object request) {}

        @Override
        public void observeClose(Status status, Metadata trailers) {
            this.events.add("close:" + status.getCode());
            this.status = status;
        }

        @Override
        public void observeCancel() {
            this.events.add("cancel");
        }

        @Override
        public void observeComplete() {
            this.events.add("complete");
        }

        @Override
        public void observeHalfClosed() {}

        @Override
        public void observeReceiveMessage(Object response) {}

        @Override
        public void observeReady() {}

        @Override
        public void observeStart() {}

        @Override
        public Span span() {
            return Span.getInvalid();
        }

        @Override
        public void end() {
            this.events.add("end");
            this.endCount.incrementAndGet();
            this.ended.countDown();
        }

        @Override
        public void observeError(Throwable e) {
            this.events.add("error");
            this.error = e;
        }
    }

    private final BlockingQueue<TestObservation> observations = new LinkedBlockingQueue<>();
    private final GrpcServerTelemetryFactory telemetryFactory = (name, port, config) -> (call, headers) -> {
        var o = new TestObservation();
        this.observations.add(o);
        return o;
    };

    private GrpcServer server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
        if (this.server != null) {
            this.server.release();
        }
    }

    @Test
    void handlerRespondsAfterDeadlineExceeded() throws Exception {
        // the server sees either its own deadline timer or the client's cancel first, the telemetry must match what the server saw
        var serverStatusAtResponse = new CompletableFuture<Status.@Nullable Code>();
        var stub = start(new EventsGrpc.EventsImplBase() {
            @Override
            public void sendEvent(SendEventRequest request, StreamObserver<SendEventResponse> responseObserver) {
                var observer = (ServerCallStreamObserver<SendEventResponse>) responseObserver;
                var grpcContext = Context.current();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                serverStatusAtResponse.complete(observer.isCancelled() ? Contexts.statusFromCancelled(grpcContext).getCode() : null);
                responseObserver.onNext(SendEventResponse.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });

        assertThatThrownBy(() -> stub.withDeadlineAfter(200, TimeUnit.MILLISECONDS).sendEvent(SendEventRequest.getDefaultInstance()))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
        var serverStatus = serverStatusAtResponse.get(5, TimeUnit.SECONDS);
        assertThat(serverStatus).isIn(Status.Code.DEADLINE_EXCEEDED, Status.Code.CANCELLED);

        var o = awaitEnded();
        Thread.sleep(200);
        assertThat(o.status.getCode()).as("events=%s", o.events).isEqualTo(serverStatus);
        assertThat(o.status.getCause()).as("a cancel is not a server error").isNull();
        assertThat(o.endCount).hasValue(1);
    }

    @Test
    void handlerRespondsFromOwnThreadAfterDeadlineExceeded() throws Exception {
        // the server sees either its own deadline timer or the client's cancel first, the telemetry must match what the server saw
        var serverStatusAtResponse = new CompletableFuture<Status.@Nullable Code>();
        var stub = start(new EventsGrpc.EventsImplBase() {
            @Override
            public void sendEvent(SendEventRequest request, StreamObserver<SendEventResponse> responseObserver) {
                var observer = (ServerCallStreamObserver<SendEventResponse>) responseObserver;
                var grpcContext = Context.current();
                // a plain thread does not carry the gRPC call context
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    serverStatusAtResponse.complete(observer.isCancelled() ? Contexts.statusFromCancelled(grpcContext).getCode() : null);
                    responseObserver.onNext(SendEventResponse.getDefaultInstance());
                    responseObserver.onCompleted();
                }).start();
            }
        });

        assertThatThrownBy(() -> stub.withDeadlineAfter(200, TimeUnit.MILLISECONDS).sendEvent(SendEventRequest.getDefaultInstance()))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
        var serverStatus = serverStatusAtResponse.get(5, TimeUnit.SECONDS);
        assertThat(serverStatus).isIn(Status.Code.DEADLINE_EXCEEDED, Status.Code.CANCELLED);

        var o = awaitEnded();
        Thread.sleep(200);
        assertThat(o.status.getCode()).as("events=%s", o.events).isEqualTo(serverStatus);
        assertThat(o.status.getCause()).as("a cancel is not a server error").isNull();
        assertThat(o.endCount).hasValue(1);
    }

    @Test
    void handlerRespondsAfterClientCancelled() throws Exception {
        var handlerStarted = new CountDownLatch(1);
        var cancelledAtResponse = new CompletableFuture<Boolean>();
        start(new EventsGrpc.EventsImplBase() {
            @Override
            public void sendEvent(SendEventRequest request, StreamObserver<SendEventResponse> responseObserver) {
                var observer = (ServerCallStreamObserver<SendEventResponse>) responseObserver;
                handlerStarted.countDown();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    return;
                }
                cancelledAtResponse.complete(observer.isCancelled());
                responseObserver.onNext(SendEventResponse.getDefaultInstance());
                responseObserver.onCompleted();
            }
        });

        var future = EventsGrpc.newFutureStub(this.channel).sendEvent(SendEventRequest.getDefaultInstance());
        assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        future.cancel(true);
        assertThat(cancelledAtResponse.get(5, TimeUnit.SECONDS)).isTrue();

        var o = awaitEnded();
        Thread.sleep(200);
        assertThat(o.status.getCode()).as("events=%s", o.events).isEqualTo(Status.Code.CANCELLED);
        assertThat(o.status.getCause()).as("a cancel is not a server error").isNull();
        assertThat(o.endCount).hasValue(1);
    }

    private TestObservation awaitEnded() throws InterruptedException {
        var o = this.observations.poll(5, TimeUnit.SECONDS);
        assertThat(o).isNotNull();
        assertThat(o.ended.await(5, TimeUnit.SECONDS)).as("observation must be ended, events=%s", o.events).isTrue();
        return o;
    }

    private EventsGrpc.EventsBlockingStub start(BindableService service) throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var config = config(port);
        var builder = new GrpcServerFactoryModule("test", "grpcServer")
            .grpcServerBuilder(() -> config, List.of(new DynamicBindableService(() -> service)), List.of(), null, null, this.telemetryFactory);
        this.server = new GrpcServer(() -> builder, () -> config);
        this.server.init();
        this.channel = OkHttpChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        return EventsGrpc.newBlockingStub(this.channel);
    }

    private static GrpcServerConfig config(int port) {
        return new GrpcServerConfig() {
            @Override
            public int port() {
                return port;
            }

            @Override
            public Size maxMessageSize() {
                return Size.of(4, Size.Type.MiB);
            }

            @Override
            public Duration shutdownWait() {
                return Duration.ofSeconds(1);
            }

            @Override
            public GrpcServerTelemetryConfig telemetry() {
                return new GrpcServerTelemetryConfig() {
                    @Override
                    public GrpcServerLoggingConfig logging() {
                        return new GrpcServerLoggingConfig() {};
                    }

                    @Override
                    public GrpcServerMetricsConfig metrics() {
                        return new GrpcServerMetricsConfig() {};
                    }

                    @Override
                    public GrpcServerTracingConfig tracing() {
                        return new GrpcServerTracingConfig() {};
                    }
                };
            }

            @Override
            public @Nullable Duration maxConnectionAge() {
                return null;
            }

            @Override
            public @Nullable Duration maxConnectionAgeGrace() {
                return null;
            }

            @Override
            public @Nullable Duration keepAliveTime() {
                return null;
            }

            @Override
            public @Nullable Duration keepAliveTimeout() {
                return null;
            }
        };
    }
}
