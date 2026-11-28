package io.koraframework.config.aot.generated;

import org.flywaydb.core.*;
import org.flywaydb.core.api.*;
import org.flywaydb.core.api.configuration.*;
import org.flywaydb.core.api.pattern.*;
import org.flywaydb.core.internal.configuration.models.*;
import org.flywaydb.core.internal.configuration.resolvers.*;
import org.flywaydb.core.internal.plugin.*;

import java.util.*;

public final class AotClassicConfiguration extends ClassicConfiguration {

    private static MigrationPattern[] EMPTY_CHERRY_PICK = new MigrationPattern[0];
    private static ValidatePattern[] EMPTY_IGNORE_PATTERNS = new ValidatePattern[0];

    private ResolvedEnvironment aotResolvedEnvironment;
    private PluginRegister aotPluginRegister;

    public AotClassicConfiguration(ConfigurationModel modernConfig) {
        super(modernConfig);
    }

    public void setAotPluginRegister(PluginRegister pluginRegister) {
        this.aotPluginRegister = pluginRegister;
        this.setPluginRegister(pluginRegister);
    }

    @Override
    public PluginRegister getPluginRegister() {
        return this.aotPluginRegister != null ? this.aotPluginRegister : super.getPluginRegister();
    }

    @Override
    public ResolvedEnvironment getResolvedEnvironment(String envName, ProvisionerMode provisionerMode, ProgressLogger progress) {
        if (aotResolvedEnvironment == null) {
            var schemas = new ArrayList<String>();
            schemas.add("public");

            aotResolvedEnvironment = new ResolvedEnvironment(
                null,
                null,
                null,
                null,
                schemas,
                0,
                120,
                null,
                new HashMap<>(),
                provisionerMode,
                null
            );
        }
        return aotResolvedEnvironment;
    }

    @Override
    public MigrationPattern[] getCherryPick() {
        var rs = EMPTY_CHERRY_PICK;
        EMPTY_CHERRY_PICK = null;
        return rs;
    }

    @Override
    public ValidatePattern[] getIgnoreMigrationPatterns() {
        var rs =  EMPTY_IGNORE_PATTERNS;
        EMPTY_IGNORE_PATTERNS = null;
        return rs;
    }
}
