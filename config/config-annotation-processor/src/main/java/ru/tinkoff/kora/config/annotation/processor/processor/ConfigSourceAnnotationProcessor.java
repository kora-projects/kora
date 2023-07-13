package ru.tinkoff.kora.config.annotation.processor.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.config.annotation.processor.ConfigClassNames;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Optional;
import java.util.Set;

public class ConfigSourceAnnotationProcessor extends AbstractKoraProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ConfigClassNames.configSourceAnnotation.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var config : roundEnv.getElementsAnnotatedWith(this.elements.getTypeElement(ConfigClassNames.configSourceAnnotation.canonicalName()))) {
            var typeBuilder = TypeSpec.interfaceBuilder(config.getSimpleName().toString() + "Module");
            var path = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(
                AnnotationUtils.findAnnotation(config, ConfigClassNames.configSourceAnnotation),
                "value"
            );
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
                .addParameter(ConfigClassNames.config, "config")
                .addParameter(ParameterizedTypeName.get(ConfigClassNames.configValueExtractor, TypeName.get(config.asType())), "extractor")
                .addStatement("var configValue = config.get($S)", path)
                .addStatement("return $T.ofNullable(extractor.extract(configValue)).orElseThrow(() -> $T.missingValueAfterParse(configValue))", Optional.class, CommonClassNames.configValueExtractionException);

            var type = typeBuilder.addMethod(method.build())
                .addAnnotation(CommonClassNames.module)
                .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated)
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
