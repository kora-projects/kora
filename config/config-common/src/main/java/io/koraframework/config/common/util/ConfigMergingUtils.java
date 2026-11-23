package io.koraframework.config.common.util;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.PathElement;
import io.koraframework.config.common.impl.SimpleConfig;
import io.koraframework.config.common.impl.SimpleConfigValueOrigin;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class ConfigMergingUtils {

    private ConfigMergingUtils() { }

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
        var newValues = new LinkedHashMap<String, ConfigValue<?>>(object1.value().size());
        // ObjectValue.get follows only the looked-up key's own relaxedNames, so a kebab or snake key of object1 never finds
        // a camelCase key of object2: index object2 keys by their other spellings to match that direction too
        var object2KeysBySpelling = new HashMap<String, List<String>>();
        for (var key2 : object2.value().keySet()) {
            for (var spelling : PathElement.get(key2).relaxedNames()) {
                if (!spelling.equals(key2)) {
                    object2KeysBySpelling.computeIfAbsent(spelling, k -> new ArrayList<>()).add(key2);
                }
            }
        }
        var overriddenKeys2 = new HashSet<String>();
        var overriddenKeys = new HashSet<String>();

        for (var entry1 : object1) {
            var key = entry1.getKey();
            var value = merge(origin, path.child(key), entry1.getValue(), object2.get(key));
            // every lookup that finds such a key2 also finds key, so key2 is the same setting and is overridden;
            // a value of another shape (object vs scalar) is kept, like on an exact-spelling miss
            for (var key2 : object2KeysBySpelling.getOrDefault(key, List.of())) {
                var value2 = object2.value().get(key2);
                if (value != null && value2 != null && (value instanceof ConfigValue.ObjectValue) == (value2 instanceof ConfigValue.ObjectValue)) {
                    value = merge(origin, path.child(key), value, value2);
                    overriddenKeys2.add(key2);
                }
            }
            if (value != null) {
                newValues.put(key, value);
                if (object1.overriddenKeys().contains(key)) {
                    overriddenKeys.add(key);
                }
            }
        }
        // key2 stays as a literal entry, so a Map section keeps it, but field lookups skip it and see the higher-layer value
        for (var entry : object2) {
            var key2 = entry.getKey();
            if (!object1.value().containsKey(key2) && entry.getValue() != null) {
                newValues.put(key2, entry.getValue());
                if (overriddenKeys2.contains(key2) || object2.overriddenKeys().contains(key2)) {
                    overriddenKeys.add(key2);
                }
            }
        }

        return new ConfigValue.ObjectValue(new SimpleConfigValueOrigin(origin, path), newValues, overriddenKeys.isEmpty() ? Set.of() : overriddenKeys);
    }
}
