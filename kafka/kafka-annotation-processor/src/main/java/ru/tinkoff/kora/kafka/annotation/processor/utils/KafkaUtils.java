package ru.tinkoff.kora.kafka.annotation.processor.utils;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.capitalize;
import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public class KafkaUtils {

    public static String prepareTagName(String controllerName, String methodName){
        return capitalize(controllerName) + capitalize(methodName) + "Tag";
    }

    public static String prepareMethodName(String controllerName, String methodName) {
        return decapitalize(controllerName) + capitalize(methodName);
    }
}
