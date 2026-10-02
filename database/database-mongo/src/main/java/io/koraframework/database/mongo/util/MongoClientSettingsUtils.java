package io.koraframework.database.mongo.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import io.koraframework.common.Configurer;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandStartedEvent;
import io.koraframework.database.mongo.MongoConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.mongodb.DefaultMongoCommandTagsProvider;
import io.micrometer.core.instrument.binder.mongodb.DefaultMongoConnectionPoolTagsProvider;
import io.micrometer.core.instrument.binder.mongodb.MongoCommandTagsProvider;
import io.micrometer.core.instrument.binder.mongodb.MongoConnectionPoolTagsProvider;
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsCommandListener;
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MongoClientSettingsUtils {

    private MongoClientSettingsUtils() {}

    public static ConnectionString parseConnectionString(String uri) {
        try {
            return new ConnectionString(uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MongoDB connection string is invalid: %s; expected mongodb://host:port/database or mongodb+srv://host/database".formatted(e.getMessage()), e);
        }
    }

    public static String resolveDatabaseName(MongoConfig config, ConnectionString connectionString) {
        if (config.database() != null) {
            return config.database();
        }
        if (connectionString.getDatabase() != null) {
            return connectionString.getDatabase();
        }
        throw new IllegalStateException("MongoDB database name is not specified; set the 'mongo.database' config property or put the database into the connection string");
    }

    /**
     * @param meterRegistry registry for driver command and connection pool metrics, {@code null} disables them
     * @param poolName      value of the {@code db.client.connection.pool.name} tag of driver metrics
     */
    public static MongoClientSettings build(MongoConfig config,
                                            ConnectionString connectionString,
                                            @Nullable Configurer<MongoClientSettings.Builder> configurer,
                                            @Nullable MeterRegistry meterRegistry,
                                            String poolName) {
        var builder = MongoClientSettings.builder().applyConnectionString(connectionString);

        if (meterRegistry != null) {
            applyDriverMetrics(builder, meterRegistry, poolName, config.telemetry().metrics().tags());
        }

        if (config.auth() != null) {
            builder.credential(MongoCredential.createCredential(config.auth().login(), config.auth().source(), config.auth().password().toCharArray()));
        }

        applyPoolConfig(builder, config.pool());
        applySocketConfig(builder, config.socket());
        applyClusterConfig(builder, config.cluster());
        applyServerConfig(builder, config.server());

        if (config.ssl() != null) {
            var ssl = config.ssl();
            builder.applyToSslSettings(b -> b
                .enabled(ssl.enabled())
                .invalidHostNameAllowed(ssl.invalidHostNameAllowed()));
        }

        if (config.readPreference() != null) builder.readPreference(parseReadPreference(config.readPreference()));
        if (config.readConcern() != null) builder.readConcern(parseReadConcern(config.readConcern()));
        if (config.writeConcern() != null) builder.writeConcern(parseWriteConcern(config.writeConcern()));
        if (config.retryReads() != null) builder.retryReads(config.retryReads());
        if (config.retryWrites() != null) builder.retryWrites(config.retryWrites());
        if (config.applicationName() != null) builder.applicationName(config.applicationName());

        if (configurer != null) {
            return configurer.configure(builder).build();
        }
        return builder.build();
    }

    // cluster.id is random per client, so it would start new metric series on every restart;
    // the pool name tag and configured tags are the same as on Kora database metrics
    private static void applyDriverMetrics(MongoClientSettings.Builder builder, MeterRegistry meterRegistry, String poolName, Map<String, String> tags) {
        var commonTags = Tags.of(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), poolName);
        for (var tag : tags.entrySet()) {
            commonTags = commonTags.and(tag.getKey(), tag.getValue());
        }
        var finalTags = commonTags;

        var defaultCommandTags = new DefaultMongoCommandTagsProvider();
        var commandTags = new MongoCommandTagsProvider() {
            @Override
            public void commandStarted(CommandStartedEvent event) {
                defaultCommandTags.commandStarted(event);
            }

            @Override
            public Iterable<Tag> commandTags(CommandEvent event) {
                return withoutClusterId(defaultCommandTags.commandTags(event)).and(finalTags);
            }
        };
        var defaultPoolTags = new DefaultMongoConnectionPoolTagsProvider();
        MongoConnectionPoolTagsProvider poolTags = event -> withoutClusterId(defaultPoolTags.connectionPoolTags(event)).and(finalTags);

        builder.addCommandListener(new MongoMetricsCommandListener(meterRegistry, commandTags));
        builder.applyToConnectionPoolSettings(b -> b.addConnectionPoolListener(new MongoMetricsConnectionPoolListener(meterRegistry, poolTags)));
    }

    private static Tags withoutClusterId(Iterable<Tag> tags) {
        var result = Tags.empty();
        for (var tag : tags) {
            if (!tag.getKey().equals("cluster.id")) {
                result = result.and(tag);
            }
        }
        return result;
    }

    private static void applyPoolConfig(MongoClientSettings.Builder builder, MongoConfig.PoolConfig config) {
        builder.applyToConnectionPoolSettings(b -> {
            if (config.minSize() != null) b.minSize(config.minSize());
            if (config.maxSize() != null) b.maxSize(config.maxSize());
            if (config.maxConnecting() != null) b.maxConnecting(config.maxConnecting());
            if (config.maxWaitTime() != null) b.maxWaitTime(config.maxWaitTime().toMillis(), TimeUnit.MILLISECONDS);
            if (config.maxConnectionLifeTime() != null) b.maxConnectionLifeTime(config.maxConnectionLifeTime().toMillis(), TimeUnit.MILLISECONDS);
            if (config.maxConnectionIdleTime() != null) b.maxConnectionIdleTime(config.maxConnectionIdleTime().toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private static void applySocketConfig(MongoClientSettings.Builder builder, MongoConfig.SocketConfig config) {
        builder.applyToSocketSettings(b -> {
            if (config.connectTimeout() != null) b.connectTimeout(toIntMillis(config.connectTimeout(), "socket.connectTimeout"), TimeUnit.MILLISECONDS);
            if (config.readTimeout() != null) b.readTimeout(toIntMillis(config.readTimeout(), "socket.readTimeout"), TimeUnit.MILLISECONDS);
        });
    }

    private static void applyClusterConfig(MongoClientSettings.Builder builder, MongoConfig.ClusterConfig config) {
        builder.applyToClusterSettings(b -> {
            if (config.serverSelectionTimeout() != null) b.serverSelectionTimeout(config.serverSelectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (config.localThreshold() != null) b.localThreshold(config.localThreshold().toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private static void applyServerConfig(MongoClientSettings.Builder builder, MongoConfig.ServerConfig config) {
        builder.applyToServerSettings(b -> {
            if (config.heartbeatFrequency() != null) b.heartbeatFrequency(config.heartbeatFrequency().toMillis(), TimeUnit.MILLISECONDS);
            if (config.minHeartbeatFrequency() != null) b.minHeartbeatFrequency(config.minHeartbeatFrequency().toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private static int toIntMillis(Duration duration, String property) {
        var millis = duration.toMillis();
        if (millis < 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("MongoDB '%s' must be between 0 and %d milliseconds, but was %d".formatted(property, Integer.MAX_VALUE, millis));
        }
        return (int) millis;
    }

    private static ReadPreference parseReadPreference(String value) {
        try {
            return ReadPreference.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MongoDB 'mongo.readPreference' is invalid: %s; expected primary, primaryPreferred, secondary, secondaryPreferred or nearest".formatted(value), e);
        }
    }

    private static ReadConcern parseReadConcern(String value) {
        try {
            return new ReadConcern(ReadConcernLevel.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MongoDB 'mongo.readConcern' is invalid: %s; expected local, majority, linearizable, available or snapshot".formatted(value), e);
        }
    }

    // WriteConcern.valueOf() silently returns null for an unknown name, so names are matched here to fail with a clear message
    private static WriteConcern parseWriteConcern(String value) {
        var nodeCount = tryParseInt(value);
        if (nodeCount != null) {
            return new WriteConcern(nodeCount);
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "majority" -> WriteConcern.MAJORITY;
            case "acknowledged" -> WriteConcern.ACKNOWLEDGED;
            case "unacknowledged" -> WriteConcern.UNACKNOWLEDGED;
            case "journaled" -> WriteConcern.JOURNALED;
            case "w1" -> WriteConcern.W1;
            case "w2" -> WriteConcern.W2;
            case "w3" -> WriteConcern.W3;
            default ->
                throw new IllegalArgumentException("MongoDB 'mongo.writeConcern' is invalid: %s; expected majority, acknowledged, unacknowledged, journaled, w1, w2, w3 or a number of nodes".formatted(value));
        };
    }

    @Nullable
    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
