package ru.tinkoff.kora.config.annotation.processor.processor;

import com.squareup.javapoet.*;
import com.typesafe.config.Config;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.config.common.ConfigSource;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public class ConfigSourceAnnotationProcessor extends AbstractKoraProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ConfigSource.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var config : roundEnv.getElementsAnnotatedWith(ConfigSource.class)) {
            var typeBuilder = TypeSpec.interfaceBuilder(config.getSimpleName().toString() + "Module");
            var path = config.getAnnotation(ConfigSource.class).value();
            var name = new StringBuilder(config.getSimpleName().toString());
            var parent = config.getEnclosingElement();
            while (parent.getKind() != ElementKind.PACKAGE) {
                name.insert(0, parent.getSimpleName());
                parent = parent.getEnclosingElement();
            }
            name.replace(0, 1, String.valueOf(Character.toLowerCase(name.charAt(0))));

            var method = MethodSpec.methodBuilder(name.toString())
                .returns(TypeName.get(config.asType()))
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addParameter(TypeName.get(Config.class), "config")
                .addParameter(ParameterizedTypeName.get(ClassName.get(ConfigValueExtractor.class), TypeName.get(config.asType())), "extractor")
                .addStatement("var configValue = config.getValue($S)", path)
                .addStatement("return extractor.extract(configValue)");

            var type = typeBuilder.addMethod(method.build())
                .addAnnotation(Module.class)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                    .addMember("value", CodeBlock.of("$S", ConfigSourceAnnotationProcessor.class.getCanonicalName()))
                    .build())
                .addModifiers(Modifier.PUBLIC)
                .addOriginatingElement(config)
                .build();

            var packageElement = this.elements.getPackageOf(config);

            var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), type).build();

            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        }

        return false;
    }
}
