package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.ZeebeClient;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

public final class ZeebeClientReadinessProbe implements ReadinessProbe {

    private final ZeebeClient client;

    public ZeebeClientReadinessProbe(ZeebeClient client) {
        this.client = client;
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        var topology = client.newTopologyRequest().send().join();
        if (topology.getBrokers().isEmpty()) {
            return new ReadinessProbeFailure("ZeebeClient no brokers available");
        } else {
            return null;
        }
    }
}
