package io.koraframework.config.aot;

import com.typesafe.config.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import io.koraframework.config.common.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public final class HoconAotParser implements AotConfigParser {

    private Config baseConfig;

    @Override
    public void loadAndMerge(ClassLoader classLoader, String customResource, String customFile) {
        ConfigParseOptions parseOptions = ConfigParseOptions.defaults().setClassLoader(classLoader);
        Config referenceConfig = ConfigFactory.parseResources("reference.conf", parseOptions);

        Config appConfig;
        if (customFile != null && !customFile.isBlank()) {
            appConfig = ConfigFactory.parseFile(new File(customFile), parseOptions);
        } else if (customResource != null && !customResource.isBlank()) {
            appConfig = ConfigFactory.parseResources(customResource, parseOptions);
        } else {
            appConfig = ConfigFactory.parseResources("application.conf", parseOptions);
        }

        baseConfig = appConfig.withFallback(referenceConfig);
    }

    @Override
    public Object getConfigValue(String path) {
        if (baseConfig == null) return null;

        if (path.isBlank()) {
            return extractUnresolvedValue(baseConfig.root());
        }

        String[] parts = path.split("\\.");
        ConfigObject currentObject = baseConfig.root();
        ConfigValue targetValue = null;

        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            targetValue = currentObject.get(key);
            if (targetValue == null) {
                List<String> relaxedNames = PathElement.get(key).relaxedNames();
                for (String variant : relaxedNames) {
                    targetValue = currentObject.get(variant);
                    if (targetValue != null) break;
                }
            }
            if (targetValue == null) return null;
            if (i < parts.length - 1 && targetValue instanceof ConfigObject configObject) {
                currentObject = configObject;
            }
        }

        return extractUnresolvedValue(targetValue);
    }

    private Object extractUnresolvedValue(ConfigValue hoconValue) {
        if (hoconValue == null) return null;

        if (hoconValue instanceof ConfigObject obj) {
            var map = new LinkedHashMap<String, Object>(obj.size());
            for (var entry : obj.entrySet()) {
                map.put(entry.getKey(), extractUnresolvedValue(entry.getValue()));
            }
            return map;
        }

        if (hoconValue instanceof ConfigList list) {
            var res = new ArrayList<>(list.size());
            for (ConfigValue item : list) {
                res.add(extractUnresolvedValue(item));
            }
            return res;
        }

        String rendered = hoconValue.render(ConfigRenderOptions.concise().setJson(false));
        if (rendered != null && rendered.startsWith("\"") && rendered.endsWith("\"")) {
            rendered = rendered.substring(1, rendered.length() - 1);
        }

        if (rendered != null && rendered.contains("${")) {
            if (rendered.contains(",")) {
                String[] tokens = rendered.split(",");
                String cleanExpr = null;
                Object fallback = null;

                for (String token : tokens) {
                    String trimmedToken = token.trim();
                    if (trimmedToken.contains("${")) {
                        cleanExpr = trimmedToken;
                    } else {
                        String rawFallback = trimmedToken.replaceAll("^\"|\"$", "");
                        if (rawFallback.matches("-?\\d+")) {
                            fallback = Long.parseLong(rawFallback);
                        } else if (rawFallback.matches("-?\\d+\\.\\d+")) {
                            fallback = Double.parseDouble(rawFallback);
                        } else if ("true".equalsIgnoreCase(rawFallback) || "false".equalsIgnoreCase(rawFallback)) {
                            fallback = Boolean.parseBoolean(rawFallback);
                        } else {
                            fallback = rawFallback;
                        }
                    }
                }

                if (cleanExpr != null) {
                    return new FallbackHolder(cleanExpr, fallback);
                }
            }

            Object fallback = null;
            try {
                Config tempConfig = ConfigFactory.parseString("temp = " + hoconValue.render(ConfigRenderOptions.concise()))
                    .withFallback(baseConfig);
                Config resolvedTemp = tempConfig.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));

                if (resolvedTemp.hasPath("temp")) {
                    ConfigValue resolvedValue = resolvedTemp.getValue("temp");
                    if (!resolvedValue.render().contains("${")) {
                        fallback = resolvedValue.unwrapped();
                    }
                }
            } catch (Exception ignored) {}

            if (fallback == null) {
                var placeholderPattern = Pattern.compile("\\$\\{\\??([^:}]+)(?::([^}]+))?}");
                var matcher = placeholderPattern.matcher(rendered);
                if (matcher.find()) {
                    String fallbackStr = matcher.group(2);
                    if (fallbackStr != null) {
                        fallbackStr = fallbackStr.trim().replaceAll("^\"|\"$", "");
                        if (fallbackStr.matches("-?\\d+")) {
                            fallback = Long.parseLong(fallbackStr);
                        } else if (fallbackStr.matches("-?\\d+\\.\\d+")) {
                            fallback = Double.parseDouble(fallbackStr);
                        } else if ("true".equalsIgnoreCase(fallbackStr) || "false".equalsIgnoreCase(fallbackStr)) {
                            fallback = Boolean.parseBoolean(fallbackStr);
                        } else {
                            fallback = fallbackStr;
                        }
                    }
                }
            }

            return new FallbackHolder(rendered, fallback);
        }

        try {
            return hoconValue.unwrapped();
        } catch (ConfigException.NotResolved notResolved) {
            return new FallbackHolder(rendered, null);
        }
    }

    public static final class FallbackHolder {
        public final String rawExpression;
        public final Object fallbackValue;

        public FallbackHolder(String rawExpression, Object fallbackValue) {
            this.rawExpression = rawExpression;
            this.fallbackValue = fallbackValue;
        }
    }
}
