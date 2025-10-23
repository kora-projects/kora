package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils.Mapper;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.RepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameterParser;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;

public final class JdbcRepositoryGenerator implements RepositoryGenerator {
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public JdbcRepositoryGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public TypeSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, MethodSpec.Builder constructor) {
        var repositoryType = (DeclaredType) repositoryElement.asType();
        var queryMethods = DbUtils.findQueryMethods(this.types, this.elements, repositoryElement);
        this.enrichWithExecutor(repositoryElement, type, constructor);
        var resultMappers = new FieldFactory(this.types, elements, type, constructor, "_result_mapper_");
        var parameterMappers = new FieldFactory(this.types, elements, type, constructor, "_parameter_mapper_");

        int methodCounter = 1;
        for (var method : queryMethods) {
            var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
            var parameters = QueryParameterParser.parse(this.types, JdbcTypes.CONNECTION, JdbcTypes.PARAMETER_COLUMN_MAPPER, method, methodType);
            var queryAnnotation = AnnotationUtils.findAnnotation(method, DbUtils.QUERY_ANNOTATION);
            var queryString = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(queryAnnotation, "value");
            var query = QueryWithParameters.parse(filer, types, queryString, parameters, repositoryType, method);
            var resultMapper = this.parseResultMapper(method, methodType, parameters)
                .map(rm -> DbUtils.addMapper(resultMappers, rm))
                .orElse(null);

            DbUtils.addMappers(parameterMappers, DbUtils.parseParameterMappers(
                parameters,
                query,
                tn -> JdbcNativeTypes.findNativeType(tn) != null,
                JdbcTypes.PARAMETER_COLUMN_MAPPER
            ));
            var methodSpec = this.generate(repositoryElement, type, methodCounter, method, methodType, query, parameters, resultMapper, parameterMappers);
            type.addMethod(methodSpec);
            methodCounter++;
        }
        return type.addMethod(constructor.build()).build();
    }

    private Optional<Mapper> parseResultMapper(ExecutableElement method, ExecutableType methodType, List<QueryParameter> parameters) {
        var returnType = methodType.getReturnType();
        if (CommonUtils.isVoid(returnType)) {
            return Optional.empty();
        }
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            return Optional.empty();
        }

        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var generatedKeys = AnnotationUtils.isAnnotationPresent(method, DbUtils.ID_ANNOTATION);
        if (batchParam != null && !generatedKeys) {
            // either void or update count, no way to parse results from db with jdbc api
            if (ArrayTypeName.of(int.class).equals(TypeName.get(returnType))) {
                return Optional.empty();
            } else if (ArrayTypeName.of(long.class).equals(TypeName.get(returnType))) {
                return Optional.empty();
            } else {
                throw new ProcessingErrorException("@Batch method can't return arbitrary values, it can only return: void/UpdateCount or database-generated @Id", method);
            }
        }
        var mappings = CommonUtils.parseMapping(method);
        var mapperType = ParameterizedTypeName.get(JdbcTypes.RESULT_SET_MAPPER, TypeName.get(returnType).box());
        var resultSetMapper = mappings.getMapping(JdbcTypes.RESULT_SET_MAPPER);
        if (resultSetMapper != null) {
            return Optional.of(new Mapper(resultSetMapper.mapperClass(), mapperType, mappings.mapperTags()));
        }

        var rowMapper = mappings.getMapping(JdbcTypes.ROW_MAPPER);
        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mappings.mapperTags(), c -> CodeBlock.of("$T.listResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mappings.mapperTags(), c -> CodeBlock.of("$T.optionalResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            } else {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mappings.mapperTags(), c -> CodeBlock.of("$T.singleResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            }
        }
        return Optional.of(new Mapper(mapperType, mappings.mapperTags()));
    }

    @Override
    public ClassName repositoryInterface() {
        return JdbcTypes.JDBC_REPOSITORY;
    }

    private record QueryReplace(int start, int end, String name) {}

    public MethodSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, int methodNumber, ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters, @Nullable String resultMapperName, FieldFactory parameterMappers) {
        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var sql = query.rawQuery();
        List<QueryReplace> replaceParams = query.parameters().stream()
            .flatMap(p -> p.queryIndexes().stream().map(i -> new QueryReplace(i.start(), i.end(), p.sqlParameterName())))
            .sorted(Comparator.comparingInt(QueryReplace::start))
            .toList();
        int sqlIndexDiff = 0;
        for (var parameter : replaceParams) {
            int queryIndexAdjusted = parameter.start() - sqlIndexDiff;
            sql = sql.substring(0, queryIndexAdjusted) + "?" + sql.substring(queryIndexAdjusted + parameter.name().length() + 1);
            sqlIndexDiff += parameter.name().length();
        }

        var mb = DbUtils.queryMethodBuilder(method, methodType);
        var returnType = methodType.getReturnType();
        var connection = parameters.stream().filter(QueryParameter.ConnectionParameter.class::isInstance).findFirst()
            .map(p -> CodeBlock.of("$L", p.variable()))
            .orElse(CodeBlock.of("this._connectionFactory.currentConnection()"));

        var queryContextFieldName = "QUERY_CONTEXT_" + methodNumber;
        type.addField(
            FieldSpec.builder(DbUtils.QUERY_CONTEXT, queryContextFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("""
                    new $T(
                          $S,
                          $S,
                          $S
                        )""", DbUtils.QUERY_CONTEXT, query.rawQuery(), sql, DbUtils.operationName(method))
                .build());
        mb.addStatement("var _query = $L", queryContextFieldName);
        mb.addStatement("var _observation = this._connectionFactory.telemetry().observe(_query)");
        if (methodType.getReturnType().getKind() != TypeKind.VOID) {
            mb.addCode("return ");
        }
        CommonUtils.observe(mb, "_observation", methodType.getReturnType().getKind() == TypeKind.VOID ? "run" : "call", b -> {
            b.add("""
                var _conToUse = $L;
                $T _conToClose;
                if (_conToUse == null) {
                    _conToUse = this._connectionFactory.newConnection();
                    _conToClose = _conToUse;
                } else {
                    _conToClose = null;
                }
                """, connection, JdbcTypes.CONNECTION);
            var generatedKeys = AnnotationUtils.isAnnotationPresent(method, DbUtils.ID_ANNOTATION);
            if (generatedKeys) {
                b.beginControlFlow("try (_conToClose; var _stmt = _conToUse.prepareStatement(_query.sql(), $T.RETURN_GENERATED_KEYS))", Statement.class);
            } else {
                b.beginControlFlow("try (_conToClose; var _stmt = _conToUse.prepareStatement(_query.sql()))");
            }
            b.add(StatementSetterGenerator.generate(method, query, parameters, batchParam, parameterMappers));
            if (MethodUtils.isVoid(method)) {
                if (batchParam != null) {
                    b.addStatement("_stmt.executeBatch()");
                } else {
                    b.addStatement("_stmt.execute()");
                }
            } else if (batchParam != null) {
                if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                    b.addStatement("var _batchResult = _stmt.executeLargeBatch()");
                    b.addStatement("return new $T($T.of(_batchResult).sum())", DbUtils.UPDATE_COUNT, LongStream.class);
                } else if (returnType.toString().equals("long[]")) {
                    b.addStatement("var _batchResult = _stmt.executeLargeBatch()");
                    b.addStatement("return _batchResult");
                } else if (returnType.toString().equals("int[]")) {
                    b.addStatement("var _batchResult = _stmt.executeBatch()");
                    b.addStatement("return _batchResult");
                } else if (generatedKeys) {
                    var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive()
                        ? CodeBlock.of("_result")
                        : CodeBlock.of("$T.requireNonNull(_result, $S)", Objects.class, "Result mapping is expected non-null, but was null");

                    b.addStatement("var _batchResult = _stmt.executeBatch()");
                    b.beginControlFlow("try (var _rs = _stmt.getGeneratedKeys())")
                        .addStatement("var _result = $L.apply(_rs)", resultMapperName)
                        .addStatement("return $L", result)
                        .endControlFlow();
                } else {
                    b.addStatement("var _batchResult = _stmt.executeBatch()");
                    b.addStatement("_telemetry.close(null)");
                }
            } else if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                b
                    .addStatement("var _updateCount = _stmt.executeLargeUpdate()")
                    .addStatement("return new $T(_updateCount)", DbUtils.UPDATE_COUNT);
            } else if (generatedKeys) {
                var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive()
                    ? CodeBlock.of("_result")
                    : CodeBlock.of("$T.requireNonNull(_result, $S)", Objects.class, "Result mapping is expected non-null, but was null");

                b.add("_stmt.execute();\n");
                b.beginControlFlow("try (var _rs = _stmt.getGeneratedKeys())")
                    .addStatement("var _result = $L.apply(_rs)", resultMapperName)
                    .addStatement("return $L", result)
                    .endControlFlow();
            } else {
                var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive()
                    ? CodeBlock.of("_result")
                    : CodeBlock.of("$T.requireNonNull(_result, $S)", Objects.class, "Result mapping is expected non-null, but was null");

                Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());

                b.beginControlFlow("try (var _rs = _stmt.executeQuery())")
                    .addStatement("var _result = $L.apply(_rs)", resultMapperName)
                    .addStatement("return $L", result)
                    .endControlFlow();
            }
            b.nextControlFlow("catch (java.sql.SQLException e)")
                .addStatement("_observation.observeError(e)")
                .addStatement("throw new ru.tinkoff.kora.database.jdbc.RuntimeSqlException(e)");
            b.nextControlFlow("catch (Exception e)")
                .addStatement("_observation.observeError(e)")
                .addStatement("throw e");
            b.nextControlFlow("finally")
                .addStatement("_observation.end()")
                .endControlFlow();
        });
        mb.addCode(";\n");
        return mb.build();
    }

    public void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder) {
        builder.addField(JdbcTypes.CONNECTION_FACTORY, "_connectionFactory", Modifier.PRIVATE, Modifier.FINAL);
        builder.addSuperinterface(JdbcTypes.JDBC_REPOSITORY);
        builder.addMethod(MethodSpec.methodBuilder("getJdbcConnectionFactory")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(JdbcTypes.CONNECTION_FACTORY)
            .addCode("return this._connectionFactory;")
            .build());

        var executorTag = DbUtils.getTag(repositoryElement);
        if (executorTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(JdbcTypes.CONNECTION_FACTORY, "_connectionFactory").addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", executorTag).build()).build());
        } else {
            constructorBuilder.addParameter(JdbcTypes.CONNECTION_FACTORY, "_connectionFactory");
        }
        constructorBuilder.addStatement("this._connectionFactory = _connectionFactory");
    }
}
