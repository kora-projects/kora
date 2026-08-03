package io.koraframework.database.annotation.processor;

import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
                                            List<io.koraframework.database.annotation.processor.model.QueryParameter> parameters,
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
                    throw new ProcessingErrorException("""
                        SQL query resource wasn't found:
                          %s

                        Problem:
                          Query starts with classpath:/ but the resource can't be read from SOURCE_PATH or CLASS_PATH.

                        Hint:
                          Resource path is resolved relative to source/classpath roots after the classpath:/ prefix.

                        Fix:
                          Check that the SQL file exists, the path is correct, and the resource is included in the module sources/resources.
                        """.formatted(rawSql), method);
                }
            }
        }

        rawSql = rawSql.strip();

        var sql = new QueryMacrosParser(types).parse(rawSql, repositoryType, method);
        List<QueryParameter> params = new ArrayList<>();
        validateSqlPlaceholders(sql, parameters, method);

        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            var parameterName = parameter.name();
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.ConnectionParameter) {
                continue;
            }
            var size = params.size();
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.BatchParameter batchParameter) {
                parameter = batchParameter.parameter();
            }
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.SimpleParameter simpleParameter) {
                parseSimpleParameter(sql, i, parameterName).ifPresent(params::add);
            }
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.EntityParameter entityParameter) {
                for (var field : entityParameter.entity().columns()) {
                    parseSimpleParameter(sql, i, field.queryParameterName(parameterName)).ifPresent(params::add);
                }
                parseEntityDirectParameter(sql, i, parameterName).ifPresent(params::add);
            }
            if (params.size() == size) {
                throw new ProcessingErrorException("""
                    Query parameter is unused:
                      %s

                    Problem:
                      Method parameter wasn't found in SQL query placeholders.

                    Hint:
                      Parameters are matched by ':name'. Entity parameters are matched by their field placeholders, for example ':entity.field'.

                    Fix:
                      Add ':%s' to the query, rename the method parameter to match the SQL placeholder, or remove the unused parameter.
                    """.formatted(parameterName, parameterName), parameter.variable());
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
        return Pattern.compile("[\\s\\n,=(\\[](?<param>:" + sqlParameterName + ")(?=[\\s\\n,:)=\\];]|$)");
    }

    private static void validateSqlPlaceholders(String sql, List<io.koraframework.database.annotation.processor.model.QueryParameter> parameters, ExecutableElement method) {
        var availableParameters = new LinkedHashMap<String, io.koraframework.database.annotation.processor.model.QueryParameter>();
        var availableEntityFields = new LinkedHashMap<String, List<String>>();
        for (var parameter : parameters) {
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.ConnectionParameter) {
                continue;
            }
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.BatchParameter batchParameter) {
                parameter = batchParameter.parameter();
            }
            availableParameters.put(parameter.name(), parameter);
            if (parameter instanceof io.koraframework.database.annotation.processor.model.QueryParameter.EntityParameter entityParameter) {
                var fields = entityParameter.entity().columns().stream()
                    .map(c -> c.queryParameterName(entityParameter.name()))
                    .toList();
                fields.forEach(field -> availableParameters.put(field, entityParameter));
                availableEntityFields.put(entityParameter.name(), fields);
            }
        }

        var matcher = allSqlParameterPattern().matcher(sql);
        while (matcher.find()) {
            var placeholder = matcher.group("param").substring(1);
            if (availableParameters.containsKey(placeholder)) {
                continue;
            }
            var dotIndex = placeholder.indexOf('.');
            if (dotIndex > 0) {
                var rootName = placeholder.substring(0, dotIndex);
                var entityFields = availableEntityFields.get(rootName);
                if (entityFields != null) {
                    throw new ProcessingErrorException("""
                        SQL query placeholder has no matching entity field:
                          :%s

                        Problem:
                          Query contains ':%s', but parameter '%s' has no mapped field with this name.

                        Available fields for parameter '%s':
                        %s

                        Hint:
                          Entity fields are matched as ':entity.field'. Embedded fields use the same dotted form.

                        Fix:
                          Rename ':%s' to one of the available fields, or add the missing field to the entity.
                        """.formatted(placeholder, placeholder, rootName, rootName, formatAvailable(entityFields), placeholder), method);
                }
            }
            throw new ProcessingErrorException("""
                SQL query placeholder has no matching method parameter:
                  :%s

                Problem:
                  Query contains ':%s', but repository method has no parameter or entity field with this name.

                Available parameters:
                %s

                Hint:
                  Parameters are matched by ':name'. Entity fields are matched as ':entity.field'.

                Fix:
                  Rename ':%s' to one of the available parameters, add a method parameter named '%s', or use the correct entity field placeholder.
                """.formatted(placeholder, placeholder, formatAvailable(availableParameters.keySet()), placeholder, placeholder), method);
        }
    }

    private static String formatAvailable(Collection<String> names) {
        if (names.isEmpty()) {
            return "  - <none>";
        }
        return names.stream()
            .map(name -> "  - :" + name)
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Pattern allSqlParameterPattern() {
        return Pattern.compile("(?:^|[\\s\\n,=(\\[<>+\\-*/|&])(?<param>:[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)(?=[\\s\\n,:)=\\];<>+\\-*/|&]|$)");
    }
}
