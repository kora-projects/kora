package ru.tinkoff.kora.database.annotation.processor.jdbc;

import com.squareup.javapoet.*;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.Visitors;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
import ru.tinkoff.kora.database.annotation.processor.DbUtils.Mapper;
import ru.tinkoff.kora.database.annotation.processor.QueryWithParameters;
import ru.tinkoff.kora.database.annotation.processor.RepositoryGenerator;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameter;
import ru.tinkoff.kora.database.annotation.processor.model.QueryParameterParser;

import javax.annotation.Nullable;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

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
        for (var method : queryMethods) {
            var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
            var parameters = QueryParameterParser.parse(this.types, JdbcTypes.CONNECTION, method, methodType);
            var queryAnnotation = CommonUtils.findDirectAnnotation(method, DbUtils.QUERY_ANNOTATION);
            var queryString = CommonUtils.parseAnnotationValueWithoutDefault(queryAnnotation, "value").toString();
            var query = QueryWithParameters.parse(filer, queryString, parameters);
            this.parseResultMappers(method, methodType, parameters)
                .map(List::of)
                .ifPresent(resultMapper -> DbUtils.addMappers(this.types, type, constructor, resultMapper));
            DbUtils.addMappers(this.types, type, constructor, DbUtils.parseParameterMappers(
                method,
                parameters,
                query,
                tn -> JdbcNativeTypes.findNativeType(tn) != null,
                JdbcTypes.PARAMETER_COLUMN_MAPPER
            ));
            var methodSpec = this.generate(method, methodType, query, parameters);
            type.addMethod(methodSpec);
        }
        return type.addMethod(constructor.build()).build();
    }

    private Optional<Mapper> parseResultMappers(ExecutableElement method, ExecutableType methodType, List<QueryParameter> parameters) {
        var returnType = methodType.getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            return Optional.empty();
        }
        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        if (batchParam != null) {
            // either void or update count, no way to parse results from db with jdbc api
            return Optional.empty();
        }
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            return Optional.empty();
        }

        if (CommonUtils.isMono(returnType)) {
            returnType = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
        }
        var mapperName = DbUtils.resultMapperName(method);
        var mappings = CommonUtils.parseMapping(method);
        var mapperType = ParameterizedTypeName.get(
            JdbcTypes.RESULT_SET_MAPPER, TypeName.get(returnType).box()
        );
        var resultSetMapper = mappings.getMapping(JdbcTypes.RESULT_SET_MAPPER);
        if (resultSetMapper != null) {
            return Optional.of(new Mapper(resultSetMapper.mapperClass(), mapperType, mapperName));
        }
        var rowMapper = mappings.getMapping(JdbcTypes.ROW_MAPPER);
        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.listResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.optionalResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            } else {
                return Optional.of(new Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.singleResultSetMapper($L)", JdbcTypes.RESULT_SET_MAPPER, c)));
            }
        }
        return Optional.of(new Mapper(mapperType, mapperName));
    }

    @Override
    @Nullable
    public TypeMirror repositoryInterface() {
        return this.repositoryInterface;
    }

    public MethodSpec generate(ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters) {
        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var sql = query.rawQuery();
        for (var parameter : query.parameters().stream().sorted(Comparator.<QueryWithParameters.QueryParameter, Integer>comparing(p -> p.sqlParameterName().length()).reversed()).toList()) {
            sql = sql.replace(":" + parameter.sqlParameterName(), "?");
        }

        var b = DbUtils.queryMethodBuilder(method, methodType);
        if (CommonUtils.isMono(methodType.getReturnType())) {
            b.addCode("return $T.fromCompletionStage(() -> $T.supplyAsync(() -> {$>\n", Mono.class, CompletableFuture.class);
        }
        var connection = parameters.stream().filter(QueryParameter.ConnectionParameter.class::isInstance).findFirst()
            .map(p -> CodeBlock.of("$L", p.variable()))
            .orElse(CodeBlock.of("this._connectionFactory.currentConnection()"));
        b.addCode("""
            var _conToUse = $L;
            $T _conToClose;
            if (_conToUse == null) {
                _conToUse = this._connectionFactory.newConnection();
                _conToClose = _conToUse;
            } else {
                _conToClose = null;
            }
            var _query = new $T(
              $S,
              $S
            );
            var _telemetry = this._connectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.current(), _query);
            try (_conToClose; var _stmt = _conToUse.prepareStatement(_query.sql())) {$>
            """, connection, JdbcTypes.CONNECTION, DbUtils.QUERY_CONTEXT, query.rawQuery(), sql);
        b.addCode(StatementSetterGenerator.generate(method, query, parameters, batchParam));
        if (methodType.getReturnType().getKind() == TypeKind.VOID) {
            if (batchParam != null) {
                b.addStatement("var _batchResult = _stmt.executeBatch()");
            } else {
                b
                    .addStatement("_stmt.execute();")
                    .addStatement("var updateCount = _stmt.getUpdateCount()");
            }
            b.addStatement("_telemetry.close(null)");
        } else if (batchParam != null) {
            b.addStatement("var _batchResult = _stmt.executeBatch()");
            b.addStatement("_telemetry.close(null)");
            if (methodType.getReturnType().toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                b.addStatement("return $T.of_batchResult).sum()", IntStream.class);
            } else {
                b.addStatement("return _batchResult");
            }
        } else if (methodType.getReturnType().toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            b
                .addStatement("var _updateCount = _stmt.executeLargeUpdate()")
                .addStatement("_telemetry.close(null)")
                .addStatement("return new $T(_updateCount)", DbUtils.UPDATE_COUNT);
        } else {
            var result = CommonUtils.isNullable(method) || method.getReturnType().getKind().isPrimitive()
                ? CodeBlock.of("_result")
                : CodeBlock.of("$T.requireNonNull(_result)", Objects.class);
            b.addCode("try (var _rs = _stmt.executeQuery()) {$>\n")
                .addCode("var _result = $L.apply(_rs);\n", DbUtils.resultMapperName(method))
                .addCode("_telemetry.close(null);\n")
                .addCode("return $L;", result)
                .addCode("$<\n}\n");
        }
        b.addCode("$<\n} catch (java.sql.SQLException e) {\n")
            .addCode("  _telemetry.close(e);\n")
            .addCode("  throw new ru.tinkoff.kora.database.jdbc.RuntimeSqlException(e);\n")
            .addCode("}  catch (Exception e) {\n")
            .addCode("  _telemetry.close(e);\n")
            .addCode("  throw e;\n")
            .addCode("}\n");
        if (CommonUtils.isMono(methodType.getReturnType())) {
            b.addCode("$<\n}));\n");
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

        var needThreadPool = queryMethods.stream().anyMatch(e -> CommonUtils.isMono(e.getReturnType()));
        if (needThreadPool) {
            builder.addField(TypeName.get(Executor.class), "_executor", Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addStatement("this._executor = _executor");
            if (executorTag != null) {
                constructorBuilder.addParameter(ParameterSpec.builder(TypeName.get(Executor.class), "_executor").addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", executorTag).build()).build());
            } else {
                constructorBuilder.addParameter(ParameterSpec.builder(TypeName.get(Executor.class), "_executor").build());
            }
        }
    }
}
