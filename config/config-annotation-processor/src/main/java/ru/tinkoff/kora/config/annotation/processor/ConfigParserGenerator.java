package ru.tinkoff.kora.config.annotation.processor;

import com.squareup.javapoet.*;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.config.annotation.processor.exception.NewRoundWantedException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ConfigValueUtils;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;

public class ConfigParserGenerator {
    private static final Logger log = LoggerFactory.getLogger(ConfigParserGenerator.class);
    private final Types types;
    private final Elements elements;
    private final TypeElement configValueExtractorTypeElement;
    private final TypeElement objectConfigValueExtractorTypeElement;
    private final TypeElement configValueUtilsTypeElement;

    public ConfigParserGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        var configValueExtractorTypeErasure = types.erasure(this.elements.getTypeElement(ConfigValueExtractor.class.getCanonicalName()).asType());
        this.configValueExtractorTypeElement = (TypeElement) this.types.asElement(configValueExtractorTypeErasure);
        this.objectConfigValueExtractorTypeElement = this.elements.getTypeElement(ObjectConfigValueExtractor.class.getCanonicalName());
        this.configValueUtilsTypeElement = this.elements.getTypeElement(ConfigValueUtils.class.getCanonicalName());
    }

    public JavaFile generate(RoundEnvironment roundEnv, TypeMirror targetType) {
        log.info("Generating ConfigValueExtractor for {}", targetType);
        var element = (TypeElement) this.types.asElement(targetType);
        var packageName = this.elements.getPackageOf(element).getQualifiedName().toString();
        var typeName = CommonUtils.getOuterClassesAsPrefix(element) + element.getSimpleName() + "_" + ConfigValueExtractor.class.getSimpleName();
        var fields = new HashMap<TypeName, String>();
        var extractorType = this.types.getDeclaredType(this.objectConfigValueExtractorTypeElement, targetType);
        var typeBuilder = TypeSpec.classBuilder(typeName)
            .superclass(extractorType)
            .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", ConfigParserGenerator.class.getCanonicalName()).build())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        var constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC);

        var methodBody = CodeBlock.builder()
            .add("$[").add("return new $T(", targetType).add("\n$]")
            .indent();

        var elementConstructor = getConstructor(element);
        var parameters = elementConstructor.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            var param = parameters.get(i);
            var paramType = param.asType();
            if (paramType.getKind() == TypeKind.ERROR && !roundEnv.processingOver()) {
                throw new NewRoundWantedException(element);
            }

            var paramName = param.getSimpleName();
            var defaultExtractor = findDefaultExtractor(paramType);
            var trailingComma = (i + 1 != parameters.size() ? "," : "");
            var isNullable = CommonUtils.isNullable(param);

            methodBody.add("$[");
            if (isNullable) {
                methodBody.add("!config.hasPath($S) ? null : ", paramName);
            }

            if (defaultExtractor != null) {
                methodBody.add("config.$L($S)$L", defaultExtractor, paramName, trailingComma);
            } else {
                var fieldExtractor = fields.computeIfAbsent(TypeName.get(paramType), paramTypeName -> {
                    var fieldName = "extractor" + fields.size();
                    var paramExtractorTypeName = TypeName.get(this.types.getDeclaredType(this.configValueExtractorTypeElement, paramType));
                    typeBuilder.addField(FieldSpec.builder(paramExtractorTypeName, fieldName, Modifier.PRIVATE, Modifier.FINAL).build());
                    constructorBuilder
                        .addParameter(ParameterSpec.builder(paramExtractorTypeName, fieldName).build())
                        .addStatement("this.$1L = $1L", fieldName);

                    return fieldName;
                });
                if (isNullable) {
                    methodBody.add("$L.extract(config.getValue($S))$L", fieldExtractor, paramName, trailingComma);
                } else {
                    methodBody.add("$L.extract($T.getValueOrNull(config, $S))$L", fieldExtractor, this.configValueUtilsTypeElement, paramName, trailingComma);
                }
            }

            methodBody.add("\n$]");
        }

        methodBody.unindent().addStatement(")");

        var type = typeBuilder
            .addMethod(constructorBuilder.build())
            .addMethod(MethodSpec.methodBuilder("extract")
                .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
                .returns(TypeName.get(targetType))
                .addParameter(TypeName.get(Config.class), "config")
                .addAnnotation(Override.class)
                .addCode(methodBody.build())
                .build()
            )
            .build();

        return JavaFile.builder(packageName, type).build();
    }

    private ExecutableElement getConstructor(TypeElement element) {
        return CommonUtils.findConstructors(element, m -> !m.contains(Modifier.PRIVATE)).get(0);
    }

    @Nullable
    private String findDefaultExtractor(TypeMirror parameterType) {
        return switch (parameterType.toString()) {
            case "java.lang.Boolean", "boolean" -> "getBoolean";
            case "java.lang.Number" -> "getNumber";
            case "java.lang.Integer", "int" -> "getInt";
            case "java.lang.Long", "long" -> "getLong";
            case "java.lang.Double", "double" -> "getDouble";
            case "java.lang.String" -> "getString";
            case "com.typesafe.config.ConfigObject" -> "getObject";
            case "com.typesafe.config.Config" -> "getConfig";
            case "java.time.Duration" -> "getDuration";
            case "java.time.Period" -> "getPeriod";
            case "java.time.temporal.TemporalAmount" -> "getTemporal";
            case "com.typesafe.config.ConfigList" -> "getList";
            default -> null;
        };
    }

}
