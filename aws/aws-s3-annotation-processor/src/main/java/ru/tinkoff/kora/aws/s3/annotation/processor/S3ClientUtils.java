package ru.tinkoff.kora.aws.s3.annotation.processor;

import com.palantir.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.lang.model.element.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class S3ClientUtils {
    public static List<String> parseConfigBuckets(TypeElement s3client) {
        var bucketPaths = new LinkedHashSet<String>();
        var onClass = AnnotationUtils.findAnnotation(s3client, S3ClassNames.Annotation.BUCKET);
        if (onClass != null) {
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onClass, "value");
            if (value == null) {
                throw new ProcessingErrorException("@S3.Bucket annotation is missing value", s3client, onClass);
            }
            bucketPaths.add(value);
        }
        for (var enclosedElement : s3client.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (enclosedElement.getModifiers().contains(Modifier.DEFAULT) || enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            var onMethod = AnnotationUtils.findAnnotation(enclosedElement, S3ClassNames.Annotation.BUCKET);
            if (onMethod == null) {
                continue;
            }
            var value = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(onMethod, "value");
            if (value == null) {
                throw new ProcessingErrorException("@S3.Bucket annotation is missing value", enclosedElement, onMethod);
            }
            bucketPaths.add(value);
        }
        return new ArrayList<>(bucketPaths);
    }

    @Nullable
    public static VariableElement bucketParameter(ExecutableElement method) {
        VariableElement foundParam = null;
        for (var param : method.getParameters()) {
            var ann = AnnotationUtils.findAnnotation(param, S3ClassNames.Annotation.BUCKET);
            if (ann != null) {
                if (foundParam != null) {
                    throw new ProcessingErrorException("Multiple @S3.Bucket annotations found", method, ann);
                }
                foundParam = param;
            }
        }
        return foundParam;
    }

    @Nullable
    public static VariableElement credentialsParameter(ExecutableElement method) {
        VariableElement foundParam = null;
        for (var param : method.getParameters()) {
            var typeName = TypeName.get(param.asType());
            if (S3ClassNames.AWS_CREDENTIALS.equals(typeName)) {
                if (foundParam != null) {
                    throw new ProcessingErrorException("Multiple AwsCredentials parameters found", method, null);
                }
                foundParam = param;
            }
        }
        return foundParam;
    }
}
