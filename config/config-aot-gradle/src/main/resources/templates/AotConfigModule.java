package io.koraframework.config.aot.generated;

import io.koraframework.config.common.*;
import io.koraframework.config.common.ConfigValue.*;
import io.koraframework.config.common.annotation.*;
import io.koraframework.config.common.impl.*;
import io.koraframework.config.common.origin.*;
import java.util.*;

public interface AotConfigModule extends ConfigModule {

    @ApplicationConfig
    default ConfigOrigin applicationConfigOrigin() {
        return new SimpleConfigOrigin("aot-compiled");
    }

    @ApplicationConfig
    default Config applicationConfig(@ApplicationConfig ConfigOrigin origin) {
        var valueOrigin = new SimpleConfigValueOrigin(origin, ConfigValuePath.root());
        var rawRootMap = new LinkedHashMap<String, Object>();

        Impl.fillData(rawRootMap);

        var nestedTree = new LinkedHashMap<String, Object>();
        for (var entry : rawRootMap.entrySet()) {
            putRawIntoTree(nestedTree, entry.getKey(), (AotLeaf) entry.getValue());
        }

        var globalObject = (ObjectValue) convertToConfigValue(nestedTree, valueOrigin);
        return new SimpleConfig(origin, globalObject);
    }

    private void putRawIntoTree(Map<String, Object> rootMap, String fullKey, AotLeaf leaf) {
        int start = 0;
        int dotIdx;
        var currentMap = rootMap;

        while ((dotIdx = fullKey.indexOf('.', start)) != -1) {
            var part = fullKey.substring(start, dotIdx);
            var nextMap = (Map<String, Object>) currentMap.get(part);
            if (nextMap == null) {
                nextMap = new LinkedHashMap<>();
                currentMap.put(part, nextMap);
            }
            currentMap = nextMap;
            start = dotIdx + 1;
        }
        currentMap.put(fullKey.substring(start), leaf);
    }

    private ConfigValue<?> convertToConfigValue(Object rawValue, ConfigValueOrigin baseOrigin) {
        if (rawValue instanceof ConfigValue<?> cv) {
            return cv;
        }

        if (rawValue instanceof Map<?, ?> map) {
            var resultMap = new LinkedHashMap<String, ConfigValue<?>>((int) (map.size() / 0.75f) + 1);
            for (var entry : map.entrySet()) {
                var key = (String) entry.getKey();
                var childOrigin = new SimpleConfigValueOrigin(baseOrigin.config(), baseOrigin.path().child(key));
                resultMap.put(key, convertToConfigValue(entry.getValue(), childOrigin));
            }
            return new ObjectValue(baseOrigin, resultMap);
        }

        if (rawValue instanceof List<?> list) {
            var resultList = new ArrayList<ConfigValue<?>>(list.size());
            for (int i = 0; i < list.size(); i++) {
                var childOrigin = new SimpleConfigValueOrigin(baseOrigin.config(), baseOrigin.path().child(i));
                resultList.add(convertToConfigValue(list.get(i), childOrigin));
            }
            return new ArrayValue(baseOrigin, resultList);
        }

        if (rawValue instanceof AotLeaf leaf) {
            var leafOrigin = new SimpleConfigValueOrigin(baseOrigin.config(), leaf.path());
            var val = leaf.value();
            if (val instanceof ConfigValue<?> cv) return cv;
            if (val instanceof String s) return new StringValue(leafOrigin, s);
            if (val instanceof Number n) return new NumberValue(leafOrigin, n);
            if (val instanceof Boolean b) return new BooleanValue(leafOrigin, b);
            return new NullValue(leafOrigin);
        }

        return new NullValue(baseOrigin);
    }

    record AotLeaf(ConfigValuePath path, Object value) {}

    final class Impl {
        static void fillData(Map<String, Object> root) {
            // TARGET_INJECTION_POINT
        }
        // CHUNKS_INJECTION_POINT
    }
}
