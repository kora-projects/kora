package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigObject;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link ConfigIncluder} wrapper that records all file paths encountered
 * via {@code include file("...")} directives during HOCON parsing.
 * <p>
 * Delegates actual include resolution to the fallback (default) includer.
 */
final class TrackingConfigIncluder implements ConfigIncluder, ConfigIncluderFile {

    private ConfigIncluder fallback;
    private final Set<Path> includedFiles = new LinkedHashSet<>();

    @Override
    public ConfigIncluder withFallback(ConfigIncluder fallback) {
        if (this.fallback == fallback) {
            return this;
        }
        this.fallback = fallback;
        return this;
    }

    @Override
    public ConfigObject include(ConfigIncludeContext context, String what) {
        return fallback.include(context, what);
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File what) {
        includedFiles.add(what.toPath().toAbsolutePath());
        if (fallback instanceof ConfigIncluderFile fileIncluder) {
            return fileIncluder.includeFile(context, what);
        }
        return fallback.include(context, what.getPath());
    }

    Set<Path> getIncludedFiles() {
        return Collections.unmodifiableSet(includedFiles);
    }
}
