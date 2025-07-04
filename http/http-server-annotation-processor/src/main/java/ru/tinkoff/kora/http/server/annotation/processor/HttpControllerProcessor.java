package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.tinkoff.kora.http.server.annotation.processor.HttpServerClassNames.httpController;

public class HttpControllerProcessor extends AbstractKoraProcessor {
    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(httpController);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var controller : annotatedElements.getOrDefault(httpController, List.of())) {
            this.processController(controller.element(), roundEnv);
        }
    }

    private void processController(Element controller, RoundEnvironment roundEnv) {
        var methodGenerator = new RequestHandlerGenerator(this.elements, this.types, this.processingEnv);
        var generator = new ControllerModuleGenerator(this.types, this.elements, roundEnv, methodGenerator);
        JavaFile file;
        try {
            file = generator.generateController((TypeElement) controller);
        } catch (HttpProcessorException e) {
            e.printError(this.processingEnv);
            return;
        }
        if (file != null) {
            CommonUtils.safeWriteTo(this.processingEnv, file);
        }
    }
}
