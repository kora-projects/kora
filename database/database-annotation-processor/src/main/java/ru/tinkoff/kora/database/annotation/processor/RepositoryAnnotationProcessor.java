package ru.tinkoff.kora.database.annotation.processor;

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
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class RepositoryAnnotationProcessor extends AbstractKoraProcessor {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnnotationProcessor.class);

    private RepositoryBuilder repositoryBuilder;
    private boolean initialized = false;
    private TypeElement repositoryAnnotation;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(DbUtils.REPOSITORY_ANNOTATION.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.repositoryAnnotation = processingEnv.getElementUtils().getTypeElement(DbUtils.REPOSITORY_ANNOTATION.canonicalName());
        if (repositoryAnnotation == null) {
            return;
        }
        this.initialized = true;
        this.repositoryBuilder = new RepositoryBuilder(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        var elements = roundEnv.getElementsAnnotatedWith(this.repositoryAnnotation);
        LogUtils.logElementsFull(log, Level.DEBUG, "Generating Repository for", elements);
        for (var element : elements) {
            try {
                this.processClass(element);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private void processClass(Element classElement) throws IOException {
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
