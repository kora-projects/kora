package ru.tinkoff.kora.http.client.annotation.processor;

import com.palantir.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.http.client.annotation.processor.HttpClientClassNames.httpClientAnnotation;

public class HttpClientAnnotationProcessor extends AbstractKoraProcessor {
    private ClientClassGenerator clientGenerator;
    private ConfigClassGenerator configGenerator;
    private boolean initialized;
    private ConfigModuleGenerator configModuleGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var httpClient = processingEnv.getElementUtils().getTypeElement(httpClientAnnotation.canonicalName());
        if (httpClient == null) {
            return;
        }
        this.initialized = true;
        this.clientGenerator = new ClientClassGenerator(processingEnv);
        this.configGenerator = new ConfigClassGenerator();
        this.configModuleGenerator = new ConfigModuleGenerator(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(httpClientAnnotation.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        if (roundEnv.processingOver()) {
            return false;
        }
        var elements = annotations.stream()
            .filter(a -> a.getQualifiedName().contentEquals(httpClientAnnotation.canonicalName()))
            .flatMap(a -> roundEnv.getElementsAnnotatedWith(a).stream())
            .collect(Collectors.toSet());

        for (var httpClient : elements) {
            if (httpClient.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            var typeElement = (TypeElement) httpClient;
            try {
                this.generateClient(typeElement);
            } catch (ProcessingErrorException e) {
                throw e;
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return !elements.isEmpty();
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
