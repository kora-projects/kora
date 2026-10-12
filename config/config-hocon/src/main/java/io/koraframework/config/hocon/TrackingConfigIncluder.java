package io.koraframework.config.hocon;

import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigObject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link ConfigIncluder} wrapper that records all file paths encountered
 * via {@code include file("...")} and {@code include "..."} directives during HOCON parsing.
 * <p>
 * Tracks includes that resolve to files on the filesystem, including ones that do not exist yet.
 * Classpath resources and URLs are ignored.
 * <p>
 * Delegates actual include resolution to the fallback (default) includer.
 */
final class TrackingConfigIncluder implements ConfigIncluder, ConfigIncluderFile {

    private @Nullable ConfigIncluder fallback;
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
        var fallback = Objects.requireNonNull(this.fallback);
        if (!isUrl(what)) {
            // the default includer falls back to the classpath when the file next to the including one is missing,
            // so the directory is taken from "." which always exists; it has no file name for classpath and url parents
            var dir = context.relativeTo(".");
            var filename = dir == null ? null : dir.origin().filename();
            if (filename != null) {
                track(Path.of(filename).toAbsolutePath().normalize(), what);
            }
        }
        return fallback.include(context, what);
    }

    @Override
    public ConfigObject includeFile(ConfigIncludeContext context, File what) {
        var fallback = Objects.requireNonNull(this.fallback);
        track(Path.of(""), what.getPath());
        if (fallback instanceof ConfigIncluderFile fileIncluder) {
            return fileIncluder.includeFile(context, what);
        }
        return fallback.include(context, what.getPath());
    }

    /**
     * Records the files the way HOCON names them, even if they do not exist yet, so that the watcher picks them up
     * once they are created: a name with a .conf/.json/.properties extension is that one file,
     * any other name is loaded from name.conf, name.json and name.properties.
     */
    private void track(Path dir, String what) {
        var names = what.endsWith(".conf") || what.endsWith(".json") || what.endsWith(".properties")
            ? List.of(what)
            : List.of(what + ".conf", what + ".json", what + ".properties");
        for (var name : names) {
            try {
                var path = dir.resolve(name).toAbsolutePath();
                if (!Files.isDirectory(path)) {
                    includedFiles.add(path);
                }
            } catch (InvalidPathException e) {
                // not a valid path on this OS, HOCON treats it as a missing file, and tracking must not fail parsing
            }
        }
    }

    @SuppressWarnings("deprecation") // the same check the default includer makes
    private static boolean isUrl(String what) {
        try {
            new URL(what);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    Set<Path> getIncludedFiles() {
        return Collections.unmodifiableSet(includedFiles);
    }
}
