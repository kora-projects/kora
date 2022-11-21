package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultProgrammaticDriverConfigLoaderBuilder;

import java.net.InetSocketAddress;
import java.util.stream.Collectors;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.*;

public class CassandraSessionBuilder {
    public CqlSession build(CassandraConfig config) {
        var builder = CqlSession.builder();
        var loaderBuilder = new DefaultProgrammaticDriverConfigLoaderBuilder();
        var contactPoints = config.basic().contactPoints().stream().map(host -> {
            var s = host.split(":");
            return new InetSocketAddress(s[0], Integer.parseInt(s[1]));
        }).collect(Collectors.toList());
        builder.addContactPoints(contactPoints);
        builder.withKeyspace(config.basic().sessionKeyspace());
        if (config.auth() != null) {
            builder.withAuthCredentials(config.auth().login(), config.auth().password());
        }
        if (config.basic().dc() != null) {
            builder.withLocalDatacenter(config.basic().dc());
        }

        setBasicOptions(loaderBuilder, config.basic());
        setAdvancedOptions(loaderBuilder, config.advanced());

        if (config.profiles() != null) {
            config.profiles().forEach((profileName, profileConfig) -> setProfile(builder, loaderBuilder, profileName, profileConfig.basic(), profileConfig.advanced()));
        }

        applyOverridable(loaderBuilder, config.basic(), config.advanced());
        builder.withConfigLoader(loaderBuilder.build());
        return builder.build();
    }

    void setBasicOptions(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Basic config) {
        if (config.sessionName() != null) builder.withString(SESSION_NAME, config.sessionName());

        if (config.cloud() != null && config.cloud().secureConnectBundle() != null) builder.withString(CLOUD_SECURE_CONNECT_BUNDLE, config.cloud().secureConnectBundle());
    }

    void setAdvancedOptions(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced config) {
        if (config == null) return;

        if (config.sessionLeak() != null && config.sessionLeak().threshold() != null) builder.withInt(SESSION_LEAK_THRESHOLD, config.sessionLeak().threshold());

        if (config.connection() != null) {
            applyConnectionConfig(builder, config.connection());
        }

        if (config.reconnectOnInit() != null) builder.withBoolean(RECONNECT_ON_INIT, config.reconnectOnInit());

        if (config.reconnectionPolicy() != null) {
            if (config.reconnectionPolicy().baseDelay() != null) builder.withDuration(RECONNECTION_BASE_DELAY, config.reconnectionPolicy().baseDelay());
            if (config.reconnectionPolicy().maxDelay() != null) builder.withDuration(RECONNECTION_MAX_DELAY, config.reconnectionPolicy().maxDelay());
        }

        if (config.sslEngineFactory() != null) {
            applySslEngineFactoryConfig(builder, config.sslEngineFactory());
        }

        if (config.timestampGenerator() != null) {
            applyTimestampGeneratorConfig(builder, config.timestampGenerator());
        }


        if (config.resolveContactPoints() != null) builder.withBoolean(RESOLVE_CONTACT_POINTS, config.resolveContactPoints());

        if (config.protocol() != null) {
            applyProtocolConfig(builder, config.protocol());
        }

        if (config.request() != null && config.request().warnIfSetKeyspace() != null) builder.withBoolean(REQUEST_WARN_IF_SET_KEYSPACE, config.request().warnIfSetKeyspace());

        if (config.metrics() != null && config.metrics().session() != null) {
            applyMetricsSessionConfig(builder, config.metrics().session());
        }

        if (config.metrics() != null && config.metrics().node() != null) {
            applyMetricsNodeConfig(builder, config.metrics().node());
        }

        if (config.socket() != null) {
            applySocketConfig(builder, config.socket());
        }
        if (config.heartbeat() != null) {
            applyHeartbeatConfig(builder, config.heartbeat());
        }
        if (config.metadata() != null) {
            applyMetadataConfig(builder, config.metadata());
        }
        if (config.controlConnection() != null) {
            applyControlConnection(builder, config.controlConnection());
        }
        if (config.preparedStatements() != null) {
            applyPreparedStatementsConf(builder, config.preparedStatements());
        }

        if (config.netty() != null) {
            applyNettyConfig(builder, config.netty());
        }

        if (config.coalescer() != null && config.coalescer().rescheduleInterval() != null) builder.withDuration(COALESCER_INTERVAL, config.coalescer().rescheduleInterval());
    }

    private void applyMetricsNodeConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.MetricsConfig.NodeConfig node) {
        if (node.enabled() != null) builder.withStringList(METRICS_NODE_ENABLED, node.enabled());
        if (node.cqlMessages() != null) {
            if (node.cqlMessages().highestLatency() != null) builder.withDuration(METRICS_NODE_CQL_MESSAGES_HIGHEST, node.cqlMessages().highestLatency());
            if (node.cqlMessages().lowestLatency() != null) builder.withDuration(METRICS_NODE_CQL_MESSAGES_LOWEST, node.cqlMessages().lowestLatency());
            if (node.cqlMessages().refreshInterval() != null) builder.withDuration(METRICS_NODE_CQL_MESSAGES_INTERVAL, node.cqlMessages().refreshInterval());
            if (node.cqlMessages().significantDigits() != null) builder.withInt(METRICS_NODE_CQL_MESSAGES_DIGITS, node.cqlMessages().significantDigits());
        }
    }

    private void applyMetricsSessionConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.MetricsConfig.SessionConfig session) {
        if (session.enabled() != null) builder.withStringList(METRICS_SESSION_ENABLED, session.enabled());
        if (session.cqlRequests() != null) {
            if (session.cqlRequests().highestLatency() != null) builder.withDuration(METRICS_SESSION_CQL_REQUESTS_HIGHEST, session.cqlRequests().highestLatency());
            if (session.cqlRequests().lowestLatency() != null) builder.withDuration(METRICS_SESSION_CQL_REQUESTS_LOWEST, session.cqlRequests().lowestLatency());
            if (session.cqlRequests().refreshInterval() != null) builder.withDuration(METRICS_SESSION_CQL_REQUESTS_INTERVAL, session.cqlRequests().refreshInterval());
            if (session.cqlRequests().significantDigits() != null) builder.withInt(METRICS_SESSION_CQL_REQUESTS_DIGITS, session.cqlRequests().significantDigits());
        }
        if (session.throttlingDelay() != null) {
            if (session.throttlingDelay().highestLatency() != null) builder.withDuration(METRICS_SESSION_THROTTLING_HIGHEST, session.throttlingDelay().highestLatency());
            if (session.throttlingDelay().lowestLatency() != null) builder.withDuration(METRICS_SESSION_THROTTLING_LOWEST, session.throttlingDelay().lowestLatency());
            if (session.throttlingDelay().refreshInterval() != null) builder.withDuration(METRICS_SESSION_THROTTLING_INTERVAL, session.throttlingDelay().refreshInterval());
            if (session.throttlingDelay().significantDigits() != null) builder.withInt(METRICS_SESSION_THROTTLING_DIGITS, session.throttlingDelay().significantDigits());
        }
    }

    private void applyProtocolConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.ProtocolConfig protocol) {
        if (protocol.version() != null) builder.withString(PROTOCOL_VERSION, protocol.version());
        if (protocol.compression() != null) builder.withString(PROTOCOL_COMPRESSION, protocol.compression());
        if (protocol.maxFrameLength() != null) builder.withLong(PROTOCOL_MAX_FRAME_LENGTH, protocol.maxFrameLength());
    }

    private void applyTimestampGeneratorConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.TimestampGeneratorConfig timestampGenerator) {
        if (timestampGenerator.driftWarning() != null && timestampGenerator.driftWarning().threshold() != null)
            builder.withDuration(TIMESTAMP_GENERATOR_DRIFT_WARNING_THRESHOLD, timestampGenerator.driftWarning().threshold());
        if (timestampGenerator.driftWarning() != null && timestampGenerator.driftWarning().interval() != null)
            builder.withDuration(TIMESTAMP_GENERATOR_DRIFT_WARNING_INTERVAL, timestampGenerator.driftWarning().interval());
        if (timestampGenerator.forceJavaClock() != null) builder.withBoolean(TIMESTAMP_GENERATOR_FORCE_JAVA_CLOCK, timestampGenerator.forceJavaClock());
    }

    private void applySslEngineFactoryConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.SslEngineFactoryConfig sslEngineFactory) {
        if (sslEngineFactory.cipherSuites() != null) builder.withStringList(SSL_CIPHER_SUITES, sslEngineFactory.cipherSuites());
        if (sslEngineFactory.hostnameValidation() != null) builder.withBoolean(SSL_HOSTNAME_VALIDATION, sslEngineFactory.hostnameValidation());
        if (sslEngineFactory.truststorePath() != null) builder.withString(SSL_TRUSTSTORE_PATH, sslEngineFactory.truststorePath());
        if (sslEngineFactory.truststorePassword() != null) builder.withString(SSL_TRUSTSTORE_PASSWORD, sslEngineFactory.truststorePassword());
        if (sslEngineFactory.keystorePath() != null) builder.withString(SSL_KEYSTORE_PATH, sslEngineFactory.keystorePath());
        if (sslEngineFactory.keystorePassword() != null) builder.withString(SSL_KEYSTORE_PASSWORD, sslEngineFactory.keystorePassword());
    }

    private void applyConnectionConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.ConnectionConfig connection) {
        if (connection.connectTimeout() != null) builder.withDuration(CONNECTION_CONNECT_TIMEOUT, connection.connectTimeout());
        if (connection.initQueryTimeout() != null) builder.withDuration(CONNECTION_INIT_QUERY_TIMEOUT, connection.initQueryTimeout());
        if (connection.setKeyspaceTimeout() != null) builder.withDuration(CONNECTION_SET_KEYSPACE_TIMEOUT, connection.setKeyspaceTimeout());
        if (connection.pool() != null && connection.pool().localSize() != null) builder.withInt(CONNECTION_POOL_LOCAL_SIZE, connection.pool().localSize());
        if (connection.pool() != null && connection.pool().remoteSize() != null) builder.withInt(CONNECTION_POOL_REMOTE_SIZE, connection.pool().remoteSize());
        if (connection.maxRequestsPerConnection() != null) builder.withInt(CONNECTION_MAX_REQUESTS, connection.maxRequestsPerConnection());
        if (connection.maxOrphanRequests() != null) builder.withInt(CONNECTION_MAX_ORPHAN_REQUESTS, connection.maxOrphanRequests());
        if (connection.warnOnInitError() != null) builder.withBoolean(CONNECTION_WARN_INIT_ERROR, connection.warnOnInitError());
    }

    private void applyNettyConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.NettyConfig netty) {
        if (netty.daemon() != null) builder.withBoolean(NETTY_DAEMON, netty.daemon());

        if (netty.ioGroup() != null) {
            if (netty.ioGroup().size() != null) builder.withInt(NETTY_IO_SIZE, netty.ioGroup().size());
            if (netty.ioGroup().shutdown() != null) {
                if (netty.ioGroup().shutdown().timeout() != null) builder.withInt(NETTY_IO_SHUTDOWN_TIMEOUT, netty.ioGroup().shutdown().timeout());
                if (netty.ioGroup().shutdown().quietPeriod() != null) builder.withInt(NETTY_IO_SHUTDOWN_QUIET_PERIOD, netty.ioGroup().shutdown().quietPeriod());
                if (netty.ioGroup().shutdown().unit() != null) builder.withString(NETTY_IO_SHUTDOWN_UNIT, netty.ioGroup().shutdown().unit());
            }
        }

        if (netty.adminGroup() != null) {
            if (netty.adminGroup().size() != null) builder.withInt(NETTY_ADMIN_SIZE, netty.adminGroup().size());
            if (netty.adminGroup().shutdown() != null) {
                if (netty.adminGroup().shutdown().timeout() != null) builder.withInt(NETTY_ADMIN_SHUTDOWN_TIMEOUT, netty.adminGroup().shutdown().timeout());
                if (netty.adminGroup().shutdown().quietPeriod() != null) builder.withInt(NETTY_ADMIN_SHUTDOWN_QUIET_PERIOD, netty.adminGroup().shutdown().quietPeriod());
                if (netty.adminGroup().shutdown().unit() != null) builder.withString(NETTY_ADMIN_SHUTDOWN_UNIT, netty.adminGroup().shutdown().unit());
            }
        }

        if (netty.timer() != null) {
            if (netty.timer().tickDuration() != null) builder.withDuration(NETTY_TIMER_TICK_DURATION, netty.timer().tickDuration());
            if (netty.timer().ticksPerWheel() != null) builder.withInt(NETTY_TIMER_TICKS_PER_WHEEL, netty.timer().ticksPerWheel());
        }
    }

    private void applyPreparedStatementsConf(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.PreparedStatementsConfig preparedStatements) {
        if (preparedStatements.reprepareOnUp() != null) {
            if (preparedStatements.reprepareOnUp().enabled() != null) builder.withBoolean(REPREPARE_ENABLED, preparedStatements.reprepareOnUp().enabled());
            if (preparedStatements.reprepareOnUp().checkSystemTable() != null) builder.withBoolean(REPREPARE_CHECK_SYSTEM_TABLE, preparedStatements.reprepareOnUp().checkSystemTable());
            if (preparedStatements.reprepareOnUp().maxStatements() != null) builder.withInt(REPREPARE_MAX_STATEMENTS, preparedStatements.reprepareOnUp().maxStatements());
            if (preparedStatements.reprepareOnUp().maxParallelism() != null) builder.withInt(REPREPARE_MAX_PARALLELISM, preparedStatements.reprepareOnUp().maxParallelism());
            if (preparedStatements.reprepareOnUp().timeout() != null) builder.withDuration(REPREPARE_TIMEOUT, preparedStatements.reprepareOnUp().timeout());
        }

        if (preparedStatements.preparedCache() != null && preparedStatements.preparedCache().weakValues() != null)
            builder.withBoolean(PREPARED_CACHE_WEAK_VALUES, preparedStatements.preparedCache().weakValues());
    }

    private void applyControlConnection(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.ControlConnectionConfig controlConnection) {
        if (controlConnection.timeout() != null) builder.withDuration(CONTROL_CONNECTION_TIMEOUT, controlConnection.timeout());
        if (controlConnection.schemaAgreement() != null) {
            if (controlConnection.schemaAgreement().interval() != null) builder.withDuration(CONTROL_CONNECTION_AGREEMENT_INTERVAL, controlConnection.schemaAgreement().interval());
            if (controlConnection.schemaAgreement().timeout() != null) builder.withDuration(CONTROL_CONNECTION_AGREEMENT_TIMEOUT, controlConnection.schemaAgreement().timeout());
            if (controlConnection.schemaAgreement().warnOnFailure() != null) builder.withBoolean(CONTROL_CONNECTION_AGREEMENT_WARN, controlConnection.schemaAgreement().warnOnFailure());
        }
    }

    private void applyHeartbeatConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.HeartBeatConfig heartbeat) {
        if (heartbeat.interval() != null) builder.withDuration(HEARTBEAT_INTERVAL, heartbeat.interval());
        if (heartbeat.timeout() != null) builder.withDuration(HEARTBEAT_TIMEOUT, heartbeat.timeout());
    }

    private void applySocketConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.SocketConfig socket) {
        if (socket.tcpNoDelay() != null) builder.withBoolean(SOCKET_TCP_NODELAY, socket.tcpNoDelay());
        if (socket.keepAlive() != null) builder.withBoolean(SOCKET_KEEP_ALIVE, socket.keepAlive());
        if (socket.reuseAddress() != null) builder.withBoolean(SOCKET_REUSE_ADDRESS, socket.reuseAddress());
        if (socket.lingerInterval() != null) builder.withInt(SOCKET_LINGER_INTERVAL, socket.lingerInterval());
        if (socket.receiveBufferSize() != null) builder.withInt(SOCKET_RECEIVE_BUFFER_SIZE, socket.receiveBufferSize());
        if (socket.sendBufferSize() != null) builder.withInt(SOCKET_SEND_BUFFER_SIZE, socket.sendBufferSize());
    }

    private void applyMetadataConfig(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Advanced.MetadataConfig metadata) {
        if (metadata.topologyEventDebouncer() != null) {
            if (metadata.topologyEventDebouncer().window() != null) builder.withDuration(METADATA_TOPOLOGY_WINDOW, metadata.topologyEventDebouncer().window());
            if (metadata.topologyEventDebouncer().maxEvents() != null) builder.withInt(METADATA_TOPOLOGY_MAX_EVENTS, metadata.topologyEventDebouncer().maxEvents());
        }

        if (metadata.schema() != null) {
            if (metadata.schema().enabled() != null) builder.withBoolean(METADATA_SCHEMA_ENABLED, metadata.schema().enabled());
            if (metadata.schema().refreshedKeyspaces() != null) builder.withStringList(METADATA_SCHEMA_REFRESHED_KEYSPACES, metadata.schema().refreshedKeyspaces());
            if (metadata.schema().requestTimeout() != null) builder.withDuration(METADATA_SCHEMA_REQUEST_TIMEOUT, metadata.schema().requestTimeout());
            if (metadata.schema().requestPageSize() != null) builder.withInt(METADATA_SCHEMA_REQUEST_PAGE_SIZE, metadata.schema().requestPageSize());
            if (metadata.schema().debouncer() != null && metadata.schema().debouncer().window() != null) builder.withDuration(METADATA_SCHEMA_WINDOW, metadata.schema().debouncer().window());
            if (metadata.schema().debouncer() != null && metadata.schema().debouncer().maxEvents() != null) builder.withInt(METADATA_SCHEMA_MAX_EVENTS, metadata.schema().debouncer().maxEvents());
        }

        if (metadata.tokenMapEnabled() != null) builder.withBoolean(METADATA_TOKEN_MAP_ENABLED, metadata.tokenMapEnabled());
    }

    void setProfile(CqlSessionBuilder builder, DefaultProgrammaticDriverConfigLoaderBuilder loaderBuilder, String profileName, CassandraConfig.Basic basic, CassandraConfig.Advanced advanced) {
        loaderBuilder.startProfile(profileName);
        if (basic.dc() != null) builder.withLocalDatacenter(profileName, basic.dc());
        applyOverridable(loaderBuilder, basic, advanced);
        loaderBuilder.endProfile();
    }

    void applyOverridable(DefaultProgrammaticDriverConfigLoaderBuilder builder, CassandraConfig.Basic basic, CassandraConfig.Advanced advanced) {
        if (basic.request() != null) {
            if (basic.request().timeout() != null) builder.withDuration(REQUEST_TIMEOUT, basic.request().timeout());
            if (basic.request().consistency() != null) builder.withString(REQUEST_CONSISTENCY, basic.request().consistency());
            if (basic.request().pageSize() != null) builder.withInt(REQUEST_PAGE_SIZE, basic.request().pageSize());
            if (basic.request().serialConsistency() != null) builder.withString(REQUEST_SERIAL_CONSISTENCY, basic.request().serialConsistency());
            if (basic.request().defaultIdempotence() != null) builder.withBoolean(REQUEST_DEFAULT_IDEMPOTENCE, basic.request().defaultIdempotence());
        }

        if (basic.loadBalancingPolicy() != null && basic.loadBalancingPolicy().slowReplicaAvoidance() != null)
            builder.withBoolean(LOAD_BALANCING_POLICY_SLOW_AVOIDANCE, basic.loadBalancingPolicy().slowReplicaAvoidance());

        if (advanced != null) {
            if (advanced.loadBalancingPolicy() != null) {
                if (advanced.loadBalancingPolicy().dcFailover() != null && advanced.loadBalancingPolicy().dcFailover().maxNodesPerRemoveDc() != null)
                    builder.withInt(LOAD_BALANCING_DC_FAILOVER_MAX_NODES_PER_REMOTE_DC, advanced.loadBalancingPolicy().dcFailover().maxNodesPerRemoveDc());
                if (advanced.loadBalancingPolicy().dcFailover() != null && advanced.loadBalancingPolicy().dcFailover().allowForLocalConsistencyLevels() != null)
                    builder.withBoolean(LOAD_BALANCING_DC_FAILOVER_ALLOW_FOR_LOCAL_CONSISTENCY_LEVELS, advanced.loadBalancingPolicy().dcFailover().allowForLocalConsistencyLevels());
            }

            if (advanced.request() != null && advanced.request().trace() != null) {
                if (advanced.request().trace().consistency() != null) builder.withString(REQUEST_TRACE_CONSISTENCY, advanced.request().trace().consistency());
                if (advanced.request().trace().attempts() != null) builder.withInt(REQUEST_TRACE_ATTEMPTS, advanced.request().trace().attempts());
                if (advanced.request().trace().interval() != null) builder.withDuration(REQUEST_TRACE_INTERVAL, advanced.request().trace().interval());
            }
            if (advanced.request() != null && advanced.request().logWarnings() != null) builder.withBoolean(REQUEST_LOG_WARNINGS, advanced.request().logWarnings());
            if (advanced.preparedStatements() != null && advanced.preparedStatements().prepareOnAllNodes() != null)
                builder.withBoolean(PREPARE_ON_ALL_NODES, advanced.preparedStatements().prepareOnAllNodes());
        }

    }
}
