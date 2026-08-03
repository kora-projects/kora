package io.koraframework.database.annotation.processor.cassandra;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.database.annotation.processor.entity.DbEntity;

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
                    this.messager.printMessage(Diagnostic.Kind.ERROR, """
                        Cassandra UDT type is invalid:
                          %s

                        Problem:
                          @UDT can be used only on records and Java bean-like classes.

                        Hint:
                          Kora needs fields or record components to map the Cassandra user-defined type.

                        Fix:
                          Move @UDT to a record/class entity type, or remove the annotation.
                        """.formatted(element), element);
                    continue;
                }
                var type = element.asType();
                var typeElement = (TypeElement) element;
                var entity = DbEntity.parseEntity(types, type);
                if (entity == null) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, """
                        Cassandra UDT type is invalid:
                          %s

                        Problem:
                          Kora can't parse this type as a database entity.

                        Hint:
                          UDT mapper generation requires a supported entity shape with readable columns and a usable constructor.

                        Fix:
                          Check entity fields/record components, embedded fields, and constructor annotations.
                        """.formatted(element), element);
                    continue;
                }
                this.statementSetterGenerator.generate(typeElement, type);
                this.resultExtractorGenerator.generate(typeElement, type);
            }
        }
    }
}
