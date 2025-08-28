package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CassandraRepositoryGenerator implements RepositoryGenerator {
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public CassandraRepositoryGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public ClassName repositoryInterface() {
        return CassandraTypes.REPOSITORY;
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
            var parameters = QueryParameterParser.parse(this.types, CassandraTypes.CONNECTION, CassandraTypes.PARAMETER_COLUMN_MAPPER, method, methodType);
            var queryAnnotation = AnnotationUtils.findAnnotation(method, DbUtils.QUERY_ANNOTATION);
            var queryString = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(queryAnnotation, "value");
            var query = QueryWithParameters.parse(filer, types, queryString, parameters, repositoryType, method);
            var resultMapperName = this.parseResultMapper(method, parameters, methodType)
                .map(rm -> DbUtils.addMapper(resultMappers, rm))
                .orElse(null);
            DbUtils.addMappers(parameterMappers, DbUtils.parseParameterMappers(
                parameters,
                query,
                tn -> CassandraNativeTypes.findNativeType(tn) != null,
                CassandraTypes.PARAMETER_COLUMN_MAPPER
            ));
            var methodSpec = this.generate(type, methodCounter, method, methodType, query, parameters, resultMapperName, parameterMappers);
            type.addMethod(methodSpec);
            methodCounter++;
        }
        return type.addMethod(constructor.build()).build();
    }

    private record QueryReplace(int start, int end, String name) {}

    private MethodSpec generate(TypeSpec.Builder type, int methodNumber, ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters, @Nullable String resultMapperName, FieldFactory parameterMappers) {
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

        var b = DbUtils.queryMethodBuilder(method, methodType);

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

        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        String profile = null;
        var profileAnnotation = AnnotationUtils.findAnnotation(method, CassandraTypes.CASSANDRA_PROFILE);
        if (profileAnnotation != null) {
            profile = AnnotationUtils.parseAnnotationValueWithoutDefault(profileAnnotation, "value");
        }
        var returnType = methodType.getReturnType();
        var isFuture = CommonUtils.isFuture(returnType);
        if (isFuture) {
            b.addStatement("var _ctxCurrent = $T.current()", CommonClassNames.context);
            b.addStatement("var _ctxFork = _ctxCurrent.fork()");
            b.addStatement("var _telemetry = this._connectionFactory.telemetry().createContext(_ctxFork, _query)", CommonClassNames.context);
            b.addStatement("var _session = this._connectionFactory.currentSession()");
            b.addCode("return _session.prepareAsync(_query.sql())\n");
            b.addCode("  .thenCompose(_st -> {$>$>\n");
            b.addStatement("_ctxFork.inject()");
            b.addStatement("var _stmt = _st.boundStatementBuilder()");
        } else {
            b.addStatement("var _ctxCurrent = $T.current()", CommonClassNames.context);
            b.addStatement("var _telemetry = this._connectionFactory.telemetry().createContext(_ctxCurrent, _query)", CommonClassNames.context);
            b.addStatement("var _session = this._connectionFactory.currentSession()");
            b.addStatement("var _stmt = _session.prepare(_query.sql()).boundStatementBuilder()");
        }
        if (profile != null) {
            b.addStatement("_stmt.setExecutionProfileName($S)", profile);
        }

        StatementSetterGenerator.generate(b, method, query, parameters, batchParam, parameterMappers);
        if (isFuture) {
            if (CommonUtils.isVoid(((DeclaredType) returnType).getTypeArguments().get(0))) {
                b.addStatement("return _session.executeAsync(_s).thenApply(_rs -> (Void)null)");
            } else {
                Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());
                b.addStatement("return _session.executeAsync(_s).thenCompose($N::apply)", resultMapperName);
            }
            b.addCode("""
                $<})$<
                  .whenComplete((_result, _error) -> {
                    _telemetry.close(_error);
                    _ctxCurrent.inject();
                  })""");

            if (((DeclaredType) returnType).asElement().toString().equals(CompletableFuture.class.getCanonicalName())) {
                b.addCode(".toCompletableFuture();");
            } else {
                b.addCode(";");
            }
        } else {
            b.beginControlFlow("try");
            b.addStatement("var _rs = _session.execute(_s)");
            if (returnType.getKind() != TypeKind.VOID) {
                Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());
                b.addStatement("var _result = $N.apply(_rs)", resultMapperName);
            }
            b.addStatement("_telemetry.close(null)");
            if (returnType.getKind() != TypeKind.VOID) {
                b.addStatement("return _result");
            }
            b.nextControlFlow("catch (Exception _e)")
                .addStatement("_telemetry.close(_e)")
                .addStatement("throw _e")
                .endControlFlow();
        }
        return b.build();
    }


    private Optional<DbUtils.Mapper> parseResultMapper(ExecutableElement method, List<QueryParameter> parameters, ExecutableType methodType) {
        for (var parameter : parameters) {
            if (parameter instanceof QueryParameter.BatchParameter) {
                return Optional.empty();
            }
        }
        var returnType = methodType.getReturnType();
        var mappings = CommonUtils.parseMapping(method);
        var resultSetMapper = mappings.getMapping(CassandraTypes.RESULT_SET_MAPPER);
        var rowMapper = mappings.getMapping(CassandraTypes.ROW_MAPPER);
        if (CommonUtils.isFuture(returnType)) {
            var futureParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            if (CommonUtils.isVoid(futureParam)) {
                return Optional.empty();
            }
            var mapperType = ParameterizedTypeName.get(CassandraTypes.ASYNC_RESULT_SET_MAPPER, TypeName.get(futureParam));
            if (rowMapper != null) {
                if (CommonUtils.isList(futureParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.list($L)", CassandraTypes.ASYNC_RESULT_SET_MAPPER, c)));
                } else {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.one($L)", CassandraTypes.ASYNC_RESULT_SET_MAPPER, c)));
                }
            }
            return Optional.of(new DbUtils.Mapper(mapperType, Set.of()));
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return Optional.empty();
        }
        var mapperType = ParameterizedTypeName.get(CassandraTypes.RESULT_SET_MAPPER, TypeName.get(returnType).box());
        if (resultSetMapper != null) {
            return Optional.of(new DbUtils.Mapper(resultSetMapper.mapperClass(), mapperType, resultSetMapper.mapperTags()));
        }
        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.listResultSetMapper($L)", CassandraTypes.RESULT_SET_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.optionalResultSetMapper($L)", CassandraTypes.RESULT_SET_MAPPER, c)));
            } else {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.singleResultSetMapper($L)", CassandraTypes.RESULT_SET_MAPPER, c)));
            }
        }
        return Optional.of(new DbUtils.Mapper(mapperType, Set.of()));
    }

    public void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder) {
        builder.addField(CassandraTypes.CONNECTION_FACTORY, "_connectionFactory", Modifier.PRIVATE, Modifier.FINAL);
        builder.addSuperinterface(CassandraTypes.REPOSITORY);
        builder.addMethod(MethodSpec.methodBuilder("getCassandraConnectionFactory")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(CassandraTypes.CONNECTION_FACTORY)
            .addCode("return this._connectionFactory;")
            .build());

        var executorTag = DbUtils.getTag(repositoryElement);
        if (executorTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(CassandraTypes.CONNECTION_FACTORY, "_connectionFactory").addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", executorTag).build()).build());
        } else {
            constructorBuilder.addParameter(CassandraTypes.CONNECTION_FACTORY, "_connectionFactory");
        }
        constructorBuilder.addStatement("this._connectionFactory = _connectionFactory");
    }
}
