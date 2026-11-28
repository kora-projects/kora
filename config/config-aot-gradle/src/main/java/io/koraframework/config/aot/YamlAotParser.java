package io.koraframework.config.aot;

import org.snakeyaml.engine.v2.api.*;

import java.util.*;

public final class YamlAotParser implements AotConfigParser {

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String mainContent, String profileContent) {
        var result = new HashMap<String, Object>();
        try {
            var settings = LoadSettings.builder().build();
            var load = new Load(settings);

            var mainMap = (Map<String, Object>) load.loadFromString(mainContent);
            if (mainMap == null) {
                mainMap = new HashMap<>();
            }

            if (profileContent != null && !profileContent.isBlank()) {
                var profileMap = (Map<String, Object>) load.loadFromString(profileContent);
                if (profileMap != null) {
                    mergeMaps(mainMap, profileMap);
                }
            }

            flattenYamlMap("", mainMap, result);
        } catch (Exception ignored) {
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void mergeMaps(Map<String, Object> target, Map<String, Object> source) {
        for (var key : source.keySet()) {
            var sourceValue = source.get(key);
            var targetValue = target.get(key);
            if (sourceValue instanceof Map && targetValue instanceof Map) {
                mergeMaps((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue);
            } else {
                target.put(key, sourceValue);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenYamlMap(String prefix, Map<String, Object> source, Map<String, Object> result) {
        for (var entry : source.entrySet()) {
            var key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenYamlMap(key, (Map<String, Object>) entry.getValue(), result);
            } else {
                result.put(key, entry.getValue());
            }
        }
    }

    @Override
    public void loadAndMerge(ClassLoader classLoader, String customResource, String customFile) {

    }

    @Override
    public Object getConfigValue(String path) {
        return null;
    }
}
