package ru.tinkoff.kora.http.client.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.lang.model.element.TypeElement;

public class HttpClientUtils {
    public static String clientName(TypeElement httpClientType) {
        return CommonUtils.getOuterClassesAsPrefix(httpClientType) + httpClientType.getSimpleName() + "_" + "ClientImpl";
    }

    public static String configName(TypeElement httpClientType) {
        return CommonUtils.getOuterClassesAsPrefix(httpClientType) + httpClientType.getSimpleName() + "_" + "Config";
    }

    public static String moduleName(TypeElement httpClientType) {
        return CommonUtils.getOuterClassesAsPrefix(httpClientType) + httpClientType.getSimpleName() + "_" + "Module";
    }
}
