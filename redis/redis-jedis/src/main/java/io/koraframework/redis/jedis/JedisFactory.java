package io.koraframework.redis.jedis;

import io.koraframework.common.util.Configurer;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.*;
import redis.clients.jedis.executors.ClusterCommandExecutor;
import redis.clients.jedis.providers.ClusterConnectionProvider;
import redis.clients.jedis.util.JedisURIHelper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static redis.clients.jedis.StaticCommandFlagsRegistry.registry;

public class JedisFactory {

    @Nullable
    private final Configurer<DefaultJedisClientConfig.Builder> configConfigurer;
    @Nullable
    private final Configurer<ConnectionPoolConfig> poolConfigConfigurer;
    @Nullable
    private final Configurer<RedisClient.Builder> standaloneConfigurer;
    @Nullable
    private final Configurer<RedisClusterClient.Builder> clusterConfigurer;

    public JedisFactory(@Nullable Configurer<DefaultJedisClientConfig.Builder> configConfigurer,
                        @Nullable Configurer<ConnectionPoolConfig> poolConfigConfigurer,
                        @Nullable Configurer<RedisClient.Builder> standaloneConfigurer,
                        @Nullable Configurer<RedisClusterClient.Builder> clusterConfigurer) {
        this.configConfigurer = configConfigurer;
        this.poolConfigConfigurer = poolConfigConfigurer;
        this.standaloneConfigurer = standaloneConfigurer;
        this.clusterConfigurer = clusterConfigurer;
    }

    public DefaultJedisClientConfig.Builder buildClientConfig(JedisConfig config) {
        List<URI> uris = config.uri().stream()
            .map(URI::create)
            .toList();

        URI uri = uris.get(0);
        var builder = DefaultJedisClientConfig.builder()
            .clientName("kora")
            .user(JedisURIHelper.getUser(uri))
            .password(JedisURIHelper.getPassword(uri))
            .database(JedisURIHelper.getDBIndex(uri))
            .protocol(JedisURIHelper.getRedisProtocol(uri))
            .connectionTimeoutMillis(config.connectionTimeout().toMillisPart())
            .socketTimeoutMillis(config.socketTimeout().toMillisPart())
            .blockingSocketTimeoutMillis(config.socketTimeout().toMillisPart())
            .ssl(JedisURIHelper.isRedisSSLScheme(uri));

        var protocol = switch (config.protocol()) {
            case RESP3 -> RedisProtocol.RESP3;
            case RESP2 -> RedisProtocol.RESP2;
        };
        builder = builder.protocol(protocol);

        var uriProtocol = JedisURIHelper.getRedisProtocol(uri);
        if (uriProtocol != null) {
            builder = builder.protocol(uriProtocol);
        }
        if (config.database() != null) {
            builder = builder.database(config.database());
        }
        if (config.user() != null) {
            builder = builder.user(config.user());
        }
        if (config.password() != null) {
            builder = builder.password(config.password());
        }
        builder = builder.ssl(config.ssl().enabled());

        if (configConfigurer != null) {
            builder = configConfigurer.configure(builder);
        }

        if (config.cluster().readStrategy() == JedisConfig.ClusterConfig.ReadStrategy.REPLICA) {
            builder = builder.readOnlyForRedisClusterReplicas();
        }

        return builder;
    }

    public ConnectionPoolConfig buildPoolConfig(JedisConfig.PoolConfig config) {
        var builder = new ConnectionPoolConfig();

        builder.setMaxTotal(config.maxSize());
        builder.setMinIdle(config.minIdle());
        builder.setMaxIdle(config.maxIdle());
        builder.setMaxWait(config.maxAcquireWait());
        builder.setTestOnBorrow(config.validateOnAcquire());
        builder.setTestOnCreate(config.validateOnCreate());
        builder.setTestOnReturn(config.validateOnRelease());
        builder.setTestWhileIdle(config.validateOnIdle());
        builder.setMinEvictableIdleDuration(config.evictIdleDuration());
        builder.setTimeBetweenEvictionRuns(config.evictBetweenRunsDuration());
        builder.setNumTestsPerEvictionRun(config.evictTestsPerRun());

        builder.setJmxEnabled(config.jmxEnabled());
        builder.setJmxNameBase("kora-jedis");
        builder.setJmxNamePrefix("kora-jedis-pool-");

        var result = builder;
        if (poolConfigConfigurer != null) {
            result = poolConfigConfigurer.configure(builder);
        }

        return result;
    }

    public UnifiedJedis build(JedisConfig config) {
        return (config.uri().size() == 1)
            ? buildStandalone(config).build()
            : buildCluster(config).build();
    }

    public RedisClient.Builder buildStandalone(JedisConfig config) {
        var clientConfig = buildClientConfig(config).build();
        var poolConfig = buildPoolConfig(config.pool());

        URI uri = URI.create(config.uri().get(0));
        var hostAndPort = JedisURIHelper.getHostAndPort(uri);
        var builder = (RedisClient.Builder) RedisClient.builder()
            .hostAndPort(hostAndPort)
            .clientConfig(clientConfig)
            .poolConfig(poolConfig);

        if (standaloneConfigurer != null) {
            builder = standaloneConfigurer.configure(builder);
        }

        return builder;
    }

    public RedisClusterClient.Builder buildCluster(JedisConfig config) {
        var clientConfig = buildClientConfig(config).build();
        var poolConfig = buildPoolConfig(config.pool());

        List<URI> uris = config.uri().stream()
            .map(URI::create)
            .toList();

        Set<HostAndPort> hostAndPorts = uris.stream()
            .map(JedisURIHelper::getHostAndPort)
            .collect(Collectors.toSet());

        var builder = (RedisClusterClient.Builder) RedisClusterClient.builder()
            .nodes(hostAndPorts)
            .topologyRefreshPeriod(config.cluster().topologyRefreshPeriod())
            .maxAttempts(config.cluster().retryMaxAttempts())
            .maxTotalRetriesDuration(config.cluster().retryMaxDuration())
            .clientConfig(clientConfig)
            .poolConfig(poolConfig);

        var connectionProvider = new ClusterConnectionProvider(hostAndPorts, clientConfig,
            null, poolConfig, config.cluster().topologyRefreshPeriod());

        Duration effectiveMaxTotalRetriesDuration = (config.cluster().retryMaxDuration() == null)
            ? Duration.ofMillis(config.socketTimeout().toMillis() * config.cluster().retryMaxAttempts())
            : config.cluster().retryMaxDuration();

        var flagRegistry = StaticCommandFlagsRegistry.registry();
        var commandExecutor = new ClusterCommandExecutor(connectionProvider,
            config.cluster().retryMaxAttempts(), effectiveMaxTotalRetriesDuration, flagRegistry);

        var koraCommandExecutor = new KoraClusterCommandExecutor(commandExecutor);

        if (clusterConfigurer != null) {
            builder = clusterConfigurer.configure(builder);
        }

        return builder;
    }
}
