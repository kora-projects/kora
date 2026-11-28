package org.flywaydb.core.internal.parser;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.internal.nc.NativeConnectorsDatabase;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.resource.ResourceName;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ParsingContext {
    private static final Log LOG = LogFactory.getLog(ParsingContext.class);

    private static final String DEFAULT_SCHEMA_PLACEHOLDER = "defaultSchema";
    private static final String USER_PLACEHOLDER = "user";
    private static final String DATABASE_PLACEHOLDER = "database";
    private static final String TIMESTAMP_PLACEHOLDER = "timestamp";
    private static final String FILENAME_PLACEHOLDER = "filename";
    private static final String WORKING_DIRECTORY_PLACEHOLDER = "workingDirectory";
    private static final String TABLE_PLACEHOLDER = "table";
    private static final String ENVIRONMENT_PLACEHOLDER = "environment";

    private static final DateTimeFormatter AOT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, String> placeholders = new HashMap<>();
    private Database database;

    public Map<String, String> getPlaceholders() {
        return this.placeholders;
    }

    public Database getDatabase() {
        return this.database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    private String generateName(final String name, final Configuration configuration) {
        return "flyway" + configuration.getPlaceholderSeparator() + name;
    }

    public void populate(final NativeConnectorsDatabase database, final Configuration configuration) {
        String defaultSchemaName = configuration.getDefaultSchema();
        final String[] schemaNames = configuration.getSchemas();
        if (defaultSchemaName == null) {
            if (schemaNames.length > 0) {
                defaultSchemaName = schemaNames[0];
            }
        }

        if (defaultSchemaName != null) {
            placeholders.put(generateName(DEFAULT_SCHEMA_PLACEHOLDER, configuration), defaultSchemaName);
        }

        if (database.getDatabaseMetaData().databaseName() != null) {
            placeholders.put(generateName(DATABASE_PLACEHOLDER, configuration),
                database.getDatabaseMetaData().databaseName());
        }

        placeholders.put(generateName(USER_PLACEHOLDER, configuration), database.getCurrentUser());
        placeholders.put(generateName(TIMESTAMP_PLACEHOLDER, configuration), LocalDateTime.now().format(AOT_FORMATTER));
        placeholders.put(generateName(WORKING_DIRECTORY_PLACEHOLDER, configuration), System.getProperty("user.dir"));
        placeholders.put(generateName(TABLE_PLACEHOLDER, configuration), configuration.getTable());
        placeholders.put(generateName(ENVIRONMENT_PLACEHOLDER, configuration),
            configuration.getCurrentEnvironmentName());
    }

    public void populate(final Database database, final Configuration configuration) {
        setDatabase(database);

        String defaultSchemaName = configuration.getDefaultSchema();
        final String[] schemaNames = configuration.getSchemas();

        final Schema currentSchema = getCurrentSchema(database);
        final String catalog = database.getCatalog();
        final String currentUser = getCurrentUser(database);

        if (defaultSchemaName == null) {
            if (schemaNames.length > 0) {
                defaultSchemaName = schemaNames[0];
            } else {
                defaultSchemaName = currentSchema != null ? currentSchema.getName() : null;
            }
        }

        if (defaultSchemaName != null) {
            placeholders.put(generateName(DEFAULT_SCHEMA_PLACEHOLDER, configuration), defaultSchemaName);
        }

        if (catalog != null) {
            placeholders.put(generateName(DATABASE_PLACEHOLDER, configuration), catalog);
        }

        placeholders.put(generateName(USER_PLACEHOLDER, configuration), currentUser);
        placeholders.put(generateName(TIMESTAMP_PLACEHOLDER, configuration), LocalDateTime.now().format(AOT_FORMATTER));
        placeholders.put(generateName(WORKING_DIRECTORY_PLACEHOLDER, configuration),
            configuration.getWorkingDirectory() != null
                ? configuration.getWorkingDirectory()
                : System.getProperty("user.dir"));
        placeholders.put(generateName(TABLE_PLACEHOLDER, configuration), configuration.getTable());
        placeholders.put(generateName(ENVIRONMENT_PLACEHOLDER, configuration),
            configuration.getCurrentEnvironmentName());
    }

    public void updateFilenamePlaceholder(final ResourceName resourceName, final Configuration configuration) {
        final String filenamePlaceholder = generateName(FILENAME_PLACEHOLDER, configuration);
        if (resourceName.isValid()) {
            placeholders.put(filenamePlaceholder, resourceName.getFilename());
        } else {
            placeholders.remove(filenamePlaceholder);
        }
    }

    private Schema getCurrentSchema(final Database database) {
        try {
            return database.getMainConnection().getCurrentSchema();
        } catch (FlywayException e) {
            LOG.debug("Could not get schema for " + DEFAULT_SCHEMA_PLACEHOLDER + " placeholder.");
            return null;
        }
    }

    private String getCurrentUser(final Database database) {
        try {
            return database.getCurrentUser();
        } catch (FlywayException e) {
            LOG.debug("Could not get user for " + USER_PLACEHOLDER + " placeholder.");
            return null;
        }
    }
}
