package io.koraframework.config.aot.generated;

import io.koraframework.application.graph.*;
import io.koraframework.common.annotation.*;
import io.koraframework.common.util.*;
import io.koraframework.config.common.*;
import io.koraframework.config.common.mapper.*;
import io.koraframework.database.flyway.*;
import io.koraframework.database.jdbc.*;
import org.flywaydb.core.api.*;
import org.flywaydb.core.api.callback.*;
import org.flywaydb.core.api.configuration.*;
import org.flywaydb.core.api.exception.*;
import org.flywaydb.core.api.executor.*;
import org.flywaydb.core.api.logging.*;
import org.flywaydb.core.api.migration.baseline.*;
import org.flywaydb.core.api.pattern.*;
import org.flywaydb.core.api.resolver.*;
import org.flywaydb.core.api.resource.*;
import org.flywaydb.core.extensibility.*;
import org.flywaydb.core.internal.callback.*;
import org.flywaydb.core.internal.clazz.*;
import org.flywaydb.core.internal.command.*;
import org.flywaydb.core.internal.command.clean.*;
import org.flywaydb.core.internal.configuration.models.*;
import org.flywaydb.core.internal.database.*;
import org.flywaydb.core.internal.database.base.*;
import org.flywaydb.core.internal.jdbc.*;
import org.flywaydb.core.internal.logging.slf4j.*;
import org.flywaydb.core.internal.parser.*;
import org.flywaydb.core.internal.plugin.*;
import org.flywaydb.core.internal.proprietaryStubs.*;
import org.flywaydb.core.internal.resolver.*;
import org.flywaydb.core.internal.schemahistory.*;
import org.flywaydb.core.internal.util.*;
import org.flywaydb.database.postgresql.*;
import org.slf4j.*;

import java.sql.*;
import java.util.*;
import java.util.Date;

public final class AotFlywayFactoryModule {

    private final String configPath;

    public AotFlywayFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public FlywayConfig flywayConfig(Config config, ConfigValueMapper<FlywayConfig> mapper) {
        return mapper.mapOrThrow(config.get(configPath));
    }

    @Tag(Tag.Factory.class)
    public AotFlywayJdbcDatabaseInterceptor flywayInterceptor(@Tag(Tag.Factory.class) FlywayConfig flywayConfig) {
        return new AotFlywayJdbcDatabaseInterceptor(flywayConfig);
    }

    public static final class AotFlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDataSource> {

        private static final Logger logger = LoggerFactory.getLogger(AotFlywayJdbcDatabaseInterceptor.class);
        private final FlywayConfig flywayConfig;

        public AotFlywayJdbcDatabaseInterceptor(FlywayConfig flywayConfig) {
            this.flywayConfig = flywayConfig;
        }

        @Override
        public JdbcDataSource afterInit(JdbcDataSource value) {
            if (!flywayConfig.enabled()) {
                logger.debug("FlyWay is disabled, skipping migrate...");
                return value;
            }

            final long started = TimeUtils.started();
            logger.debug("AOT FlyWay migration applying (zero run-time scanning)...");

            LogFactory.setLogCreator(new Slf4jLogCreator());

            var emptyResourceProvider = new AotEmptyResourceProvider();
            var baselineExt = new BaselineMigrationConfigurationExtension();
            baselineExt.setBaselineMigrationPrefix("B");

            var modernConfig = ConfigurationModel.defaults();
            var aotEnvironment = new EnvironmentModel();
            modernConfig.setEnvironments(Map.of("default", aotEnvironment));
            modernConfig.getFlyway().setEnvironment("default");

            var config = new AotClassicConfiguration(modernConfig);
            var databaseType = new PostgreSQLDatabaseType();
            var aotRegister = new AotPostgresPluginRegister(baselineExt, databaseType);
            config.setAotPluginRegister(aotRegister);
            config.setPluginRegister(aotRegister);

            config.setReportEnabled(false);

            config.setDataSource(value.value());
            config.setMixed(flywayConfig.mixed());
            config.setExecuteInTransaction(flywayConfig.executeInTransaction());
            config.setValidateOnMigrate(flywayConfig.validateOnMigrate());
            config.setResourceProvider(emptyResourceProvider);
            config.setGroup(false);

            config.setSkipDefaultResolvers(true);
            config.setResolvers(new AotStaticMigrationResolver(config));
            config.setLoggers("slf4j");
            config.setFailOnMissingLocations(false);

            var jdbcConnectionFactory = new JdbcConnectionFactory(
                value.value(),
                config,
                null
            );

            var parsingContext = new ParsingContext();
            var sqlScriptFactory = databaseType.createSqlScriptFactory(config, parsingContext);

            Database database = null;
            try {
                database = databaseType.createDatabase(config, jdbcConnectionFactory, null);
                parsingContext.populate(database, config);

                var schemasPair = SchemaHistoryFactory.prepareSchemas(config, database);
                var defaultSchema = schemasPair.getLeft();
                var schemas = schemasPair.getRight().toArray(new Schema[0]);
                var noCallbackSqlScriptExecutorFactory = databaseType.createSqlScriptExecutorFactory(
                    jdbcConnectionFactory,
                    NoopCallbackExecutor.INSTANCE,
                    null
                );
                var schemaHistory = SchemaHistoryFactory.getSchemaHistory(
                    config,
                    noCallbackSqlScriptExecutorFactory,
                    sqlScriptFactory,
                    database,
                    defaultSchema,
                    null
                );
                var resolver = new CompositeMigrationResolver(
                    emptyResourceProvider,
                    NoopClassProvider.INSTANCE,
                    config,
                    noCallbackSqlScriptExecutorFactory,
                    sqlScriptFactory,
                    parsingContext,
                    null,
                    config.getResolvers()
                );

                var callbackExecutor = NoopCallbackExecutor.INSTANCE;

                switch (flywayConfig.mode()) {
                    case MIGRATE:
                        if (!schemaHistory.exists()) {
                            schemaHistory.create(false);
                        }
                        validateBeforeMigrate(database, schemaHistory, defaultSchema, resolver, config, callbackExecutor);
                        var dbMigrate = new DbMigrate(database, schemaHistory, defaultSchema, resolver, config, callbackExecutor);
                        dbMigrate.migrate();
                        break;

                    case REPAIR:
                        var dbRepair = new DbRepair(database, resolver, schemaHistory, callbackExecutor, config);
                        dbRepair.repair();
                        break;

                    case CLEAN_MIGRATE:
                        var dbClean = new DbClean(database, schemaHistory, defaultSchema, schemas, callbackExecutor, config);
                        dbClean.clean();

                        if (!schemaHistory.exists()) {
                            schemaHistory.create(false);
                        }
                        validateBeforeMigrate(database, schemaHistory, defaultSchema, resolver, config, callbackExecutor);
                        var dbMigrateClean = new DbMigrate(database, schemaHistory, defaultSchema, resolver, config, callbackExecutor);
                        dbMigrateClean.migrate();
                        break;
                }
            } finally {
                IOUtils.close(database);
            }

            logger.debug("AOT FlyWay migration in mode '{}' applied in {}", flywayConfig.mode(), TimeUtils.tookForLogging(started));
            return value;
        }

        @Override
        public JdbcDataSource beforeRelease(JdbcDataSource value) {
            return value;
        }

        private void validateBeforeMigrate(Database database, SchemaHistory schemaHistory, Schema defaultSchema, CompositeMigrationResolver resolver, Configuration config, CallbackExecutor<Event> callbackExecutor) {
            if (!flywayConfig.validateOnMigrate()) {
                return;
            }
            logger.debug("AOT FlyWay validating applied migrations...");

            ValidatePattern[] ignorePatterns = {ValidatePattern.fromPattern("*:pending")};

            var dbValidate = new DbValidate(database, schemaHistory, defaultSchema, resolver, config, callbackExecutor, ignorePatterns);

            var validateResult = dbValidate.validate();

            if (!validateResult.validationSuccessful && validateResult.errorDetails != null) {
                throw new FlywayValidateException(validateResult.errorDetails, validateResult.getAllErrorMessages());
            }
            logger.debug("AOT FlyWay validation successful.");
        }
    }

    private static final class AotEmptyResourceProvider implements ResourceProvider {
        @Override
        public LoadableResource getResource(String location) {
            return null;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return Collections.emptyList();
        }
    }

    private static final class AotStaticMigrationResolver implements MigrationResolver {
        private final Configuration configuration;

        public AotStaticMigrationResolver(Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        public Collection<ResolvedMigration> resolveMigrations(Context context) {
            var result = new ArrayList<ResolvedMigration>();

            for (var generated : AotFlywayMigrations.getMigrations()) {
                var migration = new ResolvedMigrationImpl(
                    MigrationVersion.fromVersion(generated.version()),
                    generated.description(),
                    generated.filename(),
                    generated.checksum(),
                    generated.checksum(),
                    CoreMigrationType.SQL,
                    generated.physicalLocation(),
                    new AotResourceExecutor(generated)
                );
                result.add(migration);
            }

            return result;
        }
    }

    private static final class AotResourceExecutor implements MigrationExecutor {
        private final AotFlywayMigrations.MigrationData migration;

        public AotResourceExecutor(AotFlywayMigrations.MigrationData migration) {
            this.migration = migration;
        }

        @Override
        public List<Results> execute(org.flywaydb.core.api.executor.Context context) throws SQLException {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute(migration.content());
            }
            return Collections.emptyList();
        }

        @Override
        public boolean canExecuteInTransaction() {
            return true;
        }

        @Override
        public boolean shouldExecute() {
            return true;
        }
    }

    private static final class AotPostgresPluginRegister extends PluginRegister {
        private final Plugin[] aotPlugins;
        private final Map<Class<?>, Object> exactCache;
        private final Map<String, Plugin> canonicalNameCache;
        private final Map<String, Plugin> simpleNameCache;
        private final Map<Class<?>, List<Plugin>> instancesCache;


        public AotPostgresPluginRegister(BaselineMigrationConfigurationExtension baselineExt, Plugin postgresDatabaseType) {
            super();

            var ext = new PostgreSQLConfigurationExtension();
            var sqlMig = new AotSqlAppliedMigration();
            var baseMig = new BaselineAppliedMigration();
            var cherrySupport = new CherryPickSupportStub();
            var errorOverride = new ErrorOverridesSupportStub();
            var execStrategy = new DefaultExecutionStrategy();

            aotPlugins = new Plugin[] {
                postgresDatabaseType,
                ext,
                baselineExt,
                sqlMig,
                baseMig,
                cherrySupport
            };

            exactCache = new HashMap<>();
            exactCache.put(postgresDatabaseType.getClass(), postgresDatabaseType);
            exactCache.put(ext.getClass(), ext);
            exactCache.put(baselineExt.getClass(), baselineExt);
            exactCache.put(sqlMig.getClass(), sqlMig);
            exactCache.put(baseMig.getClass(), baseMig);
            exactCache.put(cherrySupport.getClass(), cherrySupport);
            exactCache.put(DatabaseType.class, postgresDatabaseType);
            exactCache.put(ConfigurationExtension.class, ext);
            exactCache.put(ErrorOverridesSupport.class, errorOverride);
            exactCache.put(DefaultExecutionStrategy.class, execStrategy);

            canonicalNameCache = new HashMap<>(16);
            simpleNameCache = new HashMap<>(16);
            for (var plugin : aotPlugins) {
                Class<?> cls = plugin.getClass();
                String canonical = cls.getCanonicalName();
                if (canonical != null) {
                    canonicalNameCache.put(canonical, plugin);
                }
                simpleNameCache.put(cls.getSimpleName(), plugin);
            }

            instancesCache = new HashMap<>(32);
            Class<?>[] interfacesToCache = { Plugin.class, DatabaseType.class, ConfigurationExtension.class, AppliedMigration.class };
            for (var iface : interfacesToCache) {
                var list = new ArrayList<Plugin>();
                for (var plugin : aotPlugins) {
                    if (iface.isInstance(plugin)) {
                        list.add(plugin);
                    }
                }
                instancesCache.put(iface, Collections.unmodifiableList(list));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> T getExact(Class<T> clazz) {
            var exact = exactCache.get(clazz);
            if (exact != null) {
                return (T) exact;
            }
            String targetName = clazz.getCanonicalName();
            if (targetName == null) {
                return null;
            }
            return (T) canonicalNameCache.get(targetName);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> List<T> getInstancesOf(Class<T> clazz) {
            var cachedList = instancesCache.get(clazz);
            if (cachedList != null) {
                return (List<T>) cachedList;
            }
            var exact = exactCache.get(clazz);
            if (exact != null) {
                return Collections.singletonList((T) exact);
            }
            var result = new ArrayList<T>(aotPlugins.length);
            for (int i = 0; i < aotPlugins.length; i++) {
                var plugin = aotPlugins[i];
                if (clazz.isInstance(plugin)) {
                    result.add((T) plugin);
                }
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> List<T> getLicensedInstancesOf(Class<T> clazz, Configuration configuration) {
            var result = new ArrayList<T>(aotPlugins.length);
            for (int i = 0; i < aotPlugins.length; i++) {
                var plugin = aotPlugins[i];
                if (clazz.isInstance(plugin)) {
                    if (plugin.isLicensed(configuration)) {
                        result.add((T) plugin);
                    }
                }
            }
            return result;
        }

        @Override
        public <T extends Plugin> T getLicensedInstanceOf(Class<T> clazz, Configuration configuration) {
            var exact = exactCache.get(clazz);
            if (exact != null) {
                var plugin = (Plugin) exact;
                if (plugin.isLicensed(configuration)) {
                    return (T) plugin;
                }
                return null;
            }

            for (int i = 0; i < aotPlugins.length; i++) {
                var plugin = aotPlugins[i];
                if (clazz.isInstance(plugin)) {
                    if (plugin.isLicensed(configuration)) {
                        return (T) plugin;
                    }
                }
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> T getInstanceOf(Class<T> clazz) {
            var exact = exactCache.get(clazz);
            if (exact != null) {
                return (T) exact;
            }

            for (int i = 0; i < aotPlugins.length; i++) {
                var plugin = aotPlugins[i];
                if (clazz.isInstance(plugin)) {
                    return (T) plugin;
                }
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> T getExact(String className) {
            return (T) simpleNameCache.get(className);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Plugin> T getLicensedExact(String className, Configuration configuration) {
            var plugin = simpleNameCache.get(className);
            if (plugin != null && plugin.isLicensed(configuration)) {
                return (T) plugin;
            }
            return null;
        }

        @Override
        public PluginRegister getCopy() {
            return this;
        }
    }

    private static final class AotSqlAppliedMigration extends BaseAppliedMigration {

        public AotSqlAppliedMigration() {
            super();
        }

        private AotSqlAppliedMigration(int installedRank,
                                       MigrationVersion version,
                                       String description,
                                       String type,
                                       String script,
                                       Integer checksum,
                                       Date installedOn,
                                       String installedBy,
                                       int executionTime,
                                       boolean success) {
            this.installedRank = installedRank;
            this.version = version;
            this.description = description;
            this.script = script;
            this.checksum = checksum;
            this.installedOn = installedOn;
            this.installedBy = installedBy;
            this.executionTime = executionTime;
            this.success = success;
            this.type = CoreMigrationType.SQL;
        }

        @Override
        public boolean handlesType(String type) {
            return "SQL".equalsIgnoreCase(type);
        }

        @Override
        public AppliedMigration create(int installedRank,
                                       MigrationVersion version,
                                       String description,
                                       String type,
                                       String script,
                                       Integer checksum,
                                       Date installedOn,
                                       String installedBy,
                                       int executionTime,
                                       boolean success) {
            return new AotSqlAppliedMigration(installedRank, version, description, type, script, checksum, installedOn, installedBy, executionTime, success);
        }
    }
}
