package ru.tinkoff.grpc.client.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.tinkoff.grpc.client.annotation.processor.GrpcClassNames.*;

public final class GrpcClientExtension implements KoraExtension {
    private final ProcessingEnvironment env;
    private final TypeMirror stubErasure;

    public GrpcClientExtension(ProcessingEnvironment env, TypeMirror stubErasure) {
        this.env = env;
        this.stubErasure = Objects.requireNonNull(stubErasure);
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, @Nullable String tag) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var dt = (DeclaredType) typeMirror;
        var te = (TypeElement) dt.asElement();
        var className = ClassName.get(te);
        if (env.getTypeUtils().isAssignable(typeMirror, stubErasure)) {
            return getStubGenerator(typeMirror, tag);
        }
        if (className.equals(channel) && tag != null) {
            return getChannel(typeMirror, tag);
        }
        if (className.equals(grpcClientConfig) && tag != null) {
            return getConfig(typeMirror, tag);
        }

        return null;
    }

    private KoraExtensionDependencyGenerator getConfig(TypeMirror typeMirror, String tag) {
        var grpcServiceClassName = ClassName.bestGuess(tag);

        var clientConfigTypeElement = env.getElementUtils().getTypeElement(grpcClientConfig.canonicalName());
        var factoryMethod = findStaticMethod(clientConfigTypeElement, "defaultConfig");
        var parameterTags = new ArrayList<String>(factoryMethod.getParameters().size() - 1);
        var parameterTypes = new ArrayList<TypeMirror>(factoryMethod.getParameters().size() - 1);
        for (int i = 0; i < factoryMethod.getParameters().size() - 1; i++) {
            var parameter = factoryMethod.getParameters().get(i);
            parameterTypes.add(parameter.asType());
            parameterTags.add(null);
        }

        return () -> new ExtensionResult.CodeBlockResult(
            factoryMethod,
            params -> CodeBlock.of("$T.defaultConfig($L, $T.SERVICE_NAME)", grpcClientConfig, params, grpcServiceClassName),
            typeMirror,
            tag,
            parameterTypes,
            parameterTags
        );
    }

    private KoraExtensionDependencyGenerator getChannel(TypeMirror typeMirror, String tag) {
        var grpcServiceTypeElement = env.getElementUtils().getTypeElement(tag);
        var grpcServiceClassName = ClassName.get(grpcServiceTypeElement);
        var managedChannelTypeElement = env.getElementUtils().getTypeElement(managedChannelLifecycle.canonicalName());
        var managedChannelTypeMirror = managedChannelTypeElement.asType();
        var managedChannelConstructor = CommonUtils.findConstructors(managedChannelTypeElement, m -> m.contains(Modifier.PUBLIC)).get(0);
        var parameterTags = new ArrayList<String>(managedChannelConstructor.getParameters().size() - 1);
        var parameterTypes = new ArrayList<TypeMirror>(managedChannelConstructor.getParameters().size() - 1);
        for (int i = 0; i < managedChannelConstructor.getParameters().size() - 1; i++) {
            var parameter = managedChannelConstructor.getParameters().get(i);
            parameterTags.add(i < 4 ? tag : null);
            parameterTypes.add(parameter.asType());
        }
        return () -> new ExtensionResult.CodeBlockResult(
            managedChannelConstructor,
            params -> CodeBlock.of("new $T($L, $T.getServiceDescriptor())", managedChannelLifecycle, params, grpcServiceClassName),
            managedChannelTypeMirror,
            tag,
            parameterTypes,
            parameterTags
        );
    }

    @Nullable
    private KoraExtensionDependencyGenerator getStubGenerator(TypeMirror typeMirror, String tag) {
        if (tag != null) {
            return null;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        var dtm = (DeclaredType) typeMirror;
        var typeElement = dtm.asElement();
        var apiTypeElement = typeElement.getEnclosingElement();
        if (apiTypeElement.getKind() != ElementKind.CLASS) {
            return null;
        }
        var apiClassName = ClassName.get((TypeElement) apiTypeElement);
        if (AnnotationUtils.findAnnotation(apiTypeElement, grpcGenerated) == null) {
            return null;
        }
        var typeName = typeMirror.toString();
        final ExecutableElement sourceElement;
        if (typeName.endsWith("BlockingStub")) {
            sourceElement = this.findStaticMethod(apiTypeElement, "newBlockingStub");
        } else if (typeName.endsWith("FutureStub")) {
            sourceElement = this.findStaticMethod(apiTypeElement, "newFutureStub");
        } else {
            sourceElement = this.findStaticMethod(apiTypeElement, "newStub");
        }
        var channelType = sourceElement.getParameters().get(0).asType();

        return () -> new ExtensionResult.CodeBlockResult(
            sourceElement,
            params -> CodeBlock.of("$T.$N($L)", apiClassName, sourceElement.getSimpleName(), params),
            typeMirror,
            tag,
            List.of(channelType),
            List.of(apiClassName.canonicalName())
        );
    }

    private ExecutableElement findStaticMethod(Element type, String methodName) {
        for (var enclosedElement : type.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (!enclosedElement.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (enclosedElement.getSimpleName().contentEquals(methodName)) {
                return (ExecutableElement) enclosedElement;
            }
        }
        throw new IllegalStateException();
    }
}
