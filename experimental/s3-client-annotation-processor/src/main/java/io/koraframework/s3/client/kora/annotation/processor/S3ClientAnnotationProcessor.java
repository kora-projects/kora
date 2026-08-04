package io.koraframework.s3.client.kora.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.s3.client.kora.annotation.processor.gen.BucketsConfigGenerator;
import io.koraframework.s3.client.kora.annotation.processor.gen.ClientGenerator;
import io.koraframework.s3.client.kora.annotation.processor.gen.ModuleGenerator;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class S3ClientAnnotationProcessor extends AbstractKoraProcessor {
    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(S3ClassNames.Annotation.CLIENT);
    }

    @Override
    protected void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var element : annotatedElements.getOrDefault(S3ClassNames.Annotation.CLIENT, List.of())) {
            if (!element.element().getKind().isInterface()) {
                throw new ProcessingErrorException("""
                    @S3.Client can only be applied to an interface, but '%s' is %s.

                    Fix: move @S3.Client to an interface that declares abstract S3 operation methods.
                    Example: @S3.Client interface FilesClient { @S3.Get GetObjectResult get(@S3.Bucket String bucket, String key); }
                    """.formatted(element.element(), element.element().getKind().name()).trim(), element.element());
            }

            var s3client = (TypeElement) element.element();
            var packageName = processingEnv.getElementUtils().getPackageOf(s3client).getQualifiedName().toString();

            try {
                var bucketsConfig = BucketsConfigGenerator.generate(processingEnv, s3client);
                if (bucketsConfig != null) {
                    var configFile = JavaFile.builder(packageName, bucketsConfig).build();
                    configFile.writeTo(processingEnv.getFiler());
                }
                var module = ModuleGenerator.generate(processingEnv, s3client);
                var moduleFile = JavaFile.builder(packageName, module).build();
                moduleFile.writeTo(processingEnv.getFiler());

                var client = ClientGenerator.generate(processingEnv, s3client);
                var implFile = JavaFile.builder(packageName, client).build();
                implFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                throw new IllegalStateException("""
                    Kora internal error: failed to write generated S3 client files for '%s'.

                    This is not caused by the S3 client declaration itself. Check that annotation processing can write to the generated sources directory and that no generated file is locked by another process.
                    """.formatted(s3client.getQualifiedName()).trim(), e);
            }
        }
    }
}
