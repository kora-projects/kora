package ru.tinkoff.kora.s3.client.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.s3.client.annotation.processor.gen.BucketsConfigGenerator;
import ru.tinkoff.kora.s3.client.annotation.processor.gen.ClientGenerator;
import ru.tinkoff.kora.s3.client.annotation.processor.gen.ModuleGenerator;

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
                throw new ProcessingErrorException("@S3.Client annotation is intended to be used on interfaces, but was: " + element.element().getKind().name(), element.element());
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
                throw new IllegalStateException(e);
            }
        }
    }
}
