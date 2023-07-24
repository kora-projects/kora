package ru.tinkoff.kora.kora.app.annotation.processor.app;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionFactory;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.AbstractProcessor;
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
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@KoraApp
public interface AppWithProcessorExtension {
    @interface TestAnnotation {}

    @Root
    default Object object(Interface1 interface1) {
        return new Object();
    }


    @TestAnnotation
    interface Interface1 {}

    class TestExtensionExtensionFactory implements ExtensionFactory {

        @Override
        public Optional<KoraExtension> create(ProcessingEnvironment processingEnvironment) {
            return Optional.of(new TestExtension(processingEnvironment));
        }
    }

    class TestProcessor extends AbstractProcessor {

        private TypeElement interfaceElement;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of(TestAnnotation.class.getCanonicalName());
        }

        @Override
        public synchronized void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
            this.interfaceElement = processingEnv.getElementUtils().getTypeElement(Interface1.class.getCanonicalName());
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            var elements = annotations.stream()
                .filter(a -> a.getQualifiedName().contentEquals(TestAnnotation.class.getCanonicalName()))
                .map(roundEnv::getElementsAnnotatedWith)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
            for (var element : elements) {
                var packageName = this.processingEnv.getElementUtils().getPackageOf(this.interfaceElement).getQualifiedName().toString();
                var typeName = "AppWithExtensionInterface1Impl";

                var type = TypeSpec.classBuilder(typeName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addSuperinterface(Interface1.class)
                    .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                    .build();

                var javaFile = JavaFile.builder(packageName, type).build();

                try {
                    javaFile.writeTo(this.processingEnv.getFiler());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return !elements.isEmpty();
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
        public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tags) {
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

            // annotation processor will handle it
            return ExtensionResult.nextRound();
        }
    }

}
