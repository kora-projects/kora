package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.database.cassandra.annotation.CassandraProfile;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * <b>Русский</b>: Конфигурация описывающая соединения к Cassandra базе данных.
 * <hr>
 * <b>English</b>: Configuration describing connections to the Cassandra database.
 *
 * @see CassandraRepository
 */
@ConfigMapper
public interface CassandraConfig {

    /**
     * @see CassandraProfile
     */
    @Nullable
    Map<String, Profile> profiles();

    Basic basic();

    Advanced advanced();

    @Nullable
    CassandraCredentials auth();

    DatabaseTelemetryConfig telemetry();

    @ConfigMapper
    interface CassandraCredentials {
        String login();

        String password();
    }

    @ConfigMapper
    interface Profile {
        @ConfigMapper
        interface ProfileBasic extends Basic {
            @Override
            @Nullable
            default List<String> contactPoints() {
                return List.of();
            }
        }

        ProfileBasic basic();

        @Nullable
        Advanced advanced();
    }

    @ConfigMapper
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

        @ConfigMapper
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

        @ConfigMapper
        interface LoadBalancingPolicyConfig {
            @Nullable
            Boolean slowReplicaAvoidance();
        }

        @ConfigMapper
        interface CloudConfig {
            @Nullable
            String secureConnectBundle();
        }
    }

    @ConfigMapper
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

        @ConfigMapper
        interface SessionLeakConfig {
            @Nullable
            Integer threshold();
        }

        @ConfigMapper
        interface AdvancedLoadBalancingPolicyConfig {
            @Nullable
            DcFailover dcFailover();

            @ConfigMapper
            interface DcFailover {
                @Nullable
                Integer maxNodesPerRemoveDc();

                @Nullable
                Boolean allowForLocalConsistencyLevels();
            }
        }

        @ConfigMapper
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

            @ConfigMapper
            interface PoolConfig {
                @Nullable
                Integer localSize();

                @Nullable
                Integer remoteSize();
            }
        }

        @ConfigMapper
        interface ReconnectionPolicyConfig {
            @Nullable
            Duration baseDelay();

            @Nullable
            Duration maxDelay();
        }

        @ConfigMapper
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

        @ConfigMapper
        interface TimestampGeneratorConfig {
            @Nullable
            Boolean forceJavaClock();

            @Nullable
            DriftWarningConfig driftWarning();

            @ConfigMapper
            interface DriftWarningConfig {
                @Nullable
                Duration threshold();

                @Nullable
                Duration interval();
            }
        }

        @ConfigMapper
        interface ProtocolConfig {
            @Nullable
            String version();

            @Nullable
            String compression();

            @Nullable
            Long maxFrameLength();
        }

        @ConfigMapper
        interface AdvancedRequestConfig {
            @Nullable
            Boolean warnIfSetKeyspace();

            @Nullable
            TraceConfig trace();

            @Nullable
            Boolean logWarnings();

            @ConfigMapper
            interface TraceConfig {
                @Nullable
                Integer attempts();

                @Nullable
                Duration interval();

                @Nullable
                String consistency();
            }
        }

        @ConfigMapper
        interface MetricsConfig {

            IdGenerator idGenerator();

            @Nullable
            NodeConfig node();

            @Nullable
            SessionConfig session();

            default boolean publishPercentileHistogram() {
                return false;
            }

            @ConfigMapper
            interface IdGenerator {

                default String name() {
                    return "TaggingMetricIdGenerator";
                }

                @Nullable
                String prefix();
            }

            @ConfigMapper
            interface NodeConfig {

                /**
                 * @see com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric
                 */
                default List<String> enabled() {
                    return List.of(
                        DefaultNodeMetric.OPEN_CONNECTIONS.getPath(),
                        DefaultNodeMetric.IN_FLIGHT.getPath(),
                        DefaultNodeMetric.BYTES_RECEIVED.getPath(),
                        DefaultNodeMetric.BYTES_SENT.getPath(),
                        DefaultNodeMetric.WRITE_TIMEOUTS.getPath(),
                        DefaultNodeMetric.READ_TIMEOUTS.getPath(),
                        DefaultNodeMetric.ABORTED_REQUESTS.getPath()
                    );
                }

                Config cqlMessages();
            }

            @ConfigMapper
            interface SessionConfig {

                /**
                 * @see com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric
                 */
                default List<String> enabled() {
                    return List.of(
                        DefaultSessionMetric.CONNECTED_NODES.getPath(),
                        DefaultSessionMetric.CQL_REQUESTS.getPath(),
                        DefaultSessionMetric.CQL_CLIENT_TIMEOUTS.getPath(),
                        DefaultSessionMetric.CQL_PREPARED_CACHE_SIZE.getPath(),
                        DefaultSessionMetric.THROTTLING_DELAY.getPath(),
                        DefaultSessionMetric.THROTTLING_QUEUE_SIZE.getPath()
                    );
                }

                Config cqlRequests();

                Config throttlingDelay();
            }

            @ConfigMapper
            interface Config {

                default Duration lowestLatency() {
                    return Duration.ofMillis(1);
                }

                default Duration highestLatency() {
                    return Duration.ofSeconds(90);
                }

                @Nullable
                Integer significantDigits();

                @Nullable
                Duration refreshInterval();

                default Duration[] slo() {
                    return TelemetryConfig.MetricsConfig.DEFAULT_SLO;
                }
            }
        }

        @ConfigMapper
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

        @ConfigMapper
        interface HeartBeatConfig {
            @Nullable
            Duration interval();

            @Nullable
            Duration timeout();
        }

        @ConfigMapper
        interface MetadataConfig {
            @Nullable
            SchemaConfig schema();

            @Nullable
            TopologyConfig topologyEventDebouncer();

            @Nullable
            Boolean tokenMapEnabled();

            @ConfigMapper
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

                @ConfigMapper
                interface DebouncerConfig {
                    @Nullable
                    Duration window();

                    @Nullable
                    Integer maxEvents();
                }
            }

            @ConfigMapper
            interface TopologyConfig {
                @Nullable
                Duration window();

                @Nullable
                Integer maxEvents();
            }
        }

        @ConfigMapper
        interface ControlConnectionConfig {
            @Nullable
            Duration timeout();

            @Nullable
            SchemaAgreementConfig schemaAgreement();

            @ConfigMapper
            interface SchemaAgreementConfig {
                @Nullable
                Duration interval();

                @Nullable
                Duration timeout();

                @Nullable
                Boolean warnOnFailure();
            }
        }

        @ConfigMapper
        interface PreparedStatementsConfig {
            @Nullable
            Boolean prepareOnAllNodes();

            @Nullable
            ReprepareConfig reprepareOnUp();

            @Nullable
            PreparedCacheConfig preparedCache();

            @ConfigMapper
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

            @ConfigMapper
            interface PreparedCacheConfig {
                @Nullable
                Boolean weakValues();
            }
        }

        @ConfigMapper
        interface NettyConfig {
            @Nullable
            IoGroupConfig ioGroup();

            @Nullable
            AdminGroupConfig adminGroup();

            @Nullable
            TimerConfig timer();

            @Nullable
            Boolean daemon();

            @ConfigMapper
            interface IoGroupConfig {
                @Nullable
                Integer size();

                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigMapper
            interface AdminGroupConfig {
                @Nullable
                Integer size();

                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigMapper
            interface ShutdownConfig {
                @Nullable
                Integer quietPeriod();

                @Nullable
                Integer timeout();

                @Nullable
                String unit();
            }

            @ConfigMapper
            interface TimerConfig {
                @Nullable
                Duration tickDuration();

                @Nullable
                Integer ticksPerWheel();
            }
        }

        @ConfigMapper
        interface CoalescerConfig {
            @Nullable
            Duration rescheduleInterval();
        }

        @ConfigMapper
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
