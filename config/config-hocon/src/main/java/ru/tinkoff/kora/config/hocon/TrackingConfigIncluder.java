package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link ConfigIncluder} wrapper that records all file paths encountered
 * via {@code include file("...")} and {@code include "..."} directives during HOCON parsing.
 * <p>
 * Only tracks includes that resolve to actual files on the filesystem.
 * Classpath resources and URLs are ignored.
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
        var parseable = context.relativeTo(what);
        if (parseable != null) {
            var filename = parseable.origin().filename();
            if (filename != null) {
                var path = Path.of(filename).toAbsolutePath();
                if (Files.isRegularFile(path)) {
                    includedFiles.add(path);
                }
            }
        }
        return fallback.include(context, what);
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File what) {
        var path = what.toPath().toAbsolutePath();
        if (Files.isRegularFile(path)) {
            includedFiles.add(path);
        }
        if (fallback instanceof ConfigIncluderFile fileIncluder) {
            return fileIncluder.includeFile(context, what);
        }
        return fallback.include(context, what.getPath());
    }

    Set<Path> getIncludedFiles() {
        return Collections.unmodifiableSet(includedFiles);
    }
}
