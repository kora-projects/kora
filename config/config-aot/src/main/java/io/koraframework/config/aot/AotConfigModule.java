package io.koraframework.config.aot;

import io.koraframework.config.common.*;
import io.koraframework.config.common.ConfigValue.*;
import io.koraframework.config.common.annotation.*;
import io.koraframework.config.common.impl.*;
import io.koraframework.config.common.mapper.*;
import io.koraframework.config.common.origin.*;

import java.util.*;

public interface AotConfigModule extends ConfigModule, ConfigValueMapperModule {

    @ApplicationConfig
    default ConfigOrigin applicationConfigOrigin() {
        return new SimpleConfigOrigin("aot-compiled");
    }

    @ApplicationConfig
    default Config applicationConfig(@ApplicationConfig ConfigOrigin origin, Optional<Map<String, Object>> aotRawConfigData) {
        var valueOrigin = new SimpleConfigValueOrigin(origin, ConfigValuePath.root());

        if (aotRawConfigData.isEmpty()) {
            return new SimpleConfig(origin, new ObjectValue(valueOrigin, new LinkedHashMap<>()));
        }

        var rawRootMap = new LinkedHashMap<String, Object>();
        for (var entry : aotRawConfigData.get().entrySet()) {
            putRawIntoTree(rawRootMap, entry.getKey(), entry.getValue());
        }

        var globalObject = (ObjectValue) convertToConfigValue(rawRootMap, valueOrigin);

        return new SimpleConfig(origin, globalObject);
    }

    private void putRawIntoTree(Map<String, Object> rootMap, String fullKey, Object value) {
        int start = 0;
        int dotIdx;
        var currentMap = rootMap;

        while ((dotIdx = fullKey.indexOf('.', start)) != -1) {
            var part = fullKey.substring(start, dotIdx);
            start = dotIdx + 1;
            currentMap = (Map<String, Object>) currentMap.computeIfAbsent(part, _ -> new LinkedHashMap<String, Object>());
        }
        currentMap.put(fullKey.substring(start), value);
    }

    private ConfigValue<?> convertToConfigValue(Object rawValue, ConfigValueOrigin origin) {
        return switch (rawValue) {
            case Map<?, ?> map -> {
                var resultMap = new LinkedHashMap<String, ConfigValue<?>>((int) (map.size() / 0.75f) + 1);
                for (var entry : map.entrySet()) {
                    var key = (String) entry.getKey();
                    var childOrigin = new SimpleConfigValueOrigin(origin.config(), origin.path().child(key));
                    resultMap.put(key, convertToConfigValue(entry.getValue(), childOrigin));
                }
                yield new ObjectValue(origin, resultMap);
            }
            case List<?> list -> {
                var resultList = new ArrayList<ConfigValue<?>>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    var childOrigin = new SimpleConfigValueOrigin(origin.config(), origin.path().child(String.valueOf(i)));
                    resultList.add(convertToConfigValue(list.get(i), childOrigin));
                }
                yield new ArrayValue(origin, resultList);
            }
            case Boolean b -> new BooleanValue(origin, b);
            case Number n -> new NumberValue(origin, n);
            case String s -> new StringValue(origin, s);
            default -> new NullValue(origin);
        };
    }
}
