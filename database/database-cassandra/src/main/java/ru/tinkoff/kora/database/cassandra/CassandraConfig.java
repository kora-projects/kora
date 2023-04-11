package ru.tinkoff.kora.database.cassandra;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CassandraConfig(
    Map<String, Profile> profiles,
    Basic basic,
    Advanced advanced,
    CassandraCredentials auth) {
    public CassandraConfig(
        @Nullable Map<String, Profile> profiles,
        Basic basic,
        @Nullable Advanced advanced,
        CassandraCredentials auth
    ) {
        this.profiles = profiles;
        this.basic = basic;
        this.advanced = advanced;
        this.auth = auth;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CassandraConfig) obj;
        return Objects.equals(this.profiles, that.profiles) &&
               Objects.equals(this.basic, that.basic) &&
               Objects.equals(this.advanced, that.advanced) &&
               Objects.equals(this.auth, that.auth);
    }

    public record CassandraCredentials(String login, String password) {}

    public record Profile(Basic basic, Advanced advanced) {}

    public record Basic(
        @Nullable BasicRequestConfig request,
        @Nullable String sessionName,
        List<String> contactPoints,
        @Nullable String dc,
        @Nullable String sessionKeyspace,
        @Nullable LoadBalancingPolicyConfig loadBalancingPolicy,
        @Nullable CloudConfig cloud
    ) {
        public record BasicRequestConfig(
            @Nullable Duration timeout,
            @Nullable String consistency,
            @Nullable Integer pageSize,
            @Nullable String serialConsistency,
            @Nullable Boolean defaultIdempotence
        ) {
        }

        public record LoadBalancingPolicyConfig(
            @Nullable Boolean slowReplicaAvoidance
        ) {
        }

        public record CloudConfig(
            @Nullable String secureConnectBundle
        ) {
        }
    }

    public record Advanced(
        @Nullable SessionLeakConfig sessionLeak,
        @Nullable ConnectionConfig connection,
        @Nullable Boolean reconnectOnInit,
        @Nullable ReconnectionPolicyConfig reconnectionPolicy,
        @Nullable AdvancedLoadBalancingPolicyConfig loadBalancingPolicy,
        @Nullable SslEngineFactoryConfig sslEngineFactory,
        @Nullable TimestampGeneratorConfig timestampGenerator,
        @Nullable ProtocolConfig protocol,
        @Nullable AdvancedRequestConfig request,
        @Nullable MetricsConfig metrics,
        @Nullable SocketConfig socket,
        @Nullable HeartBeatConfig heartbeat,
        @Nullable MetadataConfig metadata,
        @Nullable ControlConnectionConfig controlConnection,
        @Nullable PreparedStatementsConfig preparedStatements,
        @Nullable NettyConfig netty,
        @Nullable CoalescerConfig coalescer,
        @Nullable Boolean resolveContactPoints,
        @Nullable ThrottlerConfig throttler
    ) {
        public record SessionLeakConfig(
            @Nullable Integer threshold
        ) {
        }

        public record AdvancedLoadBalancingPolicyConfig(
            @Nullable DcFailover dcFailover
        ) {
            public record DcFailover(
                @Nullable Integer maxNodesPerRemoveDc,
                @Nullable Boolean allowForLocalConsistencyLevels
            ) {
            }
        }

        public record ConnectionConfig(
            @Nullable Duration connectTimeout,
            @Nullable Duration initQueryTimeout,
            @Nullable Duration setKeyspaceTimeout,
            @Nullable Integer maxRequestsPerConnection,
            @Nullable Integer maxOrphanRequests,
            @Nullable Boolean warnOnInitError,
            @Nullable PoolConfig pool
        ) {
            public record PoolConfig(
                @Nullable Integer localSize,
                @Nullable Integer remoteSize
            ) {
            }
        }

        public record ReconnectionPolicyConfig(
            @Nullable Duration baseDelay,
            @Nullable Duration maxDelay
        ) {
        }

        public record SslEngineFactoryConfig(
            @Nullable List<String> cipherSuites,
            @Nullable Boolean hostnameValidation,
            @Nullable String keystorePath,
            @Nullable String keystorePassword,
            @Nullable String truststorePath,
            @Nullable String truststorePassword
        ) {
        }

        public record TimestampGeneratorConfig(
            @Nullable Boolean forceJavaClock,
            @Nullable DriftWarningConfig driftWarning
        ) {
            public record DriftWarningConfig(
                @Nullable Duration threshold,
                @Nullable Duration interval
            ) {
            }
        }

        public record ProtocolConfig(
            @Nullable String version,
            @Nullable String compression,
            @Nullable Long maxFrameLength
        ) {
        }

        public record AdvancedRequestConfig(
            @Nullable Boolean warnIfSetKeyspace,
            @Nullable TraceConfig trace,
            @Nullable Boolean logWarnings
        ) {
            public record TraceConfig(
                @Nullable Integer attempts,
                @Nullable Duration interval,
                @Nullable String consistency
            ) {
            }
        }

        public record MetricsConfig(
            @Nullable NodeConfig node,
            @Nullable SessionConfig session
        ) {
            public record NodeConfig(
                @Nullable List<String> enabled,
                @Nullable Config cqlMessages
            ) {
            }

            public record SessionConfig(
                @Nullable List<String> enabled,
                @Nullable Config cqlRequests,
                @Nullable Config throttlingDelay
            ) {
            }

            public record Config(
                @Nullable Duration lowestLatency,
                @Nullable Duration highestLatency,
                @Nullable Integer significantDigits,
                @Nullable Duration refreshInterval
            ) {
            }
        }

        public record SocketConfig(
            @Nullable Boolean tcpNoDelay,
            @Nullable Boolean keepAlive,
            @Nullable Boolean reuseAddress,
            @Nullable Integer lingerInterval,
            @Nullable Integer receiveBufferSize,
            @Nullable Integer sendBufferSize
        ) {
        }

        public record HeartBeatConfig(
            @Nullable Duration interval,
            @Nullable Duration timeout
        ) {
        }

        public record MetadataConfig(
            @Nullable SchemaConfig schema,
            @Nullable TopologyConfig topologyEventDebouncer,
            @Nullable Boolean tokenMapEnabled
        ) {
            public record SchemaConfig(
                @Nullable Boolean enabled,
                @Nullable Duration requestTimeout,
                @Nullable Integer requestPageSize,
                @Nullable List<String> refreshedKeyspaces,
                @Nullable DebouncerConfig debouncer
            ) {
                public record DebouncerConfig(
                    @Nullable Duration window,
                    @Nullable Integer maxEvents
                ) {
                }
            }

            public record TopologyConfig(
                @Nullable Duration window,
                @Nullable Integer maxEvents
            ) {
            }
        }

        public record ControlConnectionConfig(
            @Nullable Duration timeout,
            @Nullable SchemaAgreementConfig schemaAgreement
        ) {
            public record SchemaAgreementConfig(
                @Nullable Duration interval,
                @Nullable Duration timeout,
                @Nullable Boolean warnOnFailure
            ) {
            }
        }

        public record PreparedStatementsConfig(
            @Nullable Boolean prepareOnAllNodes,
            @Nullable ReprepareConfig reprepareOnUp,
            @Nullable PreparedCacheConfig preparedCache
        ) {
            public record ReprepareConfig(
                @Nullable Boolean enabled,
                @Nullable Boolean checkSystemTable,
                @Nullable Integer maxStatements,
                @Nullable Integer maxParallelism,
                @Nullable Duration timeout
            ) {
            }

            public record PreparedCacheConfig(
                @Nullable Boolean weakValues
            ) {
            }
        }

        public record NettyConfig(
            @Nullable IoGroupConfig ioGroup,
            @Nullable AdminGroupConfig adminGroup,
            @Nullable TimerConfig timer,
            @Nullable Boolean daemon
        ) {
            public record IoGroupConfig(
                @Nullable Integer size,
                @Nullable ShutdownConfig shutdown
            ) {
            }

            public record AdminGroupConfig(
                @Nullable Integer size,
                @Nullable ShutdownConfig shutdown
            ) {
            }

            public record ShutdownConfig(
                @Nullable Integer quietPeriod,
                @Nullable Integer timeout,
                @Nullable String unit
            ) {
            }

            public record TimerConfig(
                @Nullable Duration tickDuration,
                @Nullable Integer ticksPerWheel
            ) {
            }
        }

        public record CoalescerConfig(
            @Nullable Duration rescheduleInterval
        ) {}

        public record ThrottlerConfig(
            @Nullable String throttlerClass,
            @Nullable Integer maxConcurrentRequests,
            @Nullable Integer maxRequestsPerSecond,
            @Nullable Integer maxQueueSize,
            @Nullable Duration drainInterval
        ) {}


    }
}
