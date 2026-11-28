package io.koraframework.config.common.util;

import io.koraframework.config.common.*;
import io.koraframework.config.common.impl.*;
import io.koraframework.config.common.origin.*;
import org.jspecify.annotations.*;

import java.util.*;

public final class ConfigMergingUtils {

    private ConfigMergingUtils() {}

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
        } else if (value2 == null) {
            return value1;
        }

        if (value1 instanceof ConfigValue.ObjectValue object1 && value2 instanceof ConfigValue.ObjectValue object2) {
            return mergeObjects(origin, path, object1, object2);
        } else {
            return value1;
        }
    }

    private static ConfigValue.ObjectValue mergeObjects(ConfigOrigin origin, ConfigValuePath path, ConfigValue.ObjectValue object1, ConfigValue.ObjectValue object2) {
        var map1 = object1.value();
        var map2 = object2.value();
        int expectedSize = map1.size() + map2.size();
        var newValues = new LinkedHashMap<String, ConfigValue<?>>((int) (expectedSize / 0.75f) + 1);

        for (var entry1 : map1.entrySet()) {
            var key = entry1.getKey();
            var value1 = entry1.getValue();
            var value2 = map2.get(key);

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
        for (var entry2 : map2.entrySet()) {
            var value2 = entry2.getValue();
            if (value2 != null) {
                newValues.putIfAbsent(entry2.getKey(), value2);
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), newValues);
    }
}
