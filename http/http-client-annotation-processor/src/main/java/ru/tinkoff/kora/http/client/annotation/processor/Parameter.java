package ru.tinkoff.kora.http.client.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.common.annotation.Query;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface Parameter {
    record HeaderParameter(VariableElement parameter, String headerName) implements Parameter {}

    record QueryParameter(VariableElement parameter, String queryParameterName) implements Parameter {}

    record PathParameter(VariableElement parameter, String pathParameterName) implements Parameter {}

    record BodyParameter(VariableElement parameter, @Nullable CommonUtils.MappingData mapper) implements Parameter {}

    record ContinuationParameter(VariableElement parameter) implements Parameter {}

    class ParameterParser {
        private final Elements elements;
        private final Types types;
        private final TypeMirror requestMapperType;

        public ParameterParser(Elements elements, Types types) {
            this.elements = elements;
            this.types = types;
            var httpClientRequestMapperElement = this.elements.getTypeElement(HttpClientRequestMapper.class.getCanonicalName());
            this.requestMapperType = httpClientRequestMapperElement != null
                ? this.types.erasure(httpClientRequestMapperElement.asType())
                : null;
        }

        public Parameter parseParameter(ExecutableElement method, int parameterIndex) {
            var parameter = method.getParameters().get(parameterIndex);
            var header = parameter.getAnnotation(Header.class);
            var path = parameter.getAnnotation(Path.class);
            var query = parameter.getAnnotation(Query.class);
            if (header != null) {
                var name = header.value().isEmpty()
                    ? parameter.getSimpleName().toString()
                    : header.value();
                return new HeaderParameter(parameter, name);
            }
            if (path != null) {
                var name = path.value().isEmpty()
                    ? parameter.getSimpleName().toString()
                    : path.value();
                return new PathParameter(parameter, name);
            }
            if (query != null) {
                var name = query.value().isEmpty()
                    ? parameter.getSimpleName().toString()
                    : query.value();
                return new QueryParameter(parameter, name);
            }
            var mapping = CommonUtils.parseMapping(parameter)
                .getMapping(this.types, this.requestMapperType);
            return new BodyParameter(parameter, mapping);
        }
    }
}
