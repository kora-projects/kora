package ru.tinkoff.kora.http.server.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import java.util.Map;

public record RequestMappingData(
    ExecutableElement executableElement,
    ExecutableType executableType,
    String httpMethod,
    String route,
    Map<VariableElement, CommonUtils.MappingData> httpRequestMappingData,
    @Nullable CommonUtils.MappingData responseMapper
) {
}
