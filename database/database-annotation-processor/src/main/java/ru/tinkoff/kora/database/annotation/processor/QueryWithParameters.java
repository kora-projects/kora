package ru.tinkoff.kora.database.annotation.processor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public record QueryWithParameters(String rawQuery, List<QueryParameter> parameters) {

    public record QueryParameter(String sqlParameterName, int methodIndex, List<QueryIndex> queryIndexes, List<Integer> sqlIndexes) {

        public record QueryIndex(int start, int end) { }
    }

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

    public static QueryWithParameters parse(Filer filer,
                                            Types types,
                                            String rawSql,
                                            List<ru.tinkoff.kora.database.annotation.processor.model.QueryParameter> parameters,
                                            DeclaredType repositoryType,
                                            ExecutableElement method) {
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

        var sql = new QueryMacrosParser(types).parse(rawSql, repositoryType, method);
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
                parseSimpleParameter(sql, i, parameterName).ifPresent(params::add);
            }
            if (parameter instanceof ru.tinkoff.kora.database.annotation.processor.model.QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().columns()) {
                    parseSimpleParameter(sql, i, field.queryParameterName(parameterName)).ifPresent(params::add);
                }
                parseEntityDirectParameter(sql, i, parameterName).ifPresent(params::add);
            }
            if (params.size() == size) {
                throw new ProcessingErrorException("Parameter usage wasn't found in query: " + parameterName, parameter.variable());
            }
        }

        var paramsNumbers = params
            .stream()
            .map(QueryParameter::sqlIndexes)
            .flatMap(Collection::stream)
            .sorted()
            .toList();

        params = params.stream()
            .map(p -> new QueryParameter(p.sqlParameterName(), p.methodIndex(), p.queryIndexes(), p.sqlIndexes()
                .stream()
                .map(paramsNumbers::indexOf)
                .toList()
            ))
            .toList();

        return new QueryWithParameters(sql, params);
    }


    private static Optional<QueryParameter> parseSimpleParameter(String rawSql, int methodParameterNumber, String sqlParameterName) {
        var result = new ArrayList<QueryParameter.QueryIndex>();
        var pattern = sqlParameterPattern(sqlParameterName);
        var matcher = pattern.matcher(rawSql);
        while (matcher.find()) {
            var mr = matcher.toMatchResult();
            var start = mr.start(1);
            var end = mr.end();
            result.add(new QueryParameter.QueryIndex(start, end));
        }

        return (result.isEmpty())
            ? Optional.empty()
            : Optional.of(new QueryParameter(sqlParameterName, methodParameterNumber, result, result.stream()
            .map(QueryParameter.QueryIndex::start)
            .toList()));
    }

    private static Optional<QueryParameter> parseEntityDirectParameter(String rawSql, int methodParameterNumber, String sqlParameterName) {
        var result = new ArrayList<QueryParameter.QueryIndex>();
        var pattern = sqlParameterPattern(sqlParameterName);
        var matcher = pattern.matcher(rawSql);
        while (matcher.find()) {
            var mr = matcher.toMatchResult();
            var start = mr.start(1);
            var end = mr.end();
            result.add(new QueryParameter.QueryIndex(start, end));
        }

        return (result.isEmpty())
            ? Optional.empty()
            : Optional.of(new QueryParameter(sqlParameterName, methodParameterNumber, result, result.stream()
            .map(QueryParameter.QueryIndex::start)
            .toList()));
    }

    private static Pattern sqlParameterPattern(String sqlParameterName) {
        return Pattern.compile("[\\s\\n,(\\[](?<param>:" + sqlParameterName + ")(?=[\\s\\n,:)\\];]|$)");
    }
}
