package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.common.Tag;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

public final class JdbcRepositoryGenerator implements RepositoryGenerator {
    private final TypeMirror repositoryInterface;
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public JdbcRepositoryGenerator(ProcessingEnvironment processingEnv) {
        var repository = processingEnv.getElementUtils().getTypeElement(JdbcTypes.JDBC_REPOSITORY.canonicalName());
        if (repository == null) {
            this.repositoryInterface = null;
        } else {
            this.repositoryInterface = repository.asType();
        }
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public TypeSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, MethodSpec.Builder constructor) {
        var repositoryType = (DeclaredType) repositoryElement.asType();
        var queryMethods = DbUtils.findQueryMethods(this.types, this.elements, repositoryElement);
        this.enrichWithExecutor(repositoryElement, type, constructor, queryMethods);
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
        if (CommonUtils.isMono(returnType)) {
            returnType = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
        } else if (CommonUtils.isFuture(returnType)) {
            returnType = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
        }

        if (CommonUtils.isVoid(returnType)) {
            return Optional.empty();
        }

        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var generatedKeys = AnnotationUtils.isAnnotationPresent(method, DbUtils.ID_ANNOTATION);
        if (batchParam != null && !generatedKeys) {
            // either void or update count, no way to parse results from db with jdbc api
            return Optional.empty();
        }
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            return Optional.empty();
        }
        var mappings = CommonUtils.parseMapping(method);
        var mapperType = ParameterizedTypeName.get(
            JdbcTypes.RESULT_SET_MAPPER, TypeName.get(returnType).box()
        );
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
    @Nullable
    public TypeMirror repositoryInterface() {
        return this.repositoryInterface;
    }

    public MethodSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, int methodNumber, ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters, @Nullable String resultMapperName, FieldFactory parameterMappers) {
        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var sql = query.rawQuery();
        for (var parameter : query.parameters().stream().sorted(Comparator.<QueryWithParameters.QueryParameter>comparingInt(s -> s.sqlParameterName().length()).reversed()).toList()) {
            sql = sql.replace(":" + parameter.sqlParameterName(), "?");
        }

        var b = DbUtils.queryMethodBuilder(method, methodType);
        var returnType = methodType.getReturnType();
        final boolean isMono = CommonUtils.isMono(returnType);
        final boolean isFuture = CommonUtils.isFuture(returnType);
        b.addStatement("var _ctxCurrent = ru.tinkoff.kora.common.Context.current()");
        if (isMono) {
            b.addCode("return $T.fromCompletionStage($T.supplyAsync(() -> {$>\n", CommonClassNames.mono, CompletableFuture.class);
            returnType = ((DeclaredType) returnType).getTypeArguments().get(0);
        } else if (isFuture) {
            b.addCode("return $T.supplyAsync(() -> {$>\n", CompletableFuture.class);
            returnType = ((DeclaredType) returnType).getTypeArguments().get(0);
        }
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
        b.addStatement("var _query = $L", queryContextFieldName);

        if (isFuture || isMono) {
            b.addCode("""
                var _ctxFork = _ctxCurrent.fork();
                _ctxFork.inject();
                var _telemetry = this._connectionFactory.telemetry().createContext(_ctxFork, _query);
                """);
        } else {
            b.addCode("""
                var _telemetry = this._connectionFactory.telemetry().createContext(_ctxCurrent, _query);
                """);
        }

        b.addCode("""
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
            b.addCode("try (_conToClose; var _stmt = _conToUse.prepareStatement(_query.sql(), $T.RETURN_GENERATED_KEYS)) {$>\n", Statement.class);
        } else {
            b.addCode("try (_conToClose; var _stmt = _conToUse.prepareStatement(_query.sql())) {$>\n");
        }
        b.addCode(StatementSetterGenerator.generate(method, query, parameters, batchParam, parameterMappers));
        if (MethodUtils.isVoid(method)
            || isMono && MethodUtils.isVoidGeneric(methodType.getReturnType())
            || isFuture && MethodUtils.isVoidGeneric(methodType.getReturnType())) {

            if (batchParam != null) {
                b.addStatement("var _batchResult = _stmt.executeBatch()");
            } else {
                b.addStatement("_stmt.execute()");
                b.addStatement("var updateCount = _stmt.getUpdateCount()");
            }
            b.addStatement("_telemetry.close(null)");
            if (isMono) {
                b.addStatement("return null");
            } else if (isFuture) {
                b.addStatement("return null");
            }
        } else if (batchParam != null) {
            if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                b.addStatement("var _batchResult = _stmt.executeLargeBatch()");
                b.addStatement("_telemetry.close(null)");
                b.addStatement("return new $T($T.of(_batchResult).sum())", DbUtils.UPDATE_COUNT, LongStream.class);
            } else if (returnType.toString().equals("long[]")) {
                b.addStatement("var _batchResult = _stmt.executeLargeBatch()");
                b.addStatement("_telemetry.close(null)");
                b.addStatement("return _batchResult");
            } else if (returnType.toString().equals("int[]")) {
                b.addStatement("var _batchResult = _stmt.executeBatch()");
                b.addStatement("_telemetry.close(null)");
                b.addStatement("return _batchResult");
            } else if (generatedKeys) {
                var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive() || isMono || isFuture
                    ? CodeBlock.of("_result")
                    : CodeBlock.of("$T.requireNonNull(_result)", Objects.class);

                b.addStatement("var _batchResult = _stmt.executeBatch()");
                b.addCode("try (var _rs = _stmt.getGeneratedKeys()) {$>\n")
                    .addCode("var _result = $L.apply(_rs);\n", resultMapperName)
                    .addCode("_telemetry.close(null);\n")
                    .addCode("return $L;", result)
                    .addCode("$<\n}\n");
            } else {
                b.addStatement("var _batchResult = _stmt.executeBatch()");
                b.addStatement("_telemetry.close(null)");
            }
        } else if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            b
                .addStatement("var _updateCount = _stmt.executeLargeUpdate()")
                .addStatement("_telemetry.close(null)")
                .addStatement("return new $T(_updateCount)", DbUtils.UPDATE_COUNT);
        } else if (generatedKeys) {
            var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive() || isMono || isFuture
                ? CodeBlock.of("_result")
                : CodeBlock.of("$T.requireNonNull(_result)", Objects.class);

            b.addCode("_stmt.execute();\n");
            b.addCode("try (var _rs = _stmt.getGeneratedKeys()) {$>\n")
                .addCode("var _result = $L.apply(_rs);\n", resultMapperName)
                .addCode("_telemetry.close(null);\n")
                .addCode("return $L;", result)
                .addCode("$<\n}\n");
        } else {
            var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive() || isMono || isFuture
                ? CodeBlock.of("_result")
                : CodeBlock.of("$T.requireNonNull(_result)", Objects.class);

            Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());

            b.addCode("try (var _rs = _stmt.executeQuery()) {$>\n")
                .addCode("var _result = $L.apply(_rs);\n", resultMapperName)
                .addCode("_telemetry.close(null);\n")
                .addCode("return $L;", result)
                .addCode("$<\n}\n");
        }
        b.addCode("$<\n} catch (java.sql.SQLException e) {\n")
            .addCode("  _telemetry.close(e);\n")
            .addCode("  throw new ru.tinkoff.kora.database.jdbc.RuntimeSqlException(e);\n")
            .addCode("} catch (Exception e) {\n")
            .addCode("  _telemetry.close(e);\n")
            .addCode("  throw e;\n");

        if (isMono || isFuture) {
            b.addCode("} finally {\n")
                .addCode("  _ctxCurrent.inject();\n")
                .addCode("}\n");
        } else {
            b.addCode("}\n");
        }

        if (isMono) {
            b.addCode("$<\n}, _executor));\n");
        } else if (isFuture) {
            b.addCode("$<\n}, _executor);\n");
        }
        return b.build();
    }

    public void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder, List<ExecutableElement> queryMethods) {
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
            constructorBuilder.addParameter(ParameterSpec.builder(JdbcTypes.CONNECTION_FACTORY, "_connectionFactory").addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", executorTag).build()).build());
        } else {
            constructorBuilder.addParameter(JdbcTypes.CONNECTION_FACTORY, "_connectionFactory");
        }
        constructorBuilder.addStatement("this._connectionFactory = _connectionFactory");

        var needThreadPool = queryMethods.stream().anyMatch(e -> CommonUtils.isMono(e.getReturnType()) || CommonUtils.isFuture(e.getReturnType()));
        if (needThreadPool && executorTag != null) {
            builder.addField(TypeName.get(Executor.class), "_executor", Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addStatement("this._executor = _executor");
            constructorBuilder.addParameter(ParameterSpec.builder(TypeName.get(Executor.class), "_executor")
                .addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", executorTag).build())
                .build());
        } else if (needThreadPool) {
            builder.addField(TypeName.get(Executor.class), "_executor", Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addStatement("this._executor = _executor");
            constructorBuilder.addParameter(ParameterSpec.builder(TypeName.get(Executor.class), "_executor")
                .addAnnotation(TagUtils.makeAnnotationSpecForTypes(JdbcTypes.JDBC_DATABASE))
                .build());
        }
    }
}
