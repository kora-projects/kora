package io.koraframework.resilient.annotation.processor;

import com.palantir.javapoet.ClassName;

public final class CircuitBreakerTagUtils {

    public static final String CONFIG_PATH_ROOT = "resilient.circuitbreaker";
    public static final String TELEMETRY_CONFIG_PATH = "resilient.circuitbreaker.telemetry";
    public static final String TAG_PACKAGE = "io.koraframework.resilient.circuitbreaker.generated";
    public static final String CONFIG_PATH_FIELD = "CONFIG_PATH";

    private CircuitBreakerTagUtils() {}

    public static ClassName tagName(String configPath) {
        return ClassName.get(TAG_PACKAGE, generatedName(configPath));
    }

    public static ClassName moduleName(String configPath) {
        return ClassName.get(TAG_PACKAGE, generatedName(configPath) + "Module");
    }

    public static String factoryMethodName(String configPath) {
        var generatedName = generatedName(configPath);
        return Character.toLowerCase(generatedName.charAt(0)) + generatedName.substring(1) + "FactoryModule";
    }

    private static String generatedName(String configPath) {
        var builder = new StringBuilder("CircuitBreaker");
        var capitalizeNext = true;
        for (int i = 0; i < configPath.length(); i++) {
            var c = configPath.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    builder.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    builder.append(c);
                }
            } else {
                capitalizeNext = true;
            }
        }
        return builder.toString();
    }

    public static boolean isReservedPath(String path) {
        return CONFIG_PATH_ROOT.equals(path) || TELEMETRY_CONFIG_PATH.equals(path);
    }
}
