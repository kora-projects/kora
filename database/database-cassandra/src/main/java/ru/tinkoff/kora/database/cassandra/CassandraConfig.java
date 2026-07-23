package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
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
@ConfigValueExtractor
public interface CassandraConfig {

    /**
     * @see ru.tinkoff.kora.database.cassandra.annotation.CassandraProfile
     */
    @Nullable
    Map<String, Profile> profiles();

    /**
     * @return Basic driver settings such as node addresses, datacenter, keyspace and request parameters.
     */
    Basic basic();

    /**
     * @return Advanced driver settings such as connections, SSL, protocol, metadata, metrics and throttling.
     */
    Advanced advanced();

    /**
     * @return Credentials used for authentication in Cassandra.
     */
    @Nullable
    CassandraCredentials auth();

    /**
     * @return Kora telemetry settings for executed queries.
     */
    TelemetryConfig telemetry();

    @ConfigValueExtractor
    interface CassandraCredentials {
        /**
         * @return Username for authentication in Cassandra.
         */
        String login();

        /**
         * @return Password for authentication in Cassandra.
         */
        String password();
    }

    @ConfigValueExtractor
    interface Profile {
        @ConfigValueExtractor
        interface ProfileBasic extends Basic {
            /**
             * @return Cassandra node addresses, which can not be overridden per profile and are always taken from the root configuration.
             */
            @Override
            @Nullable
            default List<String> contactPoints() {
                return List.of();
            }
        }

        /**
         * @return Basic driver settings overridden by this profile.
         */
        ProfileBasic basic();

        /**
         * @return Advanced driver settings overridden by this profile.
         */
        @Nullable
        Advanced advanced();
    }

    @ConfigValueExtractor
    interface Basic {
        /**
         * @return Regular request execution settings such as timeout, consistency level and page size.
         */
        @Nullable
        BasicRequestConfig request();

        /**
         * @return Driver session name used in logs, metrics and diagnostics.
         */
        @Nullable
        String sessionName();

        /**
         * @return Cassandra node addresses in host:port format.
         */
        List<String> contactPoints();

        /**
         * @return Local datacenter for the load balancing policy.
         */
        @Nullable
        String dc();

        /**
         * @return Keyspace that is set for the session after connection.
         */
        @Nullable
        String sessionKeyspace();

        /**
         * @return Basic load balancing policy settings.
         */
        @Nullable
        LoadBalancingPolicyConfig loadBalancingPolicy();

        /**
         * @return Settings for connecting to DataStax Astra or cloud Cassandra.
         */
        @Nullable
        CloudConfig cloud();

        @ConfigValueExtractor
        interface BasicRequestConfig {
            /**
             * @return Regular request execution timeout.
             */
            @Nullable
            Duration timeout();

            /**
             * @return Regular request consistency level, for example ONE, LOCAL_ONE, LOCAL_QUORUM, QUORUM or ALL.
             */
            @Nullable
            String consistency();

            /**
             * @return Result page size, meaning the maximum number of rows requested in one network round trip.
             */
            @Nullable
            Integer pageSize();

            /**
             * @return Serial consistency level for lightweight transactions (LWT), either SERIAL or LOCAL_SERIAL.
             */
            @Nullable
            String serialConsistency();

            /**
             * @return Default request idempotence that defines whether retries and speculative execution can be applied safely.
             */
            @Nullable
            Boolean defaultIdempotence();
        }

        @ConfigValueExtractor
        interface LoadBalancingPolicyConfig {
            /**
             * @return Enables slow replica avoidance in the default load balancing policy.
             */
            @Nullable
            Boolean slowReplicaAvoidance();
        }

        @ConfigValueExtractor
        interface CloudConfig {
            /**
             * @return Path or URL to the Secure Connect Bundle for connecting to DataStax Astra or cloud Cassandra.
             */
            @Nullable
            String secureConnectBundle();
        }
    }

    @ConfigValueExtractor
    interface Advanced {
        /**
         * @return Driver session leak detection settings.
         */
        @Nullable
        SessionLeakConfig sessionLeak();

        /**
         * @return Node connection and connection pool settings.
         */
        @Nullable
        ConnectionConfig connection();

        /**
         * @return Allows initialization retry when none of the contact points answer during startup.
         */
        @Nullable
        Boolean reconnectOnInit();

        /**
         * @return Reconnection policy delay settings.
         */
        @Nullable
        ReconnectionPolicyConfig reconnectionPolicy();

        /**
         * @return Advanced load balancing policy settings such as remote datacenter failover.
         */
        @Nullable
        AdvancedLoadBalancingPolicyConfig loadBalancingPolicy();

        /**
         * @return SSL/TLS settings for connections to Cassandra.
         */
        @Nullable
        SslEngineFactoryConfig sslEngineFactory();

        /**
         * @return Query timestamp generator settings.
         */
        @Nullable
        TimestampGeneratorConfig timestampGenerator();

        /**
         * @return Cassandra binary protocol settings.
         */
        @Nullable
        ProtocolConfig protocol();

        /**
         * @return Advanced request execution settings such as query tracing and warning logging.
         */
        @Nullable
        AdvancedRequestConfig request();

        /**
         * @return Driver metrics settings.
         */
        MetricsConfig metrics();

        /**
         * @return TCP socket settings for connections to Cassandra.
         */
        @Nullable
        SocketConfig socket();

        /**
         * @return Heartbeat settings for idle connections.
         */
        @Nullable
        HeartBeatConfig heartbeat();

        /**
         * @return Schema and cluster topology metadata settings.
         */
        @Nullable
        MetadataConfig metadata();

        /**
         * @return Service control connection settings.
         */
        @Nullable
        ControlConnectionConfig controlConnection();

        /**
         * @return Prepared statement preparation and caching settings.
         */
        @Nullable
        PreparedStatementsConfig preparedStatements();

        /**
         * @return Netty thread group and timer settings of the driver.
         */
        @Nullable
        NettyConfig netty();

        /**
         * @return Message coalescing settings applied before sending.
         */
        @Nullable
        CoalescerConfig coalescer();

        /**
         * @return Allows the driver to resolve contact points through DNS during startup.
         */
        @Nullable
        Boolean resolveContactPoints();

        /**
         * @return Driver request throttler settings.
         */
        @Nullable
        ThrottlerConfig throttler();

        @ConfigValueExtractor
        interface SessionLeakConfig {
            /**
             * @return Driver session leak warning threshold.
             */
            @Nullable
            Integer threshold();
        }

        @ConfigValueExtractor
        interface AdvancedLoadBalancingPolicyConfig {
            /**
             * @return Failover settings for remote datacenter nodes.
             */
            @Nullable
            DcFailover dcFailover();

            @ConfigValueExtractor
            interface DcFailover {
                /**
                 * @return Maximum number of remote datacenter nodes that can be used for failover.
                 */
                @Nullable
                Integer maxNodesPerRemoveDc();

                /**
                 * @return Allows failover to a remote datacenter for local consistency levels.
                 */
                @Nullable
                Boolean allowForLocalConsistencyLevels();
            }
        }

        @ConfigValueExtractor
        interface ConnectionConfig {
            /**
             * @return Timeout for opening a network connection to a node.
             */
            @Nullable
            Duration connectTimeout();

            /**
             * @return Timeout for requests that the driver executes while initializing a connection.
             */
            @Nullable
            Duration initQueryTimeout();

            /**
             * @return Timeout for setting the keyspace on a connection.
             */
            @Nullable
            Duration setKeyspaceTimeout();

            /**
             * @return Maximum number of simultaneous requests per connection.
             */
            @Nullable
            Integer maxRequestsPerConnection();

            /**
             * @return Maximum number of requests whose response is no longer awaited but may still complete inside the driver.
             */
            @Nullable
            Integer maxOrphanRequests();

            /**
             * @return Logs a warning when connection initialization fails for an individual node.
             */
            @Nullable
            Boolean warnOnInitError();

            /**
             * @return Connection pool size settings.
             */
            @Nullable
            PoolConfig pool();

            @ConfigValueExtractor
            interface PoolConfig {
                /**
                 * @return Connection pool size for local datacenter nodes.
                 */
                @Nullable
                Integer localSize();

                /**
                 * @return Connection pool size for remote nodes.
                 */
                @Nullable
                Integer remoteSize();
            }
        }

        @ConfigValueExtractor
        interface ReconnectionPolicyConfig {
            /**
             * @return Initial delay of the reconnection policy.
             */
            @Nullable
            Duration baseDelay();

            /**
             * @return Maximum delay of the reconnection policy.
             */
            @Nullable
            Duration maxDelay();
        }

        @ConfigValueExtractor
        interface SslEngineFactoryConfig {
            /**
             * @return Allowed cipher suites for SSL/TLS.
             */
            @Nullable
            List<String> cipherSuites();

            /**
             * @return Checks that the node hostname matches the SSL/TLS certificate.
             */
            @Nullable
            Boolean hostnameValidation();

            /**
             * @return Path to the client keystore.
             */
            @Nullable
            String keystorePath();

            /**
             * @return Client keystore password.
             */
            @Nullable
            String keystorePassword();

            /**
             * @return Path to the truststore.
             */
            @Nullable
            String truststorePath();

            /**
             * @return Truststore password.
             */
            @Nullable
            String truststorePassword();
        }

        @ConfigValueExtractor
        interface TimestampGeneratorConfig {
            /**
             * @return Forces Java system clock usage for query timestamp generation.
             */
            @Nullable
            Boolean forceJavaClock();

            /**
             * @return Timestamp drift warning settings.
             */
            @Nullable
            DriftWarningConfig driftWarning();

            @ConfigValueExtractor
            interface DriftWarningConfig {
                /**
                 * @return Warning threshold for timestamp drift into the future.
                 */
                @Nullable
                Duration threshold();

                /**
                 * @return Minimum interval between timestamp drift warnings.
                 */
                @Nullable
                Duration interval();
            }
        }

        @ConfigValueExtractor
        interface ProtocolConfig {
            /**
             * @return Cassandra binary protocol version, for example V4.
             */
            @Nullable
            String version();

            /**
             * @return Protocol compression algorithm, for example lz4 or snappy.
             */
            @Nullable
            String compression();

            /**
             * @return Maximum protocol frame size in bytes.
             */
            @Nullable
            Long maxFrameLength();
        }

        @ConfigValueExtractor
        interface AdvancedRequestConfig {
            /**
             * @return Logs a warning when a query explicitly changes the keyspace.
             */
            @Nullable
            Boolean warnIfSetKeyspace();

            /**
             * @return Settings for fetching query tracing information.
             */
            @Nullable
            TraceConfig trace();

            /**
             * @return Logs warnings returned by Cassandra along with a query response.
             */
            @Nullable
            Boolean logWarnings();

            @ConfigValueExtractor
            interface TraceConfig {
                /**
                 * @return Number of attempts to fetch query tracing information from Cassandra.
                 */
                @Nullable
                Integer attempts();

                /**
                 * @return Interval between attempts to fetch query tracing information.
                 */
                @Nullable
                Duration interval();

                /**
                 * @return Consistency level for queries to tracing tables.
                 */
                @Nullable
                String consistency();
            }
        }

        @ConfigValueExtractor
        interface MetricsConfig {

            /**
             * @return Driver metric identifier generator settings.
             */
            IdGenerator idGenerator();

            /**
             * @return Node level driver metrics settings.
             */
            @Nullable
            NodeConfig node();

            /**
             * @return Session level driver metrics settings.
             */
            @Nullable
            SessionConfig session();

            /**
             * @return Publishes percentile histograms for driver metrics.
             */
            default boolean publishPercentileHistogram() {
                return false;
            }

            @ConfigValueExtractor
            interface IdGenerator {

                /**
                 * @return Driver metric identifier generator name.
                 */
                default String name() {
                    return "TaggingMetricIdGenerator";
                }

                /**
                 * @return Driver metric name prefix.
                 */
                @Nullable
                String prefix();
            }

            @ConfigValueExtractor
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

                /**
                 * @return Histogram settings of the node level cqlMessages metric.
                 */
                Config cqlMessages();
            }

            @ConfigValueExtractor
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

                /**
                 * @return Histogram settings of the session level cqlRequests metric.
                 */
                Config cqlRequests();

                /**
                 * @return Histogram settings of the session level throttlingDelay metric.
                 */
                Config throttlingDelay();
            }

            @ConfigValueExtractor
            interface Config {

                /**
                 * @return Lowest expected latency of the metric histogram.
                 */
                default Duration lowestLatency() {
                    return Duration.ofMillis(1);
                }

                /**
                 * @return Highest expected latency of the metric histogram.
                 */
                default Duration highestLatency() {
                    return Duration.ofSeconds(90);
                }

                /**
                 * @return Number of significant digits of the metric histogram.
                 */
                @Nullable
                Integer significantDigits();

                /**
                 * @return Snapshot refresh interval of the metric histogram.
                 */
                @Nullable
                Duration refreshInterval();

                /**
                 * @return SLO boundaries of the metric.
                 */
                default double[] slo() {
                    return TelemetryConfig.MetricsConfig.DEFAULT_SLO;
                }
            }
        }

        @ConfigValueExtractor
        interface SocketConfig {
            /**
             * @return Enables TCP_NODELAY, which disables the Nagle algorithm.
             */
            @Nullable
            Boolean tcpNoDelay();

            /**
             * @return Enables SO_KEEPALIVE for TCP sockets.
             */
            @Nullable
            Boolean keepAlive();

            /**
             * @return Enables SO_REUSEADDR for TCP sockets.
             */
            @Nullable
            Boolean reuseAddress();

            /**
             * @return SO_LINGER value for TCP sockets.
             */
            @Nullable
            Integer lingerInterval();

            /**
             * @return TCP socket receive buffer size in bytes.
             */
            @Nullable
            Integer receiveBufferSize();

            /**
             * @return TCP socket send buffer size in bytes.
             */
            @Nullable
            Integer sendBufferSize();
        }

        @ConfigValueExtractor
        interface HeartBeatConfig {
            /**
             * @return Interval for sending a heartbeat over an idle connection.
             */
            @Nullable
            Duration interval();

            /**
             * @return Timeout for waiting for a heartbeat response.
             */
            @Nullable
            Duration timeout();
        }

        @ConfigValueExtractor
        interface MetadataConfig {
            /**
             * @return Schema metadata loading and refresh settings.
             */
            @Nullable
            SchemaConfig schema();

            /**
             * @return Coalescing settings for cluster topology change events.
             */
            @Nullable
            TopologyConfig topologyEventDebouncer();

            /**
             * @return Enables the token map used for routing requests by data owners.
             */
            @Nullable
            Boolean tokenMapEnabled();

            @ConfigValueExtractor
            interface SchemaConfig {
                /**
                 * @return Enables schema metadata loading and refresh.
                 */
                @Nullable
                Boolean enabled();

                /**
                 * @return Timeout for schema metadata queries.
                 */
                @Nullable
                Duration requestTimeout();

                /**
                 * @return Page size for schema metadata queries.
                 */
                @Nullable
                Integer requestPageSize();

                /**
                 * @return Keyspace names whose schema metadata is refreshed by the driver.
                 */
                @Nullable
                List<String> refreshedKeyspaces();

                /**
                 * @return Coalescing settings for schema refresh events.
                 */
                @Nullable
                DebouncerConfig debouncer();

                @ConfigValueExtractor
                interface DebouncerConfig {
                    /**
                     * @return Window for coalescing schema refresh events before processing.
                     */
                    @Nullable
                    Duration window();

                    /**
                     * @return Maximum number of schema refresh events that can be accumulated in the window.
                     */
                    @Nullable
                    Integer maxEvents();
                }
            }

            @ConfigValueExtractor
            interface TopologyConfig {
                /**
                 * @return Window for coalescing cluster topology change events before processing.
                 */
                @Nullable
                Duration window();

                /**
                 * @return Maximum number of topology change events that can be accumulated in the window.
                 */
                @Nullable
                Integer maxEvents();
            }
        }

        @ConfigValueExtractor
        interface ControlConnectionConfig {
            /**
             * @return Service control connection timeout.
             */
            @Nullable
            Duration timeout();

            /**
             * @return Schema agreement check settings between nodes.
             */
            @Nullable
            SchemaAgreementConfig schemaAgreement();

            @ConfigValueExtractor
            interface SchemaAgreementConfig {
                /**
                 * @return Interval for checking schema agreement between nodes.
                 */
                @Nullable
                Duration interval();

                /**
                 * @return Maximum time to wait for schema agreement.
                 */
                @Nullable
                Duration timeout();

                /**
                 * @return Logs a warning if schema agreement is not reached in time.
                 */
                @Nullable
                Boolean warnOnFailure();
            }
        }

        @ConfigValueExtractor
        interface PreparedStatementsConfig {
            /**
             * @return Prepares a statement on all nodes after it has been prepared successfully on one node.
             */
            @Nullable
            Boolean prepareOnAllNodes();

            /**
             * @return Statement re-preparation settings for a node that became available again.
             */
            @Nullable
            ReprepareConfig reprepareOnUp();

            /**
             * @return Prepared statement cache settings.
             */
            @Nullable
            PreparedCacheConfig preparedCache();

            @ConfigValueExtractor
            interface ReprepareConfig {
                /**
                 * @return Re-prepares statements on a node that became available again.
                 */
                @Nullable
                Boolean enabled();

                /**
                 * @return Checks the system.prepared_statements system table before re-preparing a statement.
                 */
                @Nullable
                Boolean checkSystemTable();

                /**
                 * @return Maximum number of statements to re-prepare, where 0 means no driver side limit.
                 */
                @Nullable
                Integer maxStatements();

                /**
                 * @return Maximum number of parallel re-prepare requests.
                 */
                @Nullable
                Integer maxParallelism();

                /**
                 * @return Timeout for re-preparing statements on one node.
                 */
                @Nullable
                Duration timeout();
            }

            @ConfigValueExtractor
            interface PreparedCacheConfig {
                /**
                 * @return Stores prepared statement cache values through weak references.
                 */
                @Nullable
                Boolean weakValues();
            }
        }

        @ConfigValueExtractor
        interface NettyConfig {
            /**
             * @return Netty thread group settings for network I/O.
             */
            @Nullable
            IoGroupConfig ioGroup();

            /**
             * @return Netty thread group settings for driver administrative tasks.
             */
            @Nullable
            AdminGroupConfig adminGroup();

            /**
             * @return Netty timer settings for delayed driver tasks.
             */
            @Nullable
            TimerConfig timer();

            /**
             * @return Makes Netty threads daemon threads.
             */
            @Nullable
            Boolean daemon();

            @ConfigValueExtractor
            interface IoGroupConfig {
                /**
                 * @return Number of Netty threads for network I/O, where 0 lets the driver choose automatically.
                 */
                @Nullable
                Integer size();

                /**
                 * @return Graceful shutdown settings of the network I/O thread group.
                 */
                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigValueExtractor
            interface AdminGroupConfig {
                /**
                 * @return Number of Netty threads for driver administrative tasks.
                 */
                @Nullable
                Integer size();

                /**
                 * @return Graceful shutdown settings of the administrative thread group.
                 */
                @Nullable
                ShutdownConfig shutdown();
            }

            @ConfigValueExtractor
            interface ShutdownConfig {
                /**
                 * @return Quiet period for graceful thread group shutdown.
                 */
                @Nullable
                Integer quietPeriod();

                /**
                 * @return Maximum wait time for thread group shutdown.
                 */
                @Nullable
                Integer timeout();

                /**
                 * @return Time unit of the thread group shutdown parameters.
                 */
                @Nullable
                String unit();
            }

            @ConfigValueExtractor
            interface TimerConfig {
                /**
                 * @return Duration of one Netty timer tick for delayed driver tasks.
                 */
                @Nullable
                Duration tickDuration();

                /**
                 * @return Number of ticks in the Netty timer wheel.
                 */
                @Nullable
                Integer ticksPerWheel();
            }
        }

        @ConfigValueExtractor
        interface CoalescerConfig {
            /**
             * @return Rescheduling interval for message coalescing before sending.
             */
            @Nullable
            Duration rescheduleInterval();
        }

        @ConfigValueExtractor
        interface ThrottlerConfig {
            /**
             * @return Driver request throttler class.
             */
            @Nullable
            String throttlerClass();

            /**
             * @return Maximum number of concurrent requests for the throttler.
             */
            @Nullable
            Integer maxConcurrentRequests();

            /**
             * @return Maximum number of requests per second for the throttler.
             */
            @Nullable
            Integer maxRequestsPerSecond();

            /**
             * @return Maximum throttler request queue size.
             */
            @Nullable
            Integer maxQueueSize();

            /**
             * @return Interval at which the throttler releases requests from the queue.
             */
            @Nullable
            Duration drainInterval();
        }
    }
}
