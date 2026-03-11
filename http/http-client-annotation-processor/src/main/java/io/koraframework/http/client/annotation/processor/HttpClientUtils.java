package io.koraframework.http.client.annotation.processor;

import io.koraframework.annotation.processor.common.NameUtils;

import javax.lang.model.element.TypeElement;

public class HttpClientUtils {
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
