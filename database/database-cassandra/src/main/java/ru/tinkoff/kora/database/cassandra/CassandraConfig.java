package ru.tinkoff.kora.database.cassandra;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigValueExtractor
public interface CassandraConfig {
    @Nullable
    Map<String, Profile> profiles();

    Basic basic();

    @Nullable
    Advanced advanced();

    @Nullable
    CassandraCredentials auth();

    @ConfigValueExtractor
    interface CassandraCredentials {
        String login();

        String password();
    }

    @ConfigValueExtractor
    interface Profile {
        Basic basic();

        @Nullable
        Advanced advanced();
    }

    @ConfigValueExtractor
    interface Basic {
        @Nullable
        BasicRequestConfig request();

        @Nullable
        String sessionName();

        List<String> contactPoints();

        @Nullable
        String dc();

        @Nullable
        String sessionKeyspace();

        @Nullable
        LoadBalancingPolicyConfig loadBalancingPolicy();

        @Nullable
        CloudConfig cloud();

        @ConfigValueExtractor
        interface BasicRequestConfig {
            @Nullable
            Duration timeout();

            @Nullable
            String consistency();

            @Nullable
            Integer pageSize();

            @Nullable
            String serialConsistency();

            @Nullable
            Boolean defaultIdempotence();
        }

        @ConfigValueExtractor
        interface LoadBalancingPolicyConfig {
            @Nullable
            Boolean slowReplicaAvoidance();
        }

        @ConfigValueExtractor
        interface CloudConfig {
            @Nullable
            String secureConnectBundle();
        }
    }

    @ConfigValueExtractor
    interface Advanced {
        @Nullable
        SessionLeakConfig sessionLeak();

        @Nullable
        ConnectionConfig connection();

        @Nullable
        Boolean reconnectOnInit();

        @Nullable
        ReconnectionPolicyConfig reconnectionPolicy();

        @Nullable
        AdvancedLoadBalancingPolicyConfig loadBalancingPolicy();

        @Nullable
        SslEngineFactoryConfig sslEngineFactory();

        @Nullable
        TimestampGeneratorConfig timestampGenerator();

        @Nullable
        ProtocolConfig protocol();

        @Nullable
        AdvancedRequestConfig request();

        @Nullable
        MetricsConfig metrics();

        @Nullable
        SocketConfig socket();

        @Nullable
        HeartBeatConfig heartbeat();

        @Nullable
        MetadataConfig metadata();

        @Nullable
        ControlConnectionConfig controlConnection();

        @Nullable
        PreparedStatementsConfig preparedStatements();

        @Nullable
        NettyConfig netty();

        @Nullable
        CoalescerConfig coalescer();

        @Nullable
        Boolean resolveContactPoints();

        @Nullable
        ThrottlerConfig throttler();

        @ConfigValueExtractor
        interface SessionLeakConfig {
            @Nullable
            Integer threshold();
        }

        @ConfigValueExtractor
        interface AdvancedLoadBalancingPolicyConfig {
            @Nullable
            DcFailover dcFailover();

            @ConfigValueExtractor
            interface DcFailover {
                @Nullable
                Integer maxNodesPerRemoveDc();

                @Nullable
                Boolean allowForLocalConsistencyLevels();
            }
        }

        @ConfigValueExtractor
        interface ConnectionConfig {
            @Nullable
            Duration connectTimeout();

            @Nullable
            Duration initQueryTimeout();

            @Nullable
            Duration setKeyspaceTimeout();

            @Nullable
            Integer maxRequestsPerConnection();

            @Nullable
            Integer maxOrphanRequests();

            @Nullable
            Boolean warnOnInitError();

            @Nullable
            PoolConfig pool();

            @ConfigValueExtractor
            interface PoolConfig {
                @Nullable
                Integer localSize();

                @Nullable
                Integer remoteSize();
            }
        }

        @ConfigValueExtractor
        interface ReconnectionPolicyConfig {
            @Nullable
            Duration baseDelay();

            @Nullable
            Duration maxDelay();
        }

        @ConfigValueExtractor
        interface SslEngineFactoryConfig {
            @Nullable
            List<String> cipherSuites();

            @Nullable
            Boolean hostnameValidation();

            @Nullable
            String keystorePath();

            @Nullable
            String keystorePassword();

            @Nullable
            String truststorePath();

            @Nullable
            String truststorePassword();
        }

        @ConfigValueExtractor
        interface TimestampGeneratorConfig {
            @Nullable
            Boolean forceJavaClock();

            @Nullable
            DriftWarningConfig driftWarning();

            interface DriftWarningConfig {
                @Nullable
                Duration threshold();

                @Nullable
                Duration interval();
            }
        }

        @ConfigValueExtractor
        interface ProtocolConfig {
            @Nullable
            String version();

            @Nullable
            String compression();

            @Nullable
            Long maxFrameLength();
        }

        @ConfigValueExtractor
        interface AdvancedRequestConfig {
            @Nullable
            Boolean warnIfSetKeyspace();

            @Nullable
            TraceConfig trace();

            @Nullable
            Boolean logWarnings();

            @ConfigValueExtractor
            interface TraceConfig {
                @Nullable
                Integer attempts();

                @Nullable
                Duration interval();

                @Nullable
                String consistency();
            }
        }

        @ConfigValueExtractor
        interface MetricsConfig {
            @Nullable
            NodeConfig node();

            @Nullable
            SessionConfig session();

            @ConfigValueExtractor
            interface NodeConfig {
                @Nullable
                List<String> enabled();

                @Nullable
                Config cqlMessages();
            }

            @ConfigValueExtractor
            interface SessionConfig {
                @Nullable
                List<String> enabled();

                @Nullable
                Config cqlRequests();

                @Nullable
                Config throttlingDelay();
            }

            @ConfigValueExtractor
            interface Config {
                @Nullable
                Duration lowestLatency();

                @Nullable
                Duration highestLatency();

                @Nullable
                Integer significantDigits();

                @Nullable
                Duration refreshInterval();
            }
        }

        @ConfigValueExtractor
        interface SocketConfig {
            @Nullable
            Boolean tcpNoDelay();

            @Nullable
            Boolean keepAlive();

            @Nullable
            Boolean reuseAddress();

            @Nullable
            Integer lingerInterval();

            @Nullable
            Integer receiveBufferSize();

            @Nullable
            Integer sendBufferSize();
        }

        @ConfigValueExtractor
        interface HeartBeatConfig {
            @Nullable
            Duration interval();

            @Nullable
            Duration timeout();
        }

        @ConfigValueExtractor
        interface MetadataConfig {
            @Nullable
            SchemaConfig schema();

            @Nullable
            TopologyConfig topologyEventDebouncer();

            @Nullable
            Boolean tokenMapEnabled();

            @ConfigValueExtractor
            interface SchemaConfig {
                @Nullable
                Boolean enabled();

                @Nullable
                Duration requestTimeout();

                @Nullable
                Integer requestPageSize();

                @Nullable
                List<String> refreshedKeyspaces();

                @Nullable
                DebouncerConfig debouncer();

                @ConfigValueExtractor
                interface DebouncerConfig {
                    @Nullable
                    Duration window();

                    @Nullable
                    Integer maxEvents();
                }
            }

            @ConfigValueExtractor
            interface TopologyConfig {
                @Nullable
                Duration window();

                @Nullable
                Integer maxEvents();
            }
        }

        @ConfigValueExtractor
        interface ControlConnectionConfig {
            @Nullable
            Duration timeout();

            @Nullable
            SchemaAgreementConfig schemaAgreement();

            @ConfigValueExtractor
            interface SchemaAgreementConfig {
                @Nullable
                Duration interval();

                @Nullable
                Duration timeout();

                @Nullable
                Boolean warnOnFailure();
            }
        }

        @ConfigValueExtractor
        interface PreparedStatementsConfig {
            @Nullable
            Boolean prepareOnAllNodes();

            @Nullable
            ReprepareConfig reprepareOnUp();

            @Nullable
            PreparedCacheConfig preparedCache();

            @ConfigValueExtractor
            interface ReprepareConfig {
                @Nullable
                Boolean enabled();

                @Nullable
                Boolean checkSystemTable();

                @Nullable
                Integer maxStatements();

                @Nullable
                Integer maxParallelism();

                @Nullable
                Duration timeout();
            }

            @ConfigValueExtractor
            interface PreparedCacheConfig {
                @Nullable
                Boolean weakValues();
            }
        }

        @ConfigValueExtractor
        interface NettyConfig {
            @Nullable
            IoGroupConfig ioGroup();

            @Nullable
            AdminGroupConfig adminGroup();

            @Nullable
            TimerConfig timer();

            @Nullable
            Boolean daemon();

            @ConfigValueExtractor
            interface IoGroupConfig {
                @Nullable
                Integer size();

                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigValueExtractor
            interface AdminGroupConfig {
                @Nullable
                Integer size();

                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigValueExtractor
            interface ShutdownConfig {
                @Nullable
                Integer quietPeriod();

                @Nullable
                Integer timeout();

                @Nullable
                String unit();
            }

            @ConfigValueExtractor
            interface TimerConfig {
                @Nullable
                Duration tickDuration();

                @Nullable
                Integer ticksPerWheel();
            }
        }

        @ConfigValueExtractor
        interface CoalescerConfig {
            @Nullable
            Duration rescheduleInterval();
        }

        @ConfigValueExtractor
        interface ThrottlerConfig {
            @Nullable
            String throttlerClass();

            @Nullable
            Integer maxConcurrentRequests();

            @Nullable
            Integer maxRequestsPerSecond();

            @Nullable
            Integer maxQueueSize();

            @Nullable
            Duration drainInterval();
        }
    }
}
