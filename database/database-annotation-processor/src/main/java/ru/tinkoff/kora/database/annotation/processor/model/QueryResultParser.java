//package ru.tinkoff.kora.database.annotation.processor.model;
//
//import com.squareup.javapoet.TypeName;
//import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
//import ru.tinkoff.kora.database.annotation.processor.RepositoryGenerator;
//
//import javax.annotation.Nullable;
//import javax.lang.model.element.ExecutableElement;
//import javax.lang.model.type.DeclaredType;
//import javax.lang.model.type.ExecutableType;
//import javax.lang.model.type.TypeMirror;
//import javax.lang.model.util.Types;
//import java.util.Objects;
//
//public final class QueryResultParser {
//    private final Types types;
//
//    public QueryResultParser(Types types) {
//        this.types = types;
//    }
//
//    public QueryResult parse(RepositoryGenerator gen, DeclaredType repository, ExecutableElement method) {
//        var mappings = CommonUtils.parseMapping(method);
//        var mapping = gen.resultMapperTypes().stream()
//            .map(mappings::getMapping)
//            .filter(Objects::nonNull)
//            .filter(m -> m.mapperClass() != null)
//            .findFirst()
//            .orElse(null);
//
//        var executableType = (ExecutableType) this.types.asMemberOf(repository, method);
//        var type = executableType.getReturnType();
//
//        if (this.isMono(type)) {
//            var typeParameter = this.typeParameter(type);
//            var monoOf = this.parseType(mapping, typeParameter);
//            return new QueryResult.MonoResult(TypeName.get(type), monoOf);
//        }
//        if (this.isFlux(type)) {
//            var typeParameter = this.typeParameter(type);
//            var fluxOf = this.parseType(mapping, typeParameter);
//            return new QueryResult.FluxResult(TypeName.get(type), fluxOf);
//        }
//        return this.parseType(mapping, type);
//    }
//
//    private QueryResult parseType(@Nullable CommonUtils.MappingData mapping, TypeMirror type) {
//        if (mapping != null) {
//            return new QueryResult.ResultWithMapper(TypeName.get(type), mapping);
//        }
//        return new QueryResult.SimpleResult(TypeName.get(type));
//    }
//
//
//    private boolean isMono(TypeMirror type) {
//        return type.toString().startsWith("reactor.core.publisher.Mono<");
//    }
//
//    private boolean isFlux(TypeMirror type) {
//        return type.toString().startsWith("reactor.core.publisher.Flux<");
//    }
//
//    @Nullable
//    private TypeMirror typeParameter(TypeMirror type) {
//        if (type instanceof DeclaredType dt) {
//            var typeArguments = dt.getTypeArguments();
//            if (typeArguments.size() == 1) {
//                return typeArguments.get(0);
//            } else {
//                return null;
//            }
//        } else {
//            return null;
//        }
//    }
//}
