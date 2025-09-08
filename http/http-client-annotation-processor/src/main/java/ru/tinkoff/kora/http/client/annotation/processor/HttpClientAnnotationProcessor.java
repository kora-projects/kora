package ru.tinkoff.kora.http.client.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpClientAnnotationProcessor extends AbstractKoraProcessor {
    private ClientClassGenerator clientGenerator;
    private ConfigClassGenerator configGenerator;
    private ConfigModuleGenerator configModuleGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.clientGenerator = new ClientClassGenerator(processingEnv);
        this.configGenerator = new ConfigClassGenerator(processingEnv.getElementUtils());
        this.configModuleGenerator = new ConfigModuleGenerator(processingEnv);
    }

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(HttpClientClassNames.httpClientAnnotation);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var elements = annotatedElements.getOrDefault(HttpClientClassNames.httpClientAnnotation, List.of());
        for (var annotated : elements) {
            var httpClient = annotated.element();
            if (httpClient.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            var typeElement = (TypeElement) httpClient;
            try {
                this.generateClient(typeElement);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }
    }

    private void generateClient(TypeElement element) {
        var packageName = this.elements.getPackageOf(element).getQualifiedName().toString();
        var client = this.clientGenerator.generate(element);
        var config = this.configGenerator.generate(element);
        var configModule = this.configModuleGenerator.generate(element);
        CommonUtils.safeWriteTo(this.processingEnv, configModule);
        CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, client).build());
        CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config).build());
    }
}
