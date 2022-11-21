package ru.tinkoff.kora.database.annotation.processor.model;

import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class QueryTemplate {

    public static String processTemplate(Types types, Elements elements, String source, ExecutableElement method, ExecutableType methodType, QueryResult queryResult) {
        var templateParams = TemplateModel.detectTemplateParams(source);
        if (templateParams.isEmpty()) {
            return source;
        }

        var returnTypeModel = detectReturnTypeModel(types, elements, queryResult);
        var returnTypeTemplateParams = returnTypeModel == null ? null : returnTypeModel.getTemplateParams(null);

        var templates = templateParams.stream()
            .map(TemplateModel.TemplateParam::paramName)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toMap((key) -> key, (paramName) ->
                Flux.zip(Flux.fromIterable(method.getParameters()), Flux.fromIterable(methodType.getParameterTypes()))
                    .toStream()
                    .filter(p -> p.getT1().getSimpleName().toString().equals(paramName) && p.getT2() instanceof DeclaredType && ((DeclaredType) p.getT2()).asElement().getKind().isClass())
                    .findFirst()
                    .map(t -> TemplateModel.parseEntityModel(elements, types, ((TypeElement) ((DeclaredType) t.getT2()).asElement())))
                    .orElseThrow(() -> new RuntimeException(String.format("Unknown parameter '%s' for query `%s`", paramName, source)))
            ));

        var templatesParams = new HashMap<String, Map<String, String>>(templates.size());
        templates.forEach((param, template) -> templatesParams.put(param, template.getTemplateParams(param)));

        var sql = source;
        for (var templateParam : templateParams) {
            String paramName = templateParam.paramName();

            Map<String, String> params;
            if (paramName == null) {
                params = returnTypeTemplateParams;
            } else {
                params = templatesParams.get(paramName);
            }

            var value = params.get(templateParam.template());
            if (value == null) {
                throw new RuntimeException(String.format("Unknown template `%s` for query %s", templateParam.rawTemplate(), source));
            }

            sql = sql.replace(templateParam.rawTemplate(), value);
        }
        return sql;
    }

    @Nullable
    private static TemplateModel detectReturnTypeModel(Types types, Elements elements, QueryResult result) {
        return null;
    }
}
