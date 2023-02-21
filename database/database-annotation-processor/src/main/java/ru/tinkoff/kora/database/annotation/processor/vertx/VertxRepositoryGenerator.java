package ru.tinkoff.kora.database.annotation.processor.vertx;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.Visitors;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.DbUtils;
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
import java.util.*;
import java.util.concurrent.CompletionStage;

public final class VertxRepositoryGenerator implements RepositoryGenerator {
    private final TypeMirror repositoryInterface;
    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final DeclaredType completionStageType;

    public VertxRepositoryGenerator(ProcessingEnvironment processingEnv) {
        var repository = processingEnv.getElementUtils().getTypeElement(VertxTypes.REPOSITORY.canonicalName());
        if (repository == null) {
            this.repositoryInterface = null;
        } else {
            this.repositoryInterface = repository.asType();
        }
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
        this.completionStageType = this.types.getDeclaredType(
            this.elements.getTypeElement(CompletionStage.class.getCanonicalName()),
            this.types.getWildcardType(null, null)
        );
    }

    @Override
    @Nullable
    public TypeMirror repositoryInterface() {
        return this.repositoryInterface;
    }

    @Override
    public TypeSpec generate(TypeElement repositoryElement, TypeSpec.Builder type, MethodSpec.Builder constructor) {
        var repositoryType = (DeclaredType) repositoryElement.asType();
        var queryMethods = DbUtils.findQueryMethods(this.types, this.elements, repositoryElement);
        this.enrichWithExecutor(repositoryElement, type, constructor, queryMethods);
        for (var method : queryMethods) {
            var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
            var parameters = QueryParameterParser.parse(this.types, List.of(VertxTypes.CONNECTION, VertxTypes.SQL_CLIENT), method, methodType);
            var queryAnnotation = CommonUtils.findDirectAnnotation(method, DbUtils.QUERY_ANNOTATION);
            var queryString = CommonUtils.parseAnnotationValueWithoutDefault(queryAnnotation, "value").toString();
            var query = QueryWithParameters.parse(filer, queryString, parameters);
            this.parseResultMappers(method, parameters, methodType)
                .map(List::of)
                .ifPresent(resultMapper -> DbUtils.addMappers(this.types, type, constructor, resultMapper));
            DbUtils.addMappers(this.types, type, constructor, DbUtils.parseParameterMappers(
                method,
                parameters,
                query,
                tn -> VertxNativeTypes.find(tn) != null,
                VertxTypes.PARAMETER_COLUMN_MAPPER
            ));
            var methodSpec = this.generate(method, methodType, query, parameters);
            type.addMethod(methodSpec);
        }
        return type.addMethod(constructor.build()).build();
    }

    private MethodSpec generate(ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters) {
        var sql = query.rawQuery();
        {
            var params = new ArrayList<Map.Entry<QueryWithParameters.QueryParameter, Integer>>(query.parameters().size());
            for (int i = 0; i < query.parameters().size(); i++) {
                var parameter = query.parameters().get(i);
                params.add(Map.entry(parameter, i));
            }
            for (var parameter : params.stream().sorted(Comparator.<Map.Entry<QueryWithParameters.QueryParameter, Integer>, Integer>comparing(p -> p.getKey().sqlParameterName().length()).reversed()).toList()) {
                sql = sql.replace(":" + parameter.getKey().sqlParameterName(), "$" + (parameter.getValue() + 1));
            }
        }
        var b = DbUtils.queryMethodBuilder(method, methodType);
        b.addStatement(CodeBlock.of("var _query = new $T(\n  $S,\n  $S\n)", DbUtils.QUERY_CONTEXT, query.rawQuery(), sql));
        var batchParam = parameters.stream().filter(QueryParameter.BatchParameter.class::isInstance).findFirst().orElse(null);
        var connectionParam = parameters.stream().filter(QueryParameter.ConnectionParameter.class::isInstance).findFirst().orElse(null);
        var returnType = methodType.getReturnType();
        var isFlux = CommonUtils.isFlux(returnType);
        var isMono = CommonUtils.isMono(returnType);
        var isCompletionStage = this.isCompletionStage(returnType);
        var isVoid = isVoid(returnType);

        ParametersToTupleBuilder.generate(b, query, method, parameters, batchParam);
        CodeBlock resultMapper;
        if (isVoid) {
            resultMapper = CodeBlock.of("_rs -> null");
        } else if ((isMono || isCompletionStage) && ((DeclaredType) returnType).getTypeArguments().get(0).toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            resultMapper = CodeBlock.of("$T::extractUpdateCount", VertxTypes.ROW_SET_MAPPER);
        } else if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            resultMapper = CodeBlock.of("$T::extractUpdateCount", VertxTypes.ROW_SET_MAPPER);
        } else {
            resultMapper = CodeBlock.of("$L", DbUtils.resultMapperName(method));
        }
        if (returnType.getKind() != TypeKind.VOID) {
            b.addCode("return ");
        }
        if (batchParam != null) {
            if (isCompletionStage) {
                if (connectionParam == null) {
                    b.addCode("$T.batchCompletionStage(this._connectionFactory, _query, _batchParams)\n", VertxTypes.REPOSITORY_HELPER);
                } else {
                    b.addCode("$T.batchCompletionStage($N, this._connectionFactory.telemetry(), _query, _batchParams)\n", VertxTypes.REPOSITORY_HELPER, connectionParam.name());
                }
                if (isVoid) {
                    b.addCode("  .thenApply(v -> (Void) null)\n");
                }
            } else {
                if (connectionParam == null) {
                    b.addCode("$T.batchMono(this._connectionFactory, _query, _batchParams)\n", VertxTypes.REPOSITORY_HELPER);
                } else {
                    b.addCode("$T.batchMono($N, this._connectionFactory.telemetry(), _query, _batchParams)\n", VertxTypes.REPOSITORY_HELPER, connectionParam.name());
                }
                if (isVoid) {
                    b.addCode("  .then()\n");
                }
            }
        } else if (isFlux) {
            if (connectionParam == null) {
                b.addCode("$T.flux(this._connectionFactory, _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, DbUtils.resultMapperName(method));
            } else {
                b.addCode("$T.flux($N, this._connectionFactory.telemetry(), _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, connectionParam.name(), DbUtils.resultMapperName(method));
            }
        } else if (isMono) {
            if (connectionParam == null) {
                b.addCode("$T.mono(this._connectionFactory, _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, resultMapper);
            } else {
                b.addCode("$T.mono($N, this._connectionFactory.telemetry(), _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, connectionParam.name(), resultMapper);
            }
        } else {
            if (connectionParam == null) {
                b.addCode("$T.completionStage(this._connectionFactory, _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, resultMapper);
            } else {
                b.addCode("$T.completionStage($N, this._connectionFactory.telemetry(), _query, _tuple, $L)\n", VertxTypes.REPOSITORY_HELPER, connectionParam.name(), resultMapper);
            }
        }
        if (isFlux) {
            b.addCode(";\n");
        } else if (isMono) {
            b.addCode(";\n");
        } else if (isCompletionStage) {
            b.addCode(";\n");
        } else {
            b.addCode("  .toCompletableFuture().join();\n");
        }
        return b.build();
    }

    private Optional<DbUtils.Mapper> parseResultMappers(ExecutableElement method, List<QueryParameter> parameters, ExecutableType methodType) {
        for (var parameter : parameters) {
            if (parameter instanceof QueryParameter.BatchParameter) {
                return Optional.empty();
            }
        }
        var returnType = methodType.getReturnType();
        if (isVoid(returnType)) {
            return Optional.empty();
        }
        var mapperName = DbUtils.resultMapperName(method);
        var mappings = CommonUtils.parseMapping(method);
        var rowSetMapper = mappings.getMapping(VertxTypes.ROW_SET_MAPPER);
        var rowMapper = mappings.getMapping(VertxTypes.ROW_MAPPER);
        if (CommonUtils.isFlux(returnType)) {
            var fluxParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            var mapperType = ParameterizedTypeName.get(VertxTypes.ROW_MAPPER, TypeName.get(fluxParam));
            if (rowMapper != null) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName));
            }
            return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
        }
        if (CommonUtils.isMono(returnType) || this.isCompletionStage(returnType)) {
            var monoParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            var mapperType = ParameterizedTypeName.get(VertxTypes.ROW_SET_MAPPER, TypeName.get(monoParam));
            if (rowSetMapper != null) {
                return Optional.of(new DbUtils.Mapper(rowSetMapper.mapperClass(), mapperType, mapperName));
            }
            if (rowMapper != null) {
                if (CommonUtils.isList(monoParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.listRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
                } else if (CommonUtils.isOptional(monoParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.optionalRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
                } else {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.singleRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
                }
            }
            if (monoParam.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                return Optional.empty();
            }
            return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
        }
        var mapperType = ParameterizedTypeName.get(VertxTypes.ROW_SET_MAPPER, TypeName.get(returnType).box());
        if (rowSetMapper != null) {
            return Optional.of(new DbUtils.Mapper(rowSetMapper.mapperClass(), mapperType, mapperName));
        }
        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.listRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.optionalRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
            } else {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.singleRowSetMapper($L)", VertxTypes.ROW_SET_MAPPER, c)));
            }
        }
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            return Optional.empty();
        }
        return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
    }

    public void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder, List<ExecutableElement> queryMethods) {
        builder.addField(VertxTypes.CONNECTION_FACTORY, "_connectionFactory", Modifier.PRIVATE, Modifier.FINAL);
        builder.addSuperinterface(VertxTypes.REPOSITORY);
        builder.addMethod(MethodSpec.methodBuilder("getVertxConnectionFactory")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(VertxTypes.CONNECTION_FACTORY)
            .addCode("return this._connectionFactory;")
            .build());

        var executorTag = DbUtils.getTag(repositoryElement);
        if (executorTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(VertxTypes.CONNECTION_FACTORY, "_connectionFactory").addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", executorTag).build()).build());
        } else {
            constructorBuilder.addParameter(VertxTypes.CONNECTION_FACTORY, "_connectionFactory");
        }
        constructorBuilder.addStatement("this._connectionFactory = _connectionFactory");
    }

    private boolean isCompletionStage(TypeMirror returnType) {
        return this.types.isAssignable(returnType, this.completionStageType);
    }

    private boolean isVoid(TypeMirror tm) {
        if (isCompletionStage(tm) || CommonUtils.isMono(tm)) {
            tm = Visitors.visitDeclaredType(tm, dt -> dt.getTypeArguments().get(0));
        }
        return tm.getKind() == TypeKind.NONE || tm.toString().equals("java.lang.Void") || tm.getKind() == TypeKind.VOID;
    }
}
