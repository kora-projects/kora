package ru.tinkoff.kora.kora.app.annotation.processor.app;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.Optional;

@KoraApp
public interface AppWithExtension {

    // factory: generic component, accepts its genetic TypeRef as arguments

    default Class1 test1(Interface1 class1) {
        return new Class1() {};
    }

    default Class2 test2() {
        return new Class2() {};
    }

    interface Interface1 extends MockLifecycle {}

    class Class1 implements MockLifecycle {}

    class Class2 {}

    class TestExtensionExtensionFactory implements ExtensionFactory {

        @Override
        public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new TestExtension(processingEnvironment));
        }
    }

    class TestExtension implements KoraExtension {
        private final TypeMirror interfaceType;
        private final Types types;
        private final TypeElement interfaceElement;
        private final Elements elements;
        private final ProcessingEnvironment processingEnvironment;

        public TestExtension(ProcessingEnvironment processingEnvironment) {
            this.processingEnvironment = processingEnvironment;
            this.interfaceElement = processingEnvironment.getElementUtils().getTypeElement(Interface1.class.getCanonicalName());
            this.interfaceType = processingEnvironment.getElementUtils().getTypeElement(Interface1.class.getCanonicalName()).asType();
            this.types = processingEnvironment.getTypeUtils();
            this.elements = processingEnvironment.getElementUtils();
        }

        @Override
        @Nullable
        public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror) {
            if (this.types.isSameType(typeMirror, this.interfaceType)) {
                return this::generateDependency;
            }
            return null;
        }

        public ExtensionResult generateDependency() {
            var packageName = this.elements.getPackageOf(this.interfaceElement).getQualifiedName().toString();
            var typeName = "AppWithExtensionInterface1Impl";
            var maybeGenerated = this.elements.getTypeElement(packageName + "." + typeName);
            if (maybeGenerated != null) {
                var constructor = maybeGenerated.getEnclosedElements().stream()
                    .filter(c -> c.getKind() == ElementKind.CONSTRUCTOR)
                    .map(ExecutableElement.class::cast)
                    .findFirst()
                    .get();

                return ExtensionResult.fromExecutable(constructor);
            }

            var type = TypeSpec.classBuilder(typeName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(Interface1.class)
                .addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(Class2.class, "string")
                    .build())
                .build();

            var javaFile = JavaFile.builder(packageName, type).build();

            try {
                javaFile.writeTo(this.processingEnvironment.getFiler());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return ExtensionResult.nextRound();
        }
    }
}
