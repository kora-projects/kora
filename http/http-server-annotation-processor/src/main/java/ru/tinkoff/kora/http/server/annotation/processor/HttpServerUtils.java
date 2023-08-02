package ru.tinkoff.kora.http.server.annotation.processor;


import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.AbstractMap;
import java.util.Objects;
import java.util.stream.Collectors;

public class HttpServerUtils {
    public static final ClassName interceptWithClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
    public static final ClassName interceptWithContainerClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer");

    public record Interceptor(TypeName type, @Nullable AnnotationSpec tag) {
    }

    public static Interceptor parseInterceptor(AnnotationMirror a) {
        var interceptorType = ((TypeMirror) CommonUtils.parseAnnotationValueWithoutDefault(a, "value"));
        var interceptorTypeName = ClassName.get(Objects.requireNonNull(interceptorType));
        @Nullable
        var interceptorTag = (AnnotationMirror) CommonUtils.parseAnnotationValueWithoutDefault(a, "tag");
        var interceptorTagAnnotationSpec = interceptorTag == null ? null : AnnotationSpec.get(interceptorTag);
        return new Interceptor(interceptorTypeName, interceptorTagAnnotationSpec);
    }

    @Nullable
    public static RequestMappingData extract(Elements elements, Types types, TypeElement controller, ExecutableElement executableElement) {
        var controllerAnnotation = controller.getAnnotation(HttpController.class);
        var executableType = (ExecutableType) types.asMemberOf((DeclaredType) controller.asType(), executableElement);
        for (var parameterType : executableType.getParameterTypes()) {
            if (parameterType.getKind() == TypeKind.ERROR) {
                return null;
            }
        }
        if (executableType.getReturnType().getKind() == TypeKind.ERROR) {
            return null;
        }
        var route = executableElement.getAnnotation(HttpRoute.class);
        if (route == null) {
            return null;
        }
        var httpMethod = route.method();
        var path = route.path();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        var controllerPath = controllerAnnotation.value();
        if (!controllerPath.isEmpty()) {
            if (!controllerPath.startsWith("/")) {
                controllerPath = "/" + controllerPath;
            }
            if (controllerPath.endsWith("/")) {
                controllerPath = controllerPath.substring(0, controllerPath.length() - 1);
            }
        }
        var finalPath = controllerPath + path;
        if (finalPath.isEmpty()) {
            return null;
        }
        var httpRequestMappingType = types.erasure(elements.getTypeElement(HttpServerRequestMapper.class.getCanonicalName()).asType());
        var httpResponseMappingType = types.erasure(elements.getTypeElement(HttpServerResponseMapper.class.getCanonicalName()).asType());

        var mappingData = executableElement.getParameters()
            .stream()
            .map(variableElement -> {
                var parameterMappings = CommonUtils.parseMapping(variableElement);
                var mapper = parameterMappings.getMapping(types, httpRequestMappingType);
                return new AbstractMap.SimpleImmutableEntry<>((VariableElement) variableElement, mapper);
            })
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
        var responseMapper = CommonUtils.parseMapping(executableElement).getMapping(types, httpResponseMappingType);

        return new RequestMappingData(
            executableElement,
            executableType,
            httpMethod,
            finalPath,
            mappingData,
            responseMapper
        );
    }
}
