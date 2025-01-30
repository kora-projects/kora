package ru.tinkoff.kora.redis.jedis;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface JedisConfig {

    List<String> uri();

    @Nullable
    Integer database();

    @Nullable
    String user();

    @Nullable
    String password();

    default Protocol protocol() {
        return Protocol.RESP3;
    }

    default Duration socketTimeout() {
        return Duration.ofSeconds(10);
    }

    default Duration commandTimeout() {
        return Duration.ofSeconds(20);
    }

    enum Protocol {

        /** Redis 2 to Redis 5 */
        RESP2,
        /** Redis 6+ */
        RESP3
    }
}
