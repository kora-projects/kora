package io.koraframework.config.aot;

import io.koraframework.config.common.*;
import io.koraframework.config.common.impl.*;
import io.koraframework.config.common.origin.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

public final class AotConfigEvaluator {

    private static final Map<String, String> runtimeOverrides = new ConcurrentHashMap<>();

    private AotConfigEvaluator() {}

    public static void loadAotOverrides() {
        try {
            Class.forName("io.koraframework.config.aot.generated.KoraAotConfigApplicationModule$Impl", true, AotConfigEvaluator.class.getClassLoader());
        } catch (ClassNotFoundException _) {

        }
    }

    public static void putRuntimeOverrides(Map<String, String> overrides) {
        if (overrides == null) return;
        for (var entry : overrides.entrySet()) {
            runtimeOverrides.put(entry.getKey(), entry.getValue());
        }
    }

    public static ConfigValue.StringValue resolveString(ConfigValuePath path, String sysKey, String defaultValue) {
        var raw = getRawValue(sysKey);
        var finalValue = raw != null ? raw : defaultValue;
        return new ConfigValue.StringValue(createOrigin(path), finalValue);
    }

    public static ConfigValue.NumberValue resolveNumber(ConfigValuePath path, String sysKey, Number defaultValue) {
        var raw = getRawValue(sysKey);
        if (raw == null) {
            return new ConfigValue.NumberValue(createOrigin(path), defaultValue);
        }
        
        Number parsed;
        try {
            if (raw.contains(".")) {
                parsed = Double.parseDouble(raw);
            } else {
                parsed = Long.parseLong(raw);
            }
        } catch (NumberFormatException _) {
            parsed = defaultValue;
        }
        return new ConfigValue.NumberValue(createOrigin(path), parsed);
    }

    public static ConfigValue.BooleanValue resolveBoolean(ConfigValuePath path, String sysKey, boolean defaultValue) {
        var raw = getRawValue(sysKey);
        var finalValue = raw != null ? Boolean.parseBoolean(raw) : defaultValue;
        return new ConfigValue.BooleanValue(createOrigin(path), finalValue);
    }

    public static ConfigValue.ArrayValue resolveList(ConfigValuePath path, String sysKey, List<String> defaultValues) {
        var origin = createOrigin(path);
        var resultList = new ArrayList<ConfigValue<?>>();
        
        var envKey = convertToEnvKey(sysKey);
        var envVal = System.getenv(envKey);
        
        if (envVal != null) {
            var parts = envVal.split(",");
            for (var i = 0; i < parts.length; i++) {
                resultList.add(new ConfigValue.StringValue(createOrigin(path.child(String.valueOf(i))), parts[i].trim()));
            }
            return new ConfigValue.ArrayValue(origin, resultList);
        }

        var index = 0;
        while (true) {
            var propKey = sysKey + "." + index;
            var rawItem = getRawValue(propKey);
            if (rawItem == null) {
                propKey = sysKey + "[" + index + "]";
                rawItem = getRawValue(propKey);
            }
            if (rawItem == null) break;
            
            resultList.add(new ConfigValue.StringValue(createOrigin(path.child(String.valueOf(index))), rawItem));
            index++;
        }

        if (resultList.isEmpty() && defaultValues != null) {
            for (var i = 0; i < defaultValues.size(); i++) {
                resultList.add(new ConfigValue.StringValue(createOrigin(path.child(String.valueOf(i))), defaultValues.get(i)));
            }
        }

        return new ConfigValue.ArrayValue(origin, resultList);
    }

    public static ConfigValue.ObjectValue resolveMap(ConfigValuePath path, String sysKey, Map<String, String> defaultValues) {
        var origin = createOrigin(path);
        var resultMap = new LinkedHashMap<String, ConfigValue<?>>();

        if (defaultValues != null) {
            for (var entry : defaultValues.entrySet()) {
                var childPath = path.child(entry.getKey());
                resultMap.put(entry.getKey(), new ConfigValue.StringValue(createOrigin(childPath), entry.getValue()));
            }
        }

        var prefixWithDot = sysKey + ".";
        for (var propName : System.getProperties().stringPropertyNames()) {
            if (propName.startsWith(prefixWithDot)) {
                var subKey = propName.substring(prefixWithDot.length());
                var childPath = path.child(subKey);
                resultMap.put(subKey, new ConfigValue.StringValue(createOrigin(childPath), System.getProperty(propName)));
            }
        }

        var envPrefix = convertToEnvKey(sysKey) + "_";
        for (var envEntry : System.getenv().entrySet()) {
            if (envEntry.getKey().startsWith(envPrefix)) {
                var subKey = envEntry.getKey().substring(envPrefix.length()).toLowerCase().replace('_', '.');
                var childPath = path.child(subKey);
                resultMap.put(subKey, new ConfigValue.StringValue(createOrigin(childPath), envEntry.getValue()));
            }
        }

        return new ConfigValue.ObjectValue(origin, resultMap);
    }

    private static String getRawValue(String sysKey) {
        var lastPart = sysKey.contains(".") ? sysKey.substring(sysKey.lastIndexOf('.') + 1) : sysKey;
        var prefix = sysKey.contains(".") ? sysKey.substring(0, sysKey.lastIndexOf('.') + 1) : "";
        var relaxedVariants = PathElement.get(lastPart).relaxedNames();

        for (var variant : relaxedVariants) {
            var fullEnvKey = convertToEnvKey(prefix + variant);
            var env = System.getenv(fullEnvKey);
            if (env != null) return env;
        }

        for (var variant : relaxedVariants) {
            var fullSysKey = prefix + variant;
            var sys = System.getProperty(fullSysKey);
            if (sys != null) return sys;
        }

        for (var variant : relaxedVariants) {
            var fullRuntimeKey = prefix + variant;
            var overrideVal = runtimeOverrides.get(fullRuntimeKey);
            if (overrideVal != null) return overrideVal;
        }

        return null;
    }

    private static String convertToEnvKey(String sysKey) {
        return sysKey.replace('.', '_').replace('-', '_').toUpperCase();
    }

    private static ConfigValueOrigin createOrigin(ConfigValuePath path) {
        return new SimpleConfigValueOrigin(new SimpleConfigOrigin("aot-compiled"), path);
    }

    public static Map<String, String> getRuntimeOverrides() {
        return runtimeOverrides;
    }


    public static void buildTreeFromFlatMap(Map<String, ConfigValue<?>> rootMap, String fullKey, String value) {
        var lastDot = fullKey.lastIndexOf('.');
        var configPath = lastDot > 0 ? fullKey.substring(0, lastDot) : "";
        var fieldName = lastDot > 0 ? fullKey.substring(lastDot + 1) : fullKey;

        var origin = new SimpleConfigValueOrigin(new SimpleConfigOrigin("aot-compiled"), ConfigValuePath.root().child(fullKey));
        var configValue = new ConfigValue.StringValue(origin, value);

        putIntoTree(rootMap, configPath, fieldName, configValue);
    }

    public static void putIntoTree(Map<String, ConfigValue<?>> rootMap, String configPath, String fieldName, ConfigValue<?> value) {
        if (configPath == null || configPath.isBlank()) {
            rootMap.put(fieldName, value);
            return;
        }
        var currentState = getState();
        var pathParts = currentState.pathSplit.split(configPath);
        Object currentContainer = rootMap;
        var currentPath = ConfigValuePath.root();

        for (var i = 0; i < pathParts.length; i++) {
            var part = pathParts[i];
            if (part.isBlank()) continue;
            currentPath = currentPath.child(part);
            var origin = new SimpleConfigValueOrigin(currentState.aotOrigin, currentPath);
            var isNextPartInteger = (i + 1 < pathParts.length) ? pathParts[i + 1].matches("\\d+") : fieldName.matches("\\d+");

            if (currentContainer instanceof Map) {
                @SuppressWarnings("unchecked")
                var map = (Map<String, ConfigValue<?>>) currentContainer;
                var existing = map.get(part);
                if (isNextPartInteger) {
                    var listData = (existing instanceof ConfigValue.ArrayValue av) ? new ArrayList<>(av.value()) : new ArrayList<ConfigValue<?>>();
                    map.put(part, new ConfigValue.ArrayValue(origin, listData));
                    currentContainer = listData;
                } else {
                    var targetMap = (existing instanceof ConfigValue.ObjectValue ov) ? new LinkedHashMap<>(ov.value()) : new LinkedHashMap<String, ConfigValue<?>>();
                    map.put(part, new ConfigValue.ObjectValue(origin, targetMap));
                    currentContainer = targetMap;
                }
            } else {
                @SuppressWarnings("unchecked")
                var list = (List<ConfigValue<?>>) currentContainer;
                var index = Integer.parseInt(part);
                while (list.size() <= index) {
                    var nullPath = currentPath.prev().child(String.valueOf(list.size()));
                    list.add(new ConfigValue.NullValue(new SimpleConfigValueOrigin(currentState.aotOrigin, nullPath)));
                }
                var existing = list.get(index);
                if (isNextPartInteger) {
                    var listData = (existing instanceof ConfigValue.ArrayValue av) ? new ArrayList<>(av.value()) : new ArrayList<ConfigValue<?>>();
                    list.set(index, new ConfigValue.ArrayValue(origin, listData));
                    currentContainer = listData;
                } else {
                    var targetMap = (existing instanceof ConfigValue.ObjectValue ov) ? new LinkedHashMap<>(ov.value()) : new LinkedHashMap<String, ConfigValue<?>>();
                    list.set(index, new ConfigValue.ObjectValue(origin, targetMap));
                    currentContainer = targetMap;
                }
            }
        }

        if (currentContainer instanceof Map) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, ConfigValue<?>>) currentContainer;
            map.put(fieldName, value);
        } else {
            @SuppressWarnings("unchecked")
            var list = (List<ConfigValue<?>>) currentContainer;
            var index = Integer.parseInt(fieldName);
            var finalPath = currentPath.child(fieldName);
            var finalOrigin = new SimpleConfigValueOrigin(currentState.aotOrigin, finalPath);
            while (list.size() <= index) {
                list.add(new ConfigValue.NullValue(finalOrigin));
            }
            list.set(index, value);
        }
    }

    private static ResolverState getState() {
        return ResolverState.INSTANCE;
    }

    private static class ResolverState {
        static final ResolverState INSTANCE = new ResolverState();
        final SimpleConfigOrigin aotOrigin = new SimpleConfigOrigin("aot-compiled");
        final Pattern pathSplit = Pattern.compile("\\.");
    }
}
