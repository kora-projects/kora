package io.koraframework.grpc.server.telemetry;

import io.grpc.*;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.koraframework.common.util.Size;
import io.koraframework.grpc.server.GrpcServer;
import io.koraframework.grpc.server.GrpcServerConfig;
import io.koraframework.grpc.server.GrpcServerFactoryModule;
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
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcServerTelemetryCallListenerTest {

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

    private final Logger grpcLogger = Logger.getLogger("io.grpc");
    private final List<Throwable> grpcLogged = new CopyOnWriteArrayList<>();
    private final Handler grpcLogHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getThrown() != null) {
                grpcLogged.add(record.getThrown());
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    {
        this.grpcLogger.addHandler(this.grpcLogHandler);
    }

    private GrpcServer server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        this.grpcLogger.removeHandler(this.grpcLogHandler);
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
        if (this.server != null) {
            this.server.release();
        }
    }

    @Test
    void handlerThrowsStatusRuntimeException() throws Exception {
        var stub = start(new TelemetryTestServiceGrpc.TelemetryTestServiceImplBase() {
            @Override
            public void unary(TelemetryTestRequest request, StreamObserver<TelemetryTestResponse> responseObserver) {
                throw Status.NOT_FOUND.withDescription("no such entity").asRuntimeException();
            }
        });

        assertThatThrownBy(() -> stub.unary(TelemetryTestRequest.getDefaultInstance()))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                assertThat(e.getStatus().getCode()).as(e.getStatus().toString()).isEqualTo(Status.Code.NOT_FOUND);
                assertThat(e.getStatus().getDescription()).isEqualTo("no such entity");
            });

        var o = awaitEnded();
        assertThat(o.status.getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(o.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(o.endCount).hasValue(1);
    }

    @Test
    void handlerThrowsRuntimeException() throws Exception {
        var stub = start(new TelemetryTestServiceGrpc.TelemetryTestServiceImplBase() {
            @Override
            public void unary(TelemetryTestRequest request, StreamObserver<TelemetryTestResponse> responseObserver) {
                throw new IllegalStateException("boom");
            }
        });

        assertThatThrownBy(() -> stub.unary(TelemetryTestRequest.getDefaultInstance()))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNKNOWN));

        var o = awaitEnded();
        assertThat(o.status.getCode()).isEqualTo(Status.Code.UNKNOWN);
        assertThat(o.error).isInstanceOf(IllegalStateException.class);
        assertThat(o.endCount).hasValue(1);
        // the exception is rethrown, so grpc-java still logs it
        for (int i = 0; i < 50 && this.grpcLogged.isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertThat(this.grpcLogged).anySatisfy(t -> assertThat(t).isInstanceOf(IllegalStateException.class).hasMessage("boom"));
    }

    @Test
    void handlerClosesWithErrorThenThrows() throws Exception {
        var stub = start(new TelemetryTestServiceGrpc.TelemetryTestServiceImplBase() {
            @Override
            public void unary(TelemetryTestRequest request, StreamObserver<TelemetryTestResponse> responseObserver) {
                responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                throw new IllegalStateException("boom");
            }
        });

        assertThatThrownBy(() -> stub.unary(TelemetryTestRequest.getDefaultInstance()))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));

        var o = awaitEnded();
        Thread.sleep(200);
        assertThat(o.status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(o.endCount).hasValue(1);
    }

    @Test
    void clientCancelsServerStream() throws Exception {
        var handlerSawCancel = new CountDownLatch(1);
        var stub = start(new TelemetryTestServiceGrpc.TelemetryTestServiceImplBase() {
            @Override
            public void serverStream(TelemetryTestRequest request, StreamObserver<TelemetryTestResponse> responseObserver) {
                var observer = (ServerCallStreamObserver<TelemetryTestResponse>) responseObserver;
                observer.setOnCancelHandler(handlerSawCancel::countDown);
                observer.onNext(TelemetryTestResponse.getDefaultInstance());
                // never completed: ends only when the client cancels
            }
        });

        var ctx = Context.current().withCancellation();
        try {
            ctx.run(() -> stub.serverStream(TelemetryTestRequest.getDefaultInstance()).next());
        } finally {
            ctx.cancel(null);
        }

        assertThat(handlerSawCancel.await(5, TimeUnit.SECONDS)).isTrue();
        var o = awaitEnded();
        assertThat(o.status.getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(o.endCount).hasValue(1);
    }

    private TestObservation awaitEnded() throws InterruptedException {
        var o = this.observations.poll(5, TimeUnit.SECONDS);
        assertThat(o).isNotNull();
        assertThat(o.ended.await(5, TimeUnit.SECONDS)).as("observation must be ended, events=%s", o.events).isTrue();
        return o;
    }

    private TelemetryTestServiceGrpc.TelemetryTestServiceBlockingStub start(BindableService service) throws Exception {
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
        return TelemetryTestServiceGrpc.newBlockingStub(this.channel);
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
