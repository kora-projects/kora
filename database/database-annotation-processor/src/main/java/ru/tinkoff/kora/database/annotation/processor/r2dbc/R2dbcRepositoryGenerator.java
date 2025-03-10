package ru.tinkoff.kora.database.annotation.processor.r2dbc;

import com.palantir.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.common.Tag;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class R2dbcRepositoryGenerator implements RepositoryGenerator {
    private final TypeMirror repositoryInterface;
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public R2dbcRepositoryGenerator(ProcessingEnvironment processingEnv) {
        var repository = processingEnv.getElementUtils().getTypeElement(R2dbcTypes.R2DBC_REPOSITORY.canonicalName());
        if (repository == null) {
            this.repositoryInterface = null;
        } else {
            this.repositoryInterface = repository.asType();
        }
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
    }

    @Nullable
    @Override
    public TypeMirror repositoryInterface() {
        return this.repositoryInterface;
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
            var parameters = QueryParameterParser.parse(this.types, R2dbcTypes.CONNECTION, R2dbcTypes.PARAMETER_COLUMN_MAPPER, method, methodType);
            var queryAnnotation = AnnotationUtils.findAnnotation(method, DbUtils.QUERY_ANNOTATION);
            var queryString = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(queryAnnotation, "value");
            var query = QueryWithParameters.parse(filer, types, queryString, parameters, repositoryType, method);
            var resultMapper = this.parseResultMapper(method, parameters, methodType)
                .map(rm -> DbUtils.addMapper(resultMappers, rm))
                .orElse(null);
            DbUtils.addMappers(parameterMappers, DbUtils.parseParameterMappers(
                parameters,
                query,
                tn -> R2dbcNativeTypes.findAndBox(tn) != null,
                R2dbcTypes.PARAMETER_COLUMN_MAPPER
            ));
            var methodSpec = this.generate(type, methodCounter, method, methodType, query, parameters, resultMapper, parameterMappers);
            type.addMethod(methodSpec);
            methodCounter++;
        }
        return type.addMethod(constructor.build()).build();
    }

    private MethodSpec generate(TypeSpec.Builder type, int methodNumber, ExecutableElement method, ExecutableType methodType, QueryWithParameters query, List<QueryParameter> parameters, @Nullable String resultMapperName, FieldFactory parameterMappers) {
        final boolean generatedKeys = AnnotationUtils.isAnnotationPresent(method, DbUtils.ID_ANNOTATION);
        var sql = query.rawQuery();
        for (var parameter : query.parameters().stream().sorted(Comparator.<QueryWithParameters.QueryParameter>comparingInt(s -> s.sqlParameterName().length()).reversed()).toList()) {
            for (var sqlIndex : parameter.sqlIndexes()) {
                sql = sql.replace(":" + parameter.sqlParameterName(), "$" + (sqlIndex + 1));
            }
        }
        var connectionParameter = parameters.stream().filter(QueryParameter.ConnectionParameter.class::isInstance).findFirst().orElse(null);

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
        var isFlux = CommonUtils.isFlux(methodType.getReturnType());
        var isMono = CommonUtils.isMono(methodType.getReturnType());

        var returnType = isMono || isFlux
            ? ((DeclaredType) method.getReturnType()).getTypeArguments().get(0)
            : method.getReturnType();

        b.addCode("var _result = ");
        b.addCode("$T.deferContextual(_reactorCtx -> {$>\n", isFlux ? CommonClassNames.flux : CommonClassNames.mono);
        b.addStatement("var _ctxCurrent = $T.current(_reactorCtx)", CommonClassNames.contextReactor);
        b.addStatement("var _ctxFork = _ctxCurrent.fork()");
        b.addStatement("_ctxFork.inject()");
        b.addCode("var _telemetry = this._connectionFactory.telemetry().createContext(_ctxFork, _query);\n");
        var connectionName = "_con";
        if (connectionParameter == null) {
            b.addCode("return this._connectionFactory.withConnection$L(_con -> {$>\n", isFlux ? "Flux" : "");
        } else {
            connectionName = connectionParameter.name();
        }
        b.addCode("var _stmt = $N.createStatement(_query.sql());\n", connectionName);

        R2dbcStatementSetterGenerator.generate(b, method, query, parameters, batchParam, parameterMappers);

        if (generatedKeys) {
            b.addCode("var _flux = $T.<$T>from(_stmt.returnGeneratedValues().execute());\n", CommonClassNames.flux, R2dbcTypes.RESULT);
        } else {
            b.addCode("var _flux = $T.<$T>from(_stmt.execute());\n", CommonClassNames.flux, R2dbcTypes.RESULT);
        }

        var mappings = CommonUtils.parseMapping(method);
        var resultFluxMapper = mappings.getMapping(R2dbcTypes.RESULT_FLUX_MAPPER);
        var rowMapper = mappings.getMapping(R2dbcTypes.ROW_MAPPER);
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            b.addCode("return _flux.flatMap($T::getRowsUpdated).reduce(0L, Long::sum).map($T::new)", R2dbcTypes.RESULT, DbUtils.UPDATE_COUNT);
        } else if (resultFluxMapper != null) {
            Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());
            b.addCode("return $L.apply(_flux)\n", resultMapperName);
        } else if (rowMapper != null || !CommonUtils.isVoid(returnType)) {
            Objects.requireNonNull(resultMapperName, () -> "Illegal State occurred when expected to get result mapper, but got null in " + method.getEnclosingElement().getSimpleName() + "#" + method.getSimpleName());
            b.addCode("return $L.apply(_flux)\n", resultMapperName);
        } else {
            b.addCode("return _flux.flatMap($T::getRowsUpdated).then()", R2dbcTypes.RESULT);
        }
        b.addCode("""
            .doOnEach(_s -> {
              if (_s.isOnComplete()) {
                _telemetry.close(null);
                _ctxCurrent.inject();
              } else if (_s.isOnError()) {
                _telemetry.close(_s.getThrowable());
                _ctxCurrent.inject();
              }
            });""");
        if (connectionParameter == null) {
            b.addCode("\n$<\n});\n");
        }
        b.addCode("\n$<\n});\n");
        if (isMono || isFlux) {
            b.addCode("return _result;\n");
        } else if (returnType.getKind() == TypeKind.VOID) {
            b.addCode("_result.then().block();");
        } else {
            b.addCode("return _result.block();");
        }
        return b.build();
    }

    private Optional<DbUtils.Mapper> parseResultMapper(ExecutableElement method, List<QueryParameter> parameters, ExecutableType methodType) {
        var returnType = methodType.getReturnType();
        final boolean isFlux = CommonUtils.isFlux(returnType);
        final boolean isMono = CommonUtils.isMono(returnType);
        final boolean generatedKeys = AnnotationUtils.isAnnotationPresent(method, DbUtils.ID_ANNOTATION);
        for (var parameter : parameters) {
            if (parameter instanceof QueryParameter.BatchParameter) {
                final TypeMirror realReturnType = (isMono || isFlux)
                    ? Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0))
                    : method.getReturnType();

                if (CommonUtils.isVoid(realReturnType)) {
                    return Optional.empty();
                } else if (DbUtils.UPDATE_COUNT.canonicalName().equals(realReturnType.toString())) {
                    return Optional.empty();
                } else if (!generatedKeys) {
                    throw new ProcessingErrorException("@Batch method can't return arbitrary values, it can only return: void/UpdateCount or database-generated @Id", method);
                }
            }
        }

        var mappings = CommonUtils.parseMapping(method);
        var resultFluxMapper = mappings.getMapping(R2dbcTypes.RESULT_FLUX_MAPPER);
        var rowMapper = mappings.getMapping(R2dbcTypes.ROW_MAPPER);
        if (resultFluxMapper == null && rowMapper == null) {
            if (CommonUtils.isVoid(returnType)) {
                return Optional.empty();
            }
        }

        if (isMono || isFlux) {
            var publisherParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            if (CommonUtils.isVoid(publisherParam)) {
                return Optional.empty();
            }
            if (publisherParam.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                return Optional.empty();
            }

            var mapperType = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, TypeName.get(publisherParam), TypeName.get(returnType));
            if (resultFluxMapper != null) {
                return Optional.of(new DbUtils.Mapper(resultFluxMapper.mapperClass(), mapperType, resultFluxMapper.mapperTags()));
            }
            if (rowMapper != null) {
                if (isFlux) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.flux($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                } else if (CommonUtils.isList(publisherParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.monoList($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                } else if (CommonUtils.isOptional(publisherParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.monoOptional($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                } else {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.mono($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                }
            }
            return Optional.of(new DbUtils.Mapper(mapperType, mappings.mapperTags()));
        }

        var monoParam = TypeName.get(returnType).box();
        var mapperType = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, monoParam, ParameterizedTypeName.get(CommonClassNames.mono, monoParam));
        if (resultFluxMapper != null) {
            return Optional.of(new DbUtils.Mapper(resultFluxMapper.mapperClass(), mapperType, resultFluxMapper.mapperTags()));
        }

        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.monoList($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.monoOptional($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            } else {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, rowMapper.mapperTags(), c -> CodeBlock.of("$T.mono($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            }
        }
        if (CommonUtils.isVoid(returnType)) {
            return Optional.empty();
        }
        if (returnType.toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
            return Optional.empty();
        }

        return Optional.of(new DbUtils.Mapper(mapperType, mappings.mapperTags()));
    }

    private void enrichWithExecutor(TypeElement repositoryElement, TypeSpec.Builder builder, MethodSpec.Builder constructorBuilder) {
        builder.addField(R2dbcTypes.CONNECTION_FACTORY, "_connectionFactory", Modifier.PRIVATE, Modifier.FINAL);
        builder.addSuperinterface(R2dbcTypes.R2DBC_REPOSITORY);
        builder.addMethod(MethodSpec.methodBuilder("getR2dbcConnectionFactory")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Override.class)
            .returns(R2dbcTypes.CONNECTION_FACTORY)
            .addCode("return this._connectionFactory;")
            .build());

        var executorTag = DbUtils.getTag(repositoryElement);
        if (executorTag != null) {
            constructorBuilder.addParameter(ParameterSpec.builder(R2dbcTypes.CONNECTION_FACTORY, "_connectionFactory").addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", executorTag).build()).build());
        } else {
            constructorBuilder.addParameter(R2dbcTypes.CONNECTION_FACTORY, "_connectionFactory");
        }
        constructorBuilder.addStatement("this._connectionFactory = _connectionFactory");
    }
}
