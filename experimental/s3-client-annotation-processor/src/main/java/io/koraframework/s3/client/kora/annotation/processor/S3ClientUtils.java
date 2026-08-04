package io.koraframework.s3.client.kora.annotation.processor;

import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

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
                throw new ProcessingErrorException(missingBucketValueError(s3client.getQualifiedName().toString()), s3client, onClass);
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
                throw new ProcessingErrorException(missingBucketValueError(methodName((ExecutableElement) enclosedElement)), enclosedElement, onMethod);
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
                    throw new ProcessingErrorException("""
                        S3 bucket parameter is ambiguous for '%s': more than one parameter is annotated with @S3.Bucket.

                        Fix: keep @S3.Bucket on exactly one bucket parameter, or remove all bucket parameters and configure the bucket with @S3.Bucket("config.path") on the S3 client class or method.
                        Example: fun get(@S3.Bucket bucket: String, key: String): GetObjectResult
                        """.formatted(methodName(method)).trim(), method, ann);
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
            if (S3ClassNames.S3_CREDENTIALS.equals(typeName)) {
                if (foundParam != null) {
                    throw new ProcessingErrorException("""
                        S3 credentials are ambiguous for '%s': more than one parameter has type S3Credentials.

                        Fix: keep at most one S3Credentials parameter. If credentials should come from config, remove the S3Credentials parameter completely.
                        Example: fun get(credentials: S3Credentials, @S3.Bucket bucket: String, key: String): GetObjectResult
                        """.formatted(methodName(method)).trim(), method, null);
                }
                foundParam = param;
            }
        }
        return foundParam;
    }

    private static String missingBucketValueError(String targetName) {
        return """
            S3 bucket config path is missing for '%s': @S3.Bucket was used without a value.

            Fix: pass the config path that contains the bucket name, or move @S3.Bucket to a method parameter when the bucket is passed at runtime.
            Example: @S3.Bucket("s3.clients.default.bucket")
            """.formatted(targetName).trim();
    }

    private static String methodName(ExecutableElement method) {
        return method.getEnclosingElement() + "." + method.getSimpleName();
    }
}
