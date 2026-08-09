package io.koraframework.database.mongo;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к MongoDB.
 * <hr>
 * <b>English</b>: Configuration describing connections to MongoDB.
 *
 * @see MongoRepository
 */
@ConfigMapper
public interface MongoConfig {

    /**
     * @return MongoDB connection string, for example {@code mongodb://localhost:27017/mydb}.
     */
    String uri();

    /**
     * @return Default database used by repositories, overrides the database from the connection string.
     */
    @Nullable
    String database();

    /**
     * @return Credentials used for authentication in MongoDB, override credentials from the connection string.
     */
    @Nullable
    MongoCredentials auth();

    /**
     * @return Connection pool settings.
     */
    PoolConfig pool();

    /**
     * @return TCP socket settings.
     */
    SocketConfig socket();

    /**
     * @return Cluster discovery and server selection settings.
     */
    ClusterConfig cluster();

    /**
     * @return Server monitoring (heartbeat) settings.
     */
    ServerConfig server();

    /**
     * @return SSL/TLS settings for connections to MongoDB.
     */
    @Nullable
    SslConfig ssl();

    /**
     * @return Read preference name, for example {@code primary}, {@code secondaryPreferred} or {@code nearest}.
     */
    @Nullable
    String readPreference();

    /**
     * @return Read concern level, for example {@code local}, {@code majority}, {@code linearizable} or {@code snapshot}.
     */
    @Nullable
    String readConcern();

    /**
     * @return Write concern, either a number of acknowledging nodes or {@code majority}.
     */
    @Nullable
    String writeConcern();

    /**
     * @return Retries a read operation once when a retryable error occurs.
     */
    @Nullable
    Boolean retryReads();

    /**
     * @return Retries a write operation once when a retryable error occurs.
     */
    @Nullable
    Boolean retryWrites();

    /**
     * @return Application name reported to the server and shown in its logs and profiler output.
     */
    @Nullable
    String applicationName();

    /**
     * @return Verifies the cluster is reachable with a {@code ping} command on startup, bounded by {@code cluster.serverSelectionTimeout}.
     */
    default boolean initializationFailFast() {
        return true;
    }

    /**
     * @return Executes the {@code ping} command on readiness probe.
     */
    default boolean readinessProbe() {
        return true;
    }

    /**
     * @return Kora telemetry settings for executed queries.
     */
    DatabaseTelemetryConfig telemetry();

    @ConfigMapper
    interface MongoCredentials {

        /**
         * @return Username for authentication in MongoDB.
         */
        String login();

        /**
         * @return Password for authentication in MongoDB.
         */
        String password();

        /**
         * @return Database that holds the user record, {@code admin} by default.
         */
        default String source() {
            return "admin";
        }
    }

    @ConfigMapper
    interface PoolConfig {

        /**
         * @return Minimum number of connections kept per server.
         */
        @Nullable
        Integer minSize();

        /**
         * @return Maximum number of connections per server.
         */
        @Nullable
        Integer maxSize();

        /**
         * @return Maximum number of connections a pool may be establishing concurrently.
         */
        @Nullable
        Integer maxConnecting();

        /**
         * @return Maximum time to wait for a connection to become available.
         */
        @Nullable
        Duration maxWaitTime();

        /**
         * @return Maximum lifetime of a pooled connection.
         */
        @Nullable
        Duration maxConnectionLifeTime();

        /**
         * @return Maximum idle time of a pooled connection.
         */
        @Nullable
        Duration maxConnectionIdleTime();
    }

    @ConfigMapper
    interface SocketConfig {

        /**
         * @return Timeout for opening a TCP connection to a server.
         */
        @Nullable
        Duration connectTimeout();

        /**
         * @return Timeout for reading a response from a server.
         */
        @Nullable
        Duration readTimeout();
    }

    @ConfigMapper
    interface ClusterConfig {

        /**
         * @return Timeout for selecting a server that satisfies the read preference.
         */
        @Nullable
        Duration serverSelectionTimeout();

        /**
         * @return Latency window within which a server is considered as fast as the fastest one.
         */
        @Nullable
        Duration localThreshold();
    }

    @ConfigMapper
    interface ServerConfig {

        /**
         * @return Interval between server monitoring checks.
         */
        @Nullable
        Duration heartbeatFrequency();

        /**
         * @return Minimum interval between consecutive server monitoring checks.
         */
        @Nullable
        Duration minHeartbeatFrequency();
    }

    @ConfigMapper
    interface SslConfig {

        /**
         * @return Enables SSL/TLS for connections to MongoDB.
         */
        default boolean enabled() {
            return true;
        }

        /**
         * @return Skips the check that the server hostname matches its SSL/TLS certificate.
         */
        default boolean invalidHostNameAllowed() {
            return false;
        }
    }
}
