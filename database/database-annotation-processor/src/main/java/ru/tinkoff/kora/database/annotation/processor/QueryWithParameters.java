package ru.tinkoff.kora.database.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record QueryWithParameters(String rawQuery, List<QueryParameter> parameters) {

    public record QueryParameter(String sqlParameterName, int methodIndex, List<Integer> sqlIndexes) {}

    @Nullable
    public QueryParameter find(String name) {
        for (var parameter : parameters) {
            if (parameter.sqlParameterName.equals(name)) {
                return parameter;
            }
        }
        return null;
    }

    @Nullable
    public QueryParameter find(int methodIndex) {
        for (var parameter : parameters) {
            if (parameter.methodIndex == methodIndex) {
                return parameter;
            }
        }
        return null;
    }

    public static QueryWithParameters parse(Filer filer, String rawSql, List<ru.tinkoff.kora.database.annotation.processor.model.QueryParameter> parameters) {
        if (rawSql.startsWith("classpath:/")) {
            var path = rawSql.substring(11);
            var i = path.lastIndexOf("/");
            final String packageName;
            final String resourceName;
            if (i > 0) {
                packageName = path.substring(0, i).replace('/', '.');
                resourceName = path.substring(i + 1);
            } else {
                packageName = "";
                resourceName = path;
            }
            try (var is = filer.getResource(StandardLocation.SOURCE_PATH, packageName, resourceName).openInputStream()) {
                rawSql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                try (var is = filer.getResource(StandardLocation.CLASS_PATH, packageName, resourceName).openInputStream()) {
                    rawSql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e1) {
                    e.addSuppressed(e1);
                    throw new RuntimeException(e);
                }
            }
        }

        List<QueryParameter> params = new ArrayList<>();

        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            var parameterName = parameter.name();
            if (parameter instanceof ru.tinkoff.kora.database.annotation.processor.model.QueryParameter.ConnectionParameter) {
                continue;
            }
            var size = params.size();
            if (parameter instanceof ru.tinkoff.kora.database.annotation.processor.model.QueryParameter.BatchParameter batchParameter) {
                parameter = batchParameter.parameter();
            }
            if (parameter instanceof ru.tinkoff.kora.database.annotation.processor.model.QueryParameter.SimpleParameter simpleParameter) {
                parseSimpleParameter(rawSql, i, parameterName).ifPresent(params::add);
            }
            if (parameter instanceof ru.tinkoff.kora.database.annotation.processor.model.QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().columns()) {
                    parseSimpleParameter(rawSql, i, field.queryParameterName(parameterName)).ifPresent(params::add);
                }
            }
            if (params.size() == size) {
                throw new ProcessingErrorException("Parameter usage was not found in query: " + parameterName, parameter.variable());
            }
        }

        var paramsNumbers = params
            .stream()
            .map(QueryParameter::sqlIndexes)
            .flatMap(Collection::stream)
            .sorted()
            .toList();

        params = params.stream()
            .map(p -> new QueryParameter(p.sqlParameterName(), p.methodIndex(), p.sqlIndexes()
                .stream()
                .map(paramsNumbers::indexOf)
                .toList()
            ))
            .toList();

        return new QueryWithParameters(rawSql, params);
    }


    private static Optional<QueryParameter> parseSimpleParameter(String rawSql, int methodParameterNumber, String sqlParameterName) {
        int index = -1;
        var result = new ArrayList<Integer>();
        while ((index = rawSql.indexOf(":" + sqlParameterName, index + 1)) >= 0) {
            var indexAfter = index + sqlParameterName.length() + 1;
            if (rawSql.length() >= indexAfter + 1) {
                var charAfter = rawSql.charAt(indexAfter);
                if (Character.isAlphabetic(charAfter) || charAfter == '_' || charAfter == '$' || Character.isDigit(charAfter)) {
                    continue;
                }
            }
            result.add(index);
        }

        return (result.isEmpty())
            ? Optional.empty()
            : Optional.of(new QueryParameter(sqlParameterName, methodParameterNumber, result));
    }
}
