package ru.tinkoff.kora.config.yaml;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.config.common.impl.SimpleConfig;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

import java.io.InputStream;
import java.util.*;

public class YamlConfigFactory {
    public static Config fromYaml(ConfigOrigin origin, InputStream is) {
        var settings = LoadSettings.builder()
            .setAllowRecursiveKeys(false)
            .build();
        var load = new Load(settings);
        @SuppressWarnings("unchecked")
        var document = (Map<String, ?>) load.loadFromInputStream(is);

        var path = ConfigValuePath.root();
        if (document == null) {
            return MapConfigFactory.fromMap(origin, Map.of());
        }
        var root = toObject(origin, path, document);
        return new SimpleConfig(origin, root);
    }

    private static ConfigValue.ObjectValue toObject(ConfigOrigin origin, ConfigValuePath path, Map<String, ?> document) {
        var object = new LinkedHashMap<String, ConfigValue<?>>(document.size());
        for (var entry : document.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            var key = entry.getKey();
            var valuePath = path.child(key);
            var value = toValue(origin, valuePath, entry.getValue());
            object.put(key, value);
        }
        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), object);
    }

    private static ConfigValue.ArrayValue toArray(ConfigOrigin origin, ConfigValuePath path, List<?> list) {
        var array = new ArrayList<ConfigValue<?>>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var item = list.get(i);
            if (item == null) {
                array.add(null);
                continue;
            }
            var valuePath = path.child(i);
            var value = toValue(origin, valuePath, item);
            array.add(value);
        }
        return new ConfigValue.ArrayValue(new SimpleConfigValueOrigin(origin, path), array);
    }

    private static ConfigValue<?> toValue(ConfigOrigin origin, ConfigValuePath path, Object value) {
        Objects.requireNonNull(value);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            var object = (Map<String, ?>) map;
            return toObject(origin, path, object);
        }
        if (value instanceof List<?> list) {
            return toArray(origin, path, list);
        }
        if (value instanceof Number number) {
            return new ConfigValue.NumberValue(new SimpleConfigValueOrigin(origin, path), number);
        }
        if (value instanceof String str) {
            return new ConfigValue.StringValue(new SimpleConfigValueOrigin(origin, path), str);
        }
        if (value instanceof Boolean bool) {
            return new ConfigValue.BooleanValue(new SimpleConfigValueOrigin(origin, path), bool);
        }
        throw new IllegalArgumentException("Unknown type %s for path %s".formatted(value.getClass(), path));
    }
}
