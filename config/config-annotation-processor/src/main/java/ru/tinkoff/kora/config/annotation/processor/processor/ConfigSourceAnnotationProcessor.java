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
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigSourceAnnotationProcessor extends AbstractKoraProcessor {
    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(ConfigClassNames.configSourceAnnotation);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotated : annotatedElements.getOrDefault(ConfigClassNames.configSourceAnnotation, List.of())) {
            var config = annotated.element();
            var typeBuilder = TypeSpec.interfaceBuilder(config.getSimpleName().toString() + "Module")
                .addOriginatingElement(config)
                .addAnnotation(AnnotationUtils.generated(ConfigSourceAnnotationProcessor.class));
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
                .addStatement("var parsed = extractor.extract(configValue)")
                .beginControlFlow("if (parsed == null)")
                .addStatement("throw $T.missingValueAfterParse(configValue)", CommonClassNames.configValueExtractionException)
                .endControlFlow()
                .addStatement("return parsed");

            var type = typeBuilder.addMethod(method.build())
                .addAnnotation(CommonClassNames.module)
                .addModifiers(Modifier.PUBLIC)
                .build();

            var packageElement = this.elements.getPackageOf(config);

            var javaFile = JavaFile.builder(packageElement.getQualifiedName().toString(), type).build();

            CommonUtils.safeWriteTo(this.processingEnv, javaFile);
        }
    }
}
