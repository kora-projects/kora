package ru.tinkoff.kora.redis.jedis;

import jakarta.annotation.Nonnull;
import redis.clients.jedis.*;
import redis.clients.jedis.util.JedisURIHelper;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class JedisFactory {

    private JedisFactory() { }

    @Nonnull
    static UnifiedJedis build(JedisConfig config) {
        return (config.uri().size() == 1)
            ? buildRedisClient(config)
            : buildRedisClusterClient(config);
    }

    @Nonnull
    private static JedisPooled buildRedisClient(JedisConfig config) {
        URI uri = URI.create(config.uri().get(0));

        var jedisConfigBuilder = DefaultJedisClientConfig.builder()
            .user(JedisURIHelper.getUser(uri))
            .password(JedisURIHelper.getPassword(uri))
            .database(JedisURIHelper.getDBIndex(uri))
            .ssl(JedisURIHelper.isRedisSSLScheme(uri));

        var protocol = switch (config.protocol()) {
            case RESP3 -> RedisProtocol.RESP3;
            case RESP2 -> RedisProtocol.RESP2;
        };
        jedisConfigBuilder = jedisConfigBuilder.protocol(protocol);

        var uriProtocol = JedisURIHelper.getRedisProtocol(uri);
        if (uriProtocol != null) {
            jedisConfigBuilder = jedisConfigBuilder.protocol(uriProtocol);
        }
        if (config.database() != null) {
            jedisConfigBuilder = jedisConfigBuilder.database(config.database());
        }
        if (config.user() != null) {
            jedisConfigBuilder = jedisConfigBuilder.user(config.user());
        }
        if (config.password() != null) {
            jedisConfigBuilder = jedisConfigBuilder.password(config.password());
        }

        return new JedisPooled(JedisURIHelper.getHostAndPort(uri), jedisConfigBuilder.build());
    }

    @Nonnull
    private static JedisCluster buildRedisClusterClient(JedisConfig config) {
        List<URI> uris = config.uri().stream()
            .map(URI::create)
            .toList();

        Set<HostAndPort> hostAndPorts = uris.stream()
            .map(JedisURIHelper::getHostAndPort)
            .collect(Collectors.toSet());

        URI uri = uris.get(0);
        var jedisConfigBuilder = DefaultJedisClientConfig.builder()
            .user(JedisURIHelper.getUser(uri))
            .password(JedisURIHelper.getPassword(uri))
            .database(JedisURIHelper.getDBIndex(uri))
            .protocol(JedisURIHelper.getRedisProtocol(uri))
            .ssl(JedisURIHelper.isRedisSSLScheme(uri));

        var protocol = switch (config.protocol()) {
            case RESP3 -> RedisProtocol.RESP3;
            case RESP2 -> RedisProtocol.RESP2;
        };
        jedisConfigBuilder = jedisConfigBuilder.protocol(protocol);

        var uriProtocol = JedisURIHelper.getRedisProtocol(uri);
        if (uriProtocol != null) {
            jedisConfigBuilder = jedisConfigBuilder.protocol(uriProtocol);
        }
        if (config.database() != null) {
            jedisConfigBuilder = jedisConfigBuilder.database(config.database());
        }
        if (config.user() != null) {
            jedisConfigBuilder = jedisConfigBuilder.user(config.user());
        }
        if (config.password() != null) {
            jedisConfigBuilder = jedisConfigBuilder.password(config.password());
        }

        return new JedisCluster(hostAndPorts, jedisConfigBuilder.build());
    }
}
