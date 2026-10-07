package io.koraframework.http.client.annotation.processor;

import io.koraframework.annotation.processor.common.NameUtils;

import javax.lang.model.element.TypeElement;

public class HttpClientUtils {
    /**
     * Simple name prefix of the nested dependency holders of a client implementation with too many dependencies for one constructor
     */
    public static final String DEPENDENCIES_HOLDER_PREFIX = "Dependencies";

    public static String clientName(TypeElement httpClientType) {
        return NameUtils.generatedType(httpClientType, "ClientImpl");
    }

    public static String configName(TypeElement httpClientType) {
        return NameUtils.generatedType(httpClientType, "Config");
    }

    public static String moduleName(TypeElement httpClientType) {
        return NameUtils.generatedType(httpClientType, "Module");
    }
}
