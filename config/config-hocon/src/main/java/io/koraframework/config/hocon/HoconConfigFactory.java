package io.koraframework.config.hocon;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigRenderOptions;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.impl.SimpleConfig;
import io.koraframework.config.common.impl.SimpleConfigValueOrigin;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.config.common.origin.ResourceConfigOrigin;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public final class HoconConfigFactory {

    private HoconConfigFactory() {}

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

    static ConfigOrigin fileOrigin(Path path) {
        var primary = new FileConfigOrigin(path);
        return withIncludes(primary, includedFiles(options -> ConfigFactory.parseFile(path.toFile(), options)));
    }

    static ConfigOrigin resourceOrigin(URL url) {
        var primary = new ResourceConfigOrigin(url);
        return withIncludes(primary, includedFiles(options -> ConfigFactory.parseURL(url, options)));
    }

    private static ConfigOrigin withIncludes(ConfigOrigin primary, List<FileConfigOrigin> includedFiles) {
        if (includedFiles.isEmpty()) {
            return primary;
        }
        var origins = new ArrayList<ConfigOrigin>();
        origins.add(primary);
        origins.addAll(includedFiles);
        return new ContainerConfigOrigin(origins);
    }

    private static List<FileConfigOrigin> includedFiles(Consumer<ConfigParseOptions> parse) {
        var includer = new TrackingConfigIncluder();
        var options = ConfigParseOptions.defaults().setIncluder(includer);
        parse.accept(options);
        var includedFiles = new ArrayList<FileConfigOrigin>();
        for (var includedFile : includer.getIncludedFiles()) {
            includedFiles.add(new FileConfigOrigin(includedFile));
        }
        return includedFiles;
    }
}
