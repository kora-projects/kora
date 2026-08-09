package io.koraframework.database.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import io.koraframework.database.mongo.util.MongoClientSettingsUtils;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

public class MongoDataSource implements MongoExecutor, Wrapped<MongoClient>, Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(MongoDataSource.class);
    private static final BsonDocument PING = new BsonDocument("ping", new BsonInt32(1));

    private final MongoConfig config;
    private final MongoClientSettings settings;
    private final String databaseName;
    private final DatabaseTelemetry telemetry;
    private final ScopedValue<ClientSession> session = ScopedValue.newInstance();

    private volatile @Nullable MongoClient client;

    public MongoDataSource(MongoConfig config,
                           DatabaseTelemetryFactory telemetryFactory,
                           @Nullable Configurer<MongoClientSettings.Builder> configurer) {
        this.config = Objects.requireNonNull(config);
        var connectionString = MongoClientSettingsUtils.parseConnectionString(config.uri());
        this.databaseName = MongoClientSettingsUtils.resolveDatabaseName(config, connectionString);
        this.settings = MongoClientSettingsUtils.build(config, connectionString, configurer);
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            Objects.requireNonNullElse(config.applicationName(), this.databaseName),
            "mongodb"
        );
    }

    @Override
    public void init() {
        logger.debug("MongoDataSource '{}' starting...", this.databaseName);
        var started = System.nanoTime();

        var created = MongoClients.create(this.settings);
        if (this.config.initializationFailFast()) {
            try {
                created.getDatabase(this.databaseName).runCommand(PING);
            } catch (Exception e) {
                created.close();
                throw new IllegalStateException("MongoDataSource '%s' failed to start due to: %s; check MongoDB availability, connection string, credentials, TLS, and network access".formatted(
                    this.databaseName, e.getMessage()), e);
            }
        }
        this.client = created;

        logger.info("MongoDataSource '{}' started in {}", this.databaseName, TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var current = this.client;
        if (current != null) {
            logger.debug("MongoDataSource '{}' stopping...", this.databaseName);
            var started = System.nanoTime();

            current.close();
            this.client = null;

            logger.info("MongoDataSource '{}' stopped in {}", this.databaseName, TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public MongoClient value() {
        return this.client();
    }

    @Override
    public MongoClient client() {
        var current = this.client;
        if (current == null) {
            throw new IllegalStateException("MongoDataSource '%s' is not initialized yet or is already released".formatted(this.databaseName));
        }
        return current;
    }

    @Override
    public MongoDatabase database() {
        return this.client().getDatabase(this.databaseName);
    }

    @Override
    public MongoDatabase database(String name) {
        return this.client().getDatabase(name);
    }

    @Override
    public DatabaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Nullable
    @Override
    public ClientSession currentSession() {
        return this.session.isBound()
            ? this.session.get()
            : null;
    }

    @Override
    public <T> T inTx(TransactionOptions options, Supplier<T> callback) {
        if (this.session.isBound()) {
            return callback.get();
        }

        try (var clientSession = this.client().startSession()) {
            return ScopedValue.where(this.session, clientSession)
                .call(() -> clientSession.withTransaction(callback::get, options));
        }
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        if (!this.config.readinessProbe()) {
            return null;
        }

        var current = this.client;
        if (current == null) {
            return new ReadinessProbeFailure("MongoDataSource '%s' is not initialized".formatted(this.databaseName));
        }
        current.getDatabase(this.databaseName).runCommand(PING);
        return null;
    }
}
