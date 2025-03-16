package ru.tinkoff.kora.http.server.annotation.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

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
    public record Interceptor(TypeName type, @Nullable AnnotationSpec tag) {
    }

    public static Interceptor parseInterceptor(AnnotationMirror a) {
        var interceptorType = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(a, "value");
        var interceptorTypeName = ClassName.get(Objects.requireNonNull(interceptorType));
        @Nullable
        var interceptorTag = AnnotationUtils.<AnnotationMirror>parseAnnotationValueWithoutDefault(a, "tag");
        var interceptorTagAnnotationSpec = interceptorTag == null ? null : AnnotationSpec.get(interceptorTag);
        return new Interceptor(interceptorTypeName, interceptorTagAnnotationSpec);
    }

    @Nullable
    public static RequestMappingData extract(Elements elements, Types types, TypeElement controller, ExecutableElement executableElement) {
        var controllerAnnotation = Objects.requireNonNull(AnnotationUtils.findAnnotation(controller, HttpServerClassNames.httpController));
        var executableType = (ExecutableType) types.asMemberOf((DeclaredType) controller.asType(), executableElement);
        for (var parameterType : executableType.getParameterTypes()) {
            if (parameterType.getKind() == TypeKind.ERROR) {
                return null;
            }
        }
        if (executableType.getReturnType().getKind() == TypeKind.ERROR) {
            return null;
        }

        var route = AnnotationUtils.findAnnotation(executableElement, HttpServerClassNames.httpRoute);
        if (route == null) {
            return null;
        }

        var httpMethod = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(route, "method"));
        var path = Objects.requireNonNull(AnnotationUtils.<String>parseAnnotationValueWithoutDefault(route, "path"));
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        var controllerPath = Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(controllerAnnotation, "value"), "").trim();
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

        var mappingData = executableElement.getParameters()
            .stream()
            .map(variableElement -> {
                var parameterMappings = CommonUtils.parseMapping(variableElement);
                var mapper = parameterMappings.getMapping(HttpServerClassNames.httpServerRequestMapper);
                return new AbstractMap.SimpleImmutableEntry<>((VariableElement) variableElement, mapper);
            })
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue));
        var responseMapper = CommonUtils.parseMapping(executableElement).getMapping(HttpServerClassNames.httpServerResponseMapper);

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
