package ru.tinkoff.kora.aws.s3.annotation.processor.gen;

import com.palantir.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClassNames;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClientAnnotationProcessor;
import ru.tinkoff.kora.aws.s3.annotation.processor.S3ClientUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.stream.IntStream;

public class BucketsConfigGenerator {
    public static TypeSpec generate(ProcessingEnvironment processingEnv, TypeElement s3client) {
        var paths = S3ClientUtils.parseConfigBuckets(s3client);
        if (paths.isEmpty()) {
            return null;
        }
        var packageName = processingEnv.getElementUtils().getPackageOf(s3client).getQualifiedName().toString();
        var configType = ClassName.get(packageName, NameUtils.generatedType(s3client, "BucketsConfig"));
        var b = TypeSpec.classBuilder(configType)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(AnnotationUtils.generated(S3ClientAnnotationProcessor.class))
            .addOriginatingElement(s3client);
        var constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config");
        var equals = MethodSpec.methodBuilder("equals")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ClassName.get(Object.class), "o")
            .returns(TypeName.BOOLEAN)
            .beginControlFlow("if (o instanceof $T that)", configType)
            .addStatement("return $L", IntStream.range(0, paths.size())
                .mapToObj(i -> CodeBlock.of("$T.equals(this.bucket_$L, that.bucket_$L)", Objects.class, i, i))
                .collect(CodeBlock.joining("\n  && ")))
            .nextControlFlow("else")
            .addStatement("return false")
            .endControlFlow()
            .build();
        var hashCode = MethodSpec.methodBuilder("hashCode")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("return $T.hash($L)", Objects.class, IntStream.range(0, paths.size()).mapToObj(i -> CodeBlock.of("bucket_$L", i)).collect(CodeBlock.joining(", ")))
            .build();
        for (var i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            if (path.startsWith(".")) {
                var annotation = AnnotationUtils.findAnnotation(s3client, S3ClassNames.Annotation.CLIENT);
                var annotationValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(annotation, "value");
                if (annotationValue == null) {
                    annotationValue = s3client.getSimpleName().toString();
                }
                path = annotationValue + path;
            }
            b.addField(String.class, "bucket_" + i, Modifier.PUBLIC, Modifier.FINAL);
            constructor.addStatement("this.bucket_$L = config.get($S).asString()", i, path);
        }

        b.addMethod(constructor.build());
        b.addMethod(equals);
        b.addMethod(hashCode);
        return b.build();
    }
}
