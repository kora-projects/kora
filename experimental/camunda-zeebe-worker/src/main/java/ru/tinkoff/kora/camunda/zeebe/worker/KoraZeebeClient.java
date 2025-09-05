package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class KoraZeebeClient implements Wrapped<ZeebeClient>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraZeebeClient.class);

    private final ZeebeClientConfig clientConfig;
    private final ZeebeClientConfiguration clientConfiguration;
    private final ManagedChannel managedChannel;

    private volatile ZeebeClientImpl zeebeClient;

    public KoraZeebeClient(ZeebeClientConfig clientConfig, ZeebeClientConfiguration clientConfiguration, ManagedChannel managedChannel) {
        this.clientConfig = clientConfig;
        this.clientConfiguration = clientConfiguration;
        this.managedChannel = managedChannel;
    }

    @Override
    public ZeebeClient value() {
        return this.zeebeClient;
    }

    @Override
    public void init() {
        logger.debug("ZeebeClient starting...");
        final long started = TimeUtils.started();

        this.zeebeClient = new ZeebeClientImpl(clientConfiguration, managedChannel);
        final Duration initTimeout = clientConfig.initializationFailTimeout();
        if (initTimeout != null) {
            try {
                var topology = this.zeebeClient.newTopologyRequest().send().join(initTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (topology.getBrokers().isEmpty()) {
                    throw new IllegalStateException("ZeebeClient topology is unavailable for gRPC URL: " + clientConfiguration.getGrpcAddress());
                }
            } catch (IllegalStateException e) {
                throw new RuntimeException(e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException("ZeebeClient failed to start after timeout '%s' for gRPC URL '%s', due to: %s".formatted(
                    initTimeout, clientConfiguration.getGrpcAddress(), e.getMessage()), e);
            }
        }

        logger.info("ZeebeClient started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.zeebeClient != null) {
            logger.debug("ZeebeClient stopping...");
            final long started = TimeUtils.started();

            this.zeebeClient.close();

            logger.info("ZeebeClient stopped in {}", TimeUtils.tookForLogging(started));
        }
    }
}
