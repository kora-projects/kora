package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.resource.DefaultClientResources;

public interface LettuceConfigurator {

    default DefaultClientResources.Builder configure(DefaultClientResources.Builder resouceBuilder) {
        return resouceBuilder;
    }

    default ClusterClientOptions.Builder configure(ClusterClientOptions.Builder clusterBuilder) {
        return clusterBuilder;
    }

    default ClientOptions.Builder configure(ClientOptions.Builder clientBuilder) {
        return clientBuilder;
    }
}
