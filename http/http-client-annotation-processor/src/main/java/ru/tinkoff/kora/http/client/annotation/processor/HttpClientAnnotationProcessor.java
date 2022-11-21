package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class HttpClientAnnotationProcessor extends AbstractKoraProcessor {
    private ClientClassGenerator clientGenerator;
    private ConfigClassGenerator configGenerator;
    private boolean initialized;
    private ConfigModuleGenerator configModuleGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var httpClient = processingEnv.getElementUtils().getTypeElement(HttpClient.class.getCanonicalName());
        if (httpClient == null) {
            return;
        }
        this.initialized = true;
        this.clientGenerator = new ClientClassGenerator(processingEnv);
        this.configGenerator = new ConfigClassGenerator(processingEnv);
        this.configModuleGenerator = new ConfigModuleGenerator(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(HttpClient.class.getCanonicalName());
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
            .filter(a -> a.getQualifiedName().contentEquals(HttpClient.class.getCanonicalName()))
            .flatMap(a -> roundEnv.getElementsAnnotatedWith(a).stream())
            .collect(Collectors.toSet());

        for (var httpClient : elements) {
            if (httpClient.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            var typeElement = (TypeElement) httpClient;
            try {
                this.generateClient(typeElement);
            } catch (javax.annotation.processing.FilerException e) {
                throw new RuntimeException(e);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return !elements.isEmpty();
    }

    private void generateClient(TypeElement element) throws IOException {
        var packageName = this.elements.getPackageOf(element).getQualifiedName().toString();
        var client = this.clientGenerator.generate(element);
        var config = this.configGenerator.generate(element);
        var configModule = this.configModuleGenerator.generate(element);
        CommonUtils.safeWriteTo(this.processingEnv, configModule);
        CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, client).build());
        var configFile = this.processingEnv.getFiler().createSourceFile(packageName + "." + config.name(), element);
        try (var w = configFile.openWriter()) {
            w.write(config.content());
        }
    }
}
