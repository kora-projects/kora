package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.squareup.javapoet.ClassName;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CassandraUdtAnnotationProcessor extends AbstractKoraProcessor {
    private UserDefinedTypeResultExtractorGenerator resultExtractorGenerator;
    private UserDefinedTypeStatementSetterGenerator statementSetterGenerator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(CassandraTypes.UDT_ANNOTATION);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.resultExtractorGenerator = new UserDefinedTypeResultExtractorGenerator(processingEnv);
        this.statementSetterGenerator = new UserDefinedTypeStatementSetterGenerator(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotatedList : annotatedElements.values()) {
            for (var annotated : annotatedList) {
                var element = annotated.element();
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and records can be annotated with @UDT", element);
                    continue;
                }
                var type = element.asType();
                var typeElement = (TypeElement) element;
                var entity = DbEntity.parseEntity(types, type);
                if (entity == null) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Invalid type for @UDT", element);
                    continue;
                }
                this.statementSetterGenerator.generate(typeElement, type);
                this.resultExtractorGenerator.generate(typeElement, type);
            }
        }
    }
}
