package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.SocketOptions;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface LettuceConfig {

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
        return Duration.ofSeconds(SocketOptions.DEFAULT_CONNECT_TIMEOUT);
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
