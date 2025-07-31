package ru.tinkoff.kora.http.client.annotation.processor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.httpClientRequestMapper;

public interface Parameter {

    record HeaderParameter(VariableElement parameter, String headerName) implements Parameter {}

    record QueryParameter(VariableElement parameter, String queryParameterName) implements Parameter {}

    record PathParameter(VariableElement parameter, String pathParameterName) implements Parameter {}

    record CookieParameter(VariableElement parameter, String cookieName) implements Parameter {}

    record BodyParameter(VariableElement parameter, @Nullable CommonUtils.MappingData mapper) implements Parameter {}

    record ContinuationParameter(VariableElement parameter) implements Parameter {}

    static Parameter parse(ExecutableElement method, int parameterIndex) {
        var parameter = method.getParameters().get(parameterIndex);

        var header = AnnotationUtils.findAnnotation(parameter, HttpClientClassNames.header);
        var path = AnnotationUtils.findAnnotation(parameter, HttpClientClassNames.path);
        var query = AnnotationUtils.findAnnotation(parameter, HttpClientClassNames.query);
        var cookie = AnnotationUtils.findAnnotation(parameter, HttpClientClassNames.cookie);
        if (header != null) {
            var headerValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(header, "value");
            var name = headerValue == null || headerValue.isBlank()
                ? parameter.getSimpleName().toString()
                : headerValue;
            return new HeaderParameter(parameter, name);
        }
        if (path != null) {
            var pathValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(path, "value");
            var name = pathValue == null || pathValue.isBlank()
                ? parameter.getSimpleName().toString()
                : pathValue;
            return new PathParameter(parameter, name);
        }
        if (query != null) {
            var queryValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(query, "value");
            var name = queryValue == null || queryValue.isBlank()
                ? parameter.getSimpleName().toString()
                : queryValue;
            return new QueryParameter(parameter, name);
        }
        if (cookie != null) {
            var cookieValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(cookie, "value");
            var name = cookieValue == null || cookieValue.isBlank()
                ? parameter.getSimpleName().toString()
                : cookieValue;
            return new CookieParameter(parameter, name);
        }
        var mapping = CommonUtils.parseMapping(parameter)
            .getMapping(httpClientRequestMapper);
        return new BodyParameter(parameter, mapping);
    }
}
