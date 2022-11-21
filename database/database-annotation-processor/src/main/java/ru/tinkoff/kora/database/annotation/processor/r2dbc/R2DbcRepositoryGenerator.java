package ru.tinkoff.kora.database.annotation.processor.r2dbc;

import com.squareup.javapoet.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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

public final class R2DbcRepositoryGenerator implements RepositoryGenerator {
    private final TypeMirror repositoryInterface;
    private final Types types;
    private final Elements elements;
    private final Filer filer;

    public R2DbcRepositoryGenerator(ProcessingEnvironment processingEnv) {
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
        for (var method : queryMethods) {
            var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
            var parameters = QueryParameterParser.parse(this.types, R2dbcTypes.CONNECTION, method, methodType);
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
                tn -> R2dbcNativeTypes.findAndBox(tn) != null,
                R2dbcTypes.PARAMETER_COLUMN_MAPPER
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
        var returnType = methodType.getReturnType();
        var isFlux = CommonUtils.isFlux(returnType);
        var isMono = CommonUtils.isMono(returnType);
        b.addCode("var _result = ");
        b.addCode("$T.deferContextual(_reactorCtx -> {$>\n", isFlux ? Flux.class : Mono.class);
        b.addCode("var _telemetry = this._connectionFactory.telemetry().createContext(ru.tinkoff.kora.common.Context.Reactor.current(_reactorCtx), _query);\n");
        b.addCode("return this._connectionFactory.withConnection$L(_con -> {$>\n", isFlux ? "Flux" : "");
        b.addCode("var _stmt = _con.createStatement(_query.sql());\n");

        R2dbcStatementSetterGenerator.generate(b, method, query, parameters, batchParam);

        b.addCode("var _flux = $T.<$T>from(_stmt.execute());\n", Flux.class, R2dbcTypes.RESULT);
        b.addCode("return $L.apply(_flux)\n", DbUtils.resultMapperName(method));
        b.addCode("""
              .doOnEach(_s -> {
                if (_s.isOnComplete()) {
                  _telemetry.close(null);
                } else if (_s.isOnError()) {
                  _telemetry.close(_s.getThrowable());
                }
              });
            """);
        b.addCode("\n$<\n});\n");
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

    private Optional<DbUtils.Mapper> parseResultMappers(ExecutableElement method, List<QueryParameter> parameters, ExecutableType methodType) {
        var mapperName = DbUtils.resultMapperName(method);
        for (var parameter : parameters) {
            if (parameter instanceof QueryParameter.BatchParameter) {
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    return Optional.empty();
                }
                var type = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, TypeName.get(Void.class), TypeName.get(methodType.getReturnType()));
                return Optional.of(new DbUtils.Mapper(
                    type, mapperName
                ));
            }
        }
        var returnType = methodType.getReturnType();
        var mappings = CommonUtils.parseMapping(method);
        var resultFluxMapper = mappings.getMapping(R2dbcTypes.RESULT_FLUX_MAPPER);
        var rowMapper = mappings.getMapping(R2dbcTypes.ROW_MAPPER);
        if (CommonUtils.isFlux(returnType)) {
            var fluxParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            var mapperType = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, TypeName.get(fluxParam), TypeName.get(returnType));
            if (rowMapper != null) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.flux($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            }
            return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
        }
        if (CommonUtils.isMono(returnType)) {
            var monoParam = Visitors.visitDeclaredType(returnType, dt -> dt.getTypeArguments().get(0));
            var mapperType = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, TypeName.get(monoParam), TypeName.get(returnType));
            if (rowMapper != null) {
                if (CommonUtils.isList(monoParam)) {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.monoList($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                } else {
                    return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.mono($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
                }
            }
            return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return Optional.empty();
        }
        var monoParam = TypeName.get(returnType).box();
        var mapperType = ParameterizedTypeName.get(R2dbcTypes.RESULT_FLUX_MAPPER, monoParam, ParameterizedTypeName.get(ClassName.get(Mono.class), monoParam));
        if (resultFluxMapper != null) {
            return Optional.of(new DbUtils.Mapper(resultFluxMapper.mapperClass(), mapperType, mapperName));
        }
        if (rowMapper != null) {
            if (CommonUtils.isList(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.listResultFluxMapper($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            } else if (CommonUtils.isOptional(returnType)) {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.optionalResultSetMapper($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            } else {
                return Optional.of(new DbUtils.Mapper(rowMapper.mapperClass(), mapperType, mapperName, c -> CodeBlock.of("$T.singleResultSetMapper($L)", R2dbcTypes.RESULT_FLUX_MAPPER, c)));
            }
        }
        return Optional.of(new DbUtils.Mapper(mapperType, mapperName));
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
