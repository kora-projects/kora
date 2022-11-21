package ru.tinkoff.kora.cache.redis.client;

import com.typesafe.config.Config;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.ByteArrayCodec;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface LettuceModule {

    default LettuceClientConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceClientConfig> extractor) {
        var value = config.getValue("lettuce");
        return extractor.extract(value);
    }

    default LettuceClientFactory lettuceClientFactory() {
        return new LettuceClientFactory();
    }

    default AbstractRedisClient lettuceRedisClient(LettuceClientFactory factory, LettuceClientConfig config) {
        return factory.build(config);
    }

    default LettuceBasicCommands lettuceBasicCommands(AbstractRedisClient redisClient) {
        if (redisClient instanceof RedisClient) {
            var connection = ((RedisClient) redisClient).connect(new ByteArrayCodec());
            var syncCommands = connection.sync();
            var reactiveCommands = connection.reactive();
            return new LettuceBasicCommands(new LettuceBasicCommands.Sync(syncCommands, syncCommands, syncCommands),
                new LettuceBasicCommands.Reactive(reactiveCommands, reactiveCommands, reactiveCommands), connection);
        } else if (redisClient instanceof RedisClusterClient) {
            var connection = ((RedisClusterClient) redisClient).connect(new ByteArrayCodec());
            var syncCommands = connection.sync();
            var reactiveCommands = connection.reactive();
            return new LettuceBasicCommands(new LettuceBasicCommands.Sync(syncCommands, syncCommands, syncCommands),
                new LettuceBasicCommands.Reactive(reactiveCommands, reactiveCommands, reactiveCommands), connection);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }

    default SyncRedisClient lettuceCacheRedisClient(LettuceBasicCommands commands) {
        return new LettuceSyncRedisClient(commands);
    }

    default ReactiveRedisClient lettuceReactiveCacheRedisClient(LettuceBasicCommands commands) {
        return new LettuceReactiveRedisClient(commands);
    }
}
