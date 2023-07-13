package ru.tinkoff.kora.config.common.factory;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.impl.SimpleConfig;
import ru.tinkoff.kora.config.common.impl.SimpleConfigValueOrigin;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;

public class MergeConfigFactory {
    public static Config merge(Config config, Config fallback) {
        var root1 = config.root();
        var root2 = fallback.root();
        var origin = new ContainerConfigOrigin(config.origin(), fallback.origin());
        var path = ConfigValuePath.root();

        var newRoot = mergeObjects(origin, path, root1, root2);

        return new SimpleConfig(origin, newRoot);
    }

    @Nullable
    private static ConfigValue<?> merge(ConfigOrigin origin, ConfigValuePath path, @Nullable ConfigValue<?> value1, @Nullable ConfigValue<?> value2) {
        if (value1 == null) {
            return value2;
        }
        if (value2 == null) {
            return value1;
        }
        if (value1 instanceof ConfigValue.ObjectValue object1 && value2 instanceof ConfigValue.ObjectValue object2) {
            return mergeObjects(origin, path, object1, object2);
        } else {
            return value1;
        }
    }

    private static ConfigValue.ObjectValue mergeObjects(ConfigOrigin origin, ConfigValuePath path, ConfigValue.ObjectValue object1, ConfigValue.ObjectValue object2) {
        var newValues = new LinkedHashMap<String, ConfigValue<?>>(object1.value().size());

        for (var entry1 : object1) {
            var key = entry1.getKey();
            var value1 = entry1.getValue();
            var value2 = object2.get(key);
            if (value2 == null) {
                if (value1 != null) {
                    newValues.put(key, value1);
                }
            } else if (value1 == null) {
                newValues.put(key, value2);
            } else {
                var newObject = merge(origin, path.child(key), value1, value2);
                if (newObject != null) {
                    newValues.put(key, newObject);
                }
            }
        }
        for (var entry : object2) {
            if (!object1.value().containsKey(entry.getKey()) && entry.getValue() != null) {
                newValues.put(entry.getKey(), entry.getValue());
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), newValues);
    }
}
