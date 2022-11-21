package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

public class HttpControllerProcessor extends AbstractKoraProcessor {
    private boolean initialized = false;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(HttpController.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var httpController = processingEnv.getElementUtils().getTypeElement(HttpController.class.getCanonicalName());
        if (httpController == null) {
            return;
        }
        this.initialized = true;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        for (var controller : roundEnv.getElementsAnnotatedWith(HttpController.class)) {
            this.processController(controller, roundEnv);
        }
        return false;
    }

    private void processController(Element controller, RoundEnvironment roundEnv) {
        var methodGenerator = new RequestHandlerGenerator(this.elements, this.types, this.processingEnv);
        var generator = new ControllerModuleGenerator(this.types, this.elements, roundEnv, methodGenerator);
        JavaFile file = null;
        try {
            file = generator.generateController((TypeElement) controller);
        } catch (HttpProcessorException e) {
            e.printError(this.processingEnv);
        }
        if (file != null) {
            try {
                file.writeTo(this.processingEnv.getFiler());
            } catch (IOException e) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            }
        }
    }
}
