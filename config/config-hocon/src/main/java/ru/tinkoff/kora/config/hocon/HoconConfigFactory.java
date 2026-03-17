package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.impl.SimpleConfig;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

import jakarta.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;

public class HoconConfigFactory {
    public static Config fromHocon(ConfigOrigin origin, com.typesafe.config.Config config) {
        var object = config.root();
        var path = ConfigValuePath.root();

        var value = toObject(origin, object, path);
        return new SimpleConfig(origin, value);
    }

    @Nullable
    private static ConfigValue<?> toValue(ConfigOrigin origin, com.typesafe.config.ConfigValue object, ConfigValuePath path) {
        try {
            return switch (object.valueType()) {
                case OBJECT -> toObject(origin, (ConfigObject) object, path);
                case LIST -> toArray(origin, (ConfigList) object, path);
                case NUMBER -> new ConfigValue.NumberValue(new SimpleConfigValueOrigin(origin, path), (Number) object.unwrapped());
                case BOOLEAN -> new ConfigValue.BooleanValue(new SimpleConfigValueOrigin(origin, path), (Boolean) object.unwrapped());
                case NULL -> null;
                case STRING -> new ConfigValue.StringValue(new SimpleConfigValueOrigin(origin, path), (String) object.unwrapped());
            };
        } catch (ConfigException.NotResolved notResolved) {
            return new ConfigValue.StringValue(new SimpleConfigValueOrigin(origin, path), object.render(ConfigRenderOptions.concise().setJson(false)));
        }
    }

    private static ConfigValue.ObjectValue toObject(ConfigOrigin origin, ConfigObject object, ConfigValuePath path) {
        var result = new LinkedHashMap<String, ConfigValue<?>>();
        for (var entry : object.entrySet()) {
            var value = toValue(origin, entry.getValue(), path.child(entry.getKey()));
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), result);
    }

    private static ConfigValue.ArrayValue toArray(ConfigOrigin origin, ConfigList list, ConfigValuePath path) {
        var result = new ArrayList<ConfigValue<?>>(list.size());
        for (var i = 0; i < list.size(); i++) {
            var configValue = list.get(i);
            result.add(toValue(origin, configValue, path.child(i)));
        }
        return new ConfigValue.ArrayValue(new SimpleConfigValueOrigin(origin, path), List.copyOf(result));
    }

    /**
     * Walks the parsed Typesafe Config tree and collects all unique file paths
     * from which config values originated (including include files).
     */
    static Set<Path> extractIncludedFiles(com.typesafe.config.Config config) {
        var files = new LinkedHashSet<Path>();
        collectFileOrigins(config.root(), files);
        return files;
    }

    static ConfigOrigin enrichOriginWithIncludes(FileConfigOrigin baseOrigin, com.typesafe.config.Config parsedConfig) {
        var includedFiles = extractIncludedFiles(parsedConfig);
        includedFiles.remove(baseOrigin.path().toAbsolutePath());
        if (includedFiles.isEmpty()) {
            return baseOrigin;
        }
        var origins = new ArrayList<ConfigOrigin>();
        origins.add(baseOrigin);
        for (var includedFile : includedFiles) {
            if (Files.exists(includedFile)) {
                origins.add(new FileConfigOrigin(includedFile));
            }
        }
        if (origins.size() == 1) {
            return baseOrigin;
        }
        return new ContainerConfigOrigin(origins);
    }

    private static void collectFileOrigins(com.typesafe.config.ConfigValue value, Set<Path> files) {
        var origin = value.origin();
        var filename = origin.filename();
        // Typesafe Config 1.4.4 creates synthetic origins like "merge of file1, file2" when merging values
        if (filename != null && !filename.startsWith("merge of ")) {
            files.add(Path.of(filename).toAbsolutePath());
        }
        if (value instanceof com.typesafe.config.ConfigObject obj) {
            for (var entry : obj.entrySet()) {
                collectFileOrigins(entry.getValue(), files);
            }
        } else if (value instanceof com.typesafe.config.ConfigList list) {
            for (var item : list) {
                collectFileOrigins(item, files);
            }
        }
    }
}
