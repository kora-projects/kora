package ru.tinkoff.kora.database.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RepositoryAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnnotationProcessor.class);

    private RepositoryBuilder repositoryBuilder;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(DbUtils.REPOSITORY_ANNOTATION);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.repositoryBuilder = new RepositoryBuilder(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var elements = annotatedElements.getOrDefault(DbUtils.REPOSITORY_ANNOTATION, List.of());
        if (elements.isEmpty()) {
            return;
        }
        LogUtils.logAnnotatedElementsFull(log, Level.DEBUG, "Generating Repository for", elements);
        for (var element : elements) {
            try {
                this.processClass(element.element());
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }
    }

    private void processClass(Element classElement) {
        if (classElement.getKind() != ElementKind.INTERFACE && (classElement.getKind() == ElementKind.CLASS && !classElement.getModifiers().contains(Modifier.ABSTRACT))) {
            throw new ProcessingErrorException(List.of(new ProcessingError("@Repository is only applicable to interfaces and abstract classes", classElement)));
        }

        var typeSpec = this.repositoryBuilder.build((TypeElement) classElement);
        if (typeSpec == null) {
            return;
        }

        var packageElement = this.processingEnv.getElementUtils().getPackageOf(classElement);
        var packageName = packageElement.getQualifiedName().toString();
        var javaFile = JavaFile.builder(packageName, typeSpec)
            .build();
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);
    }
}
