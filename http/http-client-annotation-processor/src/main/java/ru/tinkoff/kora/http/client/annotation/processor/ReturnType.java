package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.ComparableTypeMirror;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface ReturnType {
    default TypeName responseMapperType() {
        var publisherType = this.publisherType();
        var publisherParameter = TypeName.get(this.publisherParameter());
        var publisherFullType = ParameterizedTypeName.get(publisherType, publisherParameter);

        return ParameterizedTypeName.get(
            ClassName.get(HttpClientResponseMapper.class),
            publisherParameter,
            publisherFullType
        );
    }

    default ClassName publisherType() {
        return ClassName.get(Mono.class);
    }

    TypeMirror publisherParameter();

    record MonoReturnType(SimpleReturnType simpleReturnType) implements ReturnType {
        @Override
        public TypeMirror publisherParameter() {
            return this.simpleReturnType().typeMirror();
        }
    }

    record FluxReturnType(SimpleReturnType simpleReturnType) implements ReturnType {
        @Override
        public TypeMirror publisherParameter() {
            return this.simpleReturnType().typeMirror();
        }

        @Override
        public ClassName publisherType() {
            return ClassName.get(Flux.class);
        }
    }

    record SimpleReturnType(TypeMirror typeMirror) implements ReturnType {
        @Override
        public TypeMirror publisherParameter() {
            return this.typeMirror();
        }
    }

    record VoidReturnType(TypeMirror voidType) implements ReturnType {
        @Override
        public TypeMirror publisherParameter() {
            return this.voidType();
        }
    }

    class ReturnTypeParser {
        private final Elements elements;
        private final Types types;
        private final ComparableTypeMirror voidType;
        private final ComparableTypeMirror monoType;
        private final ComparableTypeMirror fluxType;
        private final ComparableTypeMirror objectType;
        private final TypeMirror boxedVoidType;
        private final ProcessingEnvironment env;

        public ReturnTypeParser(ProcessingEnvironment processingEnv, Elements elements, Types types) {
            this.env = processingEnv;
            this.elements = elements;
            this.types = types;
            this.boxedVoidType = this.elements.getTypeElement(Void.class.getCanonicalName()).asType();
            this.voidType = new ComparableTypeMirror(this.types, this.types.getNoType(TypeKind.VOID));
            this.monoType = new ComparableTypeMirror(this.types, this.types.erasure(this.elements.getTypeElement(Mono.class.getCanonicalName()).asType()));
            this.fluxType = new ComparableTypeMirror(this.types, this.types.erasure(this.elements.getTypeElement(Flux.class.getCanonicalName()).asType()));
            this.objectType = new ComparableTypeMirror(this.types, this.types.erasure(this.elements.getTypeElement(Object.class.getCanonicalName()).asType()));
        }

        public ReturnType parseReturnType(ExecutableElement method) {
            var methodReturnType = method.getReturnType();
            if (this.voidType.equals(methodReturnType)) {
                return new VoidReturnType(this.boxedVoidType);
            }
            if (this.objectType.equals(methodReturnType)) {
                if (method.getParameters().isEmpty()) {
                    return new SimpleReturnType(methodReturnType);
                }
                return new SimpleReturnType(methodReturnType);
            }
            var methodReturnTypeErasure = this.types.erasure(methodReturnType);
            if (this.monoType.equals(methodReturnTypeErasure)) {
                var mono = (DeclaredType) methodReturnType;
                var simpleType = new SimpleReturnType(mono.getTypeArguments().get(0));
                return new MonoReturnType(simpleType);
            }
            if (this.fluxType.equals(methodReturnTypeErasure)) {
                var flux = (DeclaredType) methodReturnType;
                var simpleType = new SimpleReturnType(flux.getTypeArguments().get(0));
                return new FluxReturnType(simpleType);
            }
            return new SimpleReturnType(methodReturnType);
        }
    }
}
