package io.koraframework.nats.common;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.Configurer;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.nats.client.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Graph-owned NATS connection. Dependants use native contexts after graph initialization.
 * The native connection exposes dispatchers, services, statistics and all client APIs.
 */
public final class NatsClient implements Lifecycle, ReadinessProbe {
    private final NatsConnectionConfig config;
    private final boolean enabled;
    private final boolean deferredInitialization;
    private final Options options;
    private final JetStreamOptions jetStreamOptions;
    private volatile @Nullable Connection connection;
    private @Nullable CompletableFuture<Connection> connecting;
    private boolean released;

    public NatsClient(NatsConnectionConfig config, @Nullable Configurer<Options.Builder> configurer) {
        this(config, configurer, true, false);
    }

    public NatsClient(NatsConnectionConfig config, @Nullable Configurer<Options.Builder> configurer, boolean enabled) {
        this(config, configurer, enabled, false);
    }

    public NatsClient(NatsConnectionConfig config, @Nullable Configurer<Options.Builder> configurer,
                      boolean enabled, boolean deferredInitialization) {
        this.config = Objects.requireNonNull(config);
        this.enabled = enabled;
        this.deferredInitialization = deferredInitialization;
        if (config.shutdownWait().isNegative() || config.shutdownWait().isZero()) {
            throw new IllegalArgumentException("NATS shutdownWait must be positive");
        }
        var builder = new Options.Builder(config.driverProperties());
        if (configurer != null) {
            builder = Objects.requireNonNull(configurer.configure(builder), "NATS options configurer returned null");
        }
        if (config.driverMetricsEnabled()) {
            builder.turnOnAdvancedStats();
        }
        this.options = builder.build();
        var js = config.jetStreamOptions();
        if (js.domain() != null && js.prefix() != null) {
            throw new IllegalArgumentException("NATS JetStream domain and prefix are mutually exclusive");
        }
        var jsBuilder = JetStreamOptions.builder().requestTimeout(js.requestTimeout());
        if (js.domain() != null) {
            jsBuilder.domain(js.domain());
        }
        if (js.prefix() != null) {
            jsBuilder.prefix(js.prefix());
        }
        this.jetStreamOptions = jsBuilder.build();
    }

    @Override
    public void init() throws IOException, InterruptedException {
        synchronized (this) {
            released = false;
        }
        // Listener workers own initial connection retries and optional readiness waiting.
        if (!deferredInitialization) {
            ensureConnected();
        }
    }

    public void ensureConnected() throws IOException, InterruptedException {
        CompletableFuture<Connection> attempt;
        boolean owner;
        synchronized (this) {
            if (!enabled || connection != null) {
                return;
            }
            if (released) {
                throw new IllegalStateException("NATS client is released");
            }
            owner = connecting == null;
            if (owner) {
                connecting = new CompletableFuture<>();
            }
            attempt = connecting;
        }
        if (!owner) {
            try {
                attempt.get();
                return;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException failure) {
                    throw failure;
                }
                if (e.getCause() instanceof InterruptedException failure) {
                    throw failure;
                }
                if (e.getCause() instanceof RuntimeException failure) {
                    throw failure;
                }
                if (e.getCause() instanceof Error failure) {
                    throw failure;
                }
                throw new IOException("NATS connection failed", e.getCause());
            }
        }
        try {
            var current = Nats.connect(options);
            try {
                synchronized (this) {
                    if (released) {
                        throw new IllegalStateException("NATS client was released while connecting");
                    }
                    connection = current;
                    attempt.complete(current);
                }
            } catch (RuntimeException | Error e) {
                current.close();
                throw e;
            }
        } catch (IOException | InterruptedException | RuntimeException | Error e) {
            attempt.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (this) {
                if (connecting == attempt) {
                    connecting = null;
                }
            }
        }
    }

    public Connection connection() {
        var result = connection;
        if (result == null) {
            throw new IllegalStateException("NATS client is not initialized");
        }
        return result;
    }

    public Options options() {
        return options;
    }

    public JetStreamOptions jetStreamOptions() {
        return jetStreamOptions;
    }

    public JetStream jetStream() throws IOException {
        return connection().jetStream(jetStreamOptions);
    }

    public JetStreamManagement jetStreamManagement() throws IOException {
        return connection().jetStreamManagement(jetStreamOptions);
    }

    public StreamContext stream(String name) throws IOException, JetStreamApiException {
        return connection().getStreamContext(name, jetStreamOptions);
    }

    public ConsumerContext consumer(String stream, String name) throws IOException, JetStreamApiException {
        return connection().getConsumerContext(stream, name, jetStreamOptions);
    }

    public KeyValue keyValue(String bucket) throws IOException {
        return connection().keyValue(bucket, KeyValueOptions.builder(jetStreamOptions).build());
    }

    public KeyValueManagement keyValueManagement() throws IOException {
        return connection().keyValueManagement(KeyValueOptions.builder(jetStreamOptions).build());
    }

    public ObjectStore objectStore(String bucket) throws IOException {
        return connection().objectStore(bucket, ObjectStoreOptions.builder(jetStreamOptions).build());
    }

    public ObjectStoreManagement objectStoreManagement() throws IOException {
        return connection().objectStoreManagement(ObjectStoreOptions.builder(jetStreamOptions).build());
    }

    @Override
    public @Nullable ReadinessProbeFailure probe() {
        if (!enabled || !config.readinessProbe()) {
            return null;
        }
        var current = connection;
        return current != null && current.getStatus() == Connection.Status.CONNECTED
            ? null : new ReadinessProbeFailure("NATS connection is not connected");
    }

    @Override
    public synchronized void release() throws Exception {
        released = true;
        if (connecting != null) {
            connecting.completeExceptionally(new IllegalStateException("NATS client is released"));
        }
        var current = connection;
        if (current == null) {
            return;
        }
        connection = null;
        try {
            if (current.getStatus() != Connection.Status.CLOSED
                && !current.drain(config.shutdownWait()).get(config.shutdownWait().toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("NATS connection did not drain within shutdownWait");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            current.close();
        }
    }
}
