package io.koraframework.database.annotation.processor.jdbc;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JdbcEntityAnnotationProcessor extends AbstractKoraProcessor {
    private JdbcEntityGenerator generator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(JdbcTypes.JDBC_ENTITY);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new JdbcEntityGenerator(processingEnv.getTypeUtils(), processingEnv.getElementUtils(), processingEnv.getFiler());
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotatedList : annotatedElements.values()) {
            for (var annotated : annotatedList) {
                var element = annotated.element();
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, """
                        JDBC entity type is invalid:
                          %s

                        Problem:
                          @EntityJdbc can be used only on records and Java bean-like classes.

                        Hint:
                          Kora needs fields or record components to generate JDBC mappers.

                        Fix:
                          Move @EntityJdbc to a record/class entity type, or remove the annotation.
                        """.formatted(element), element);
                    continue;
                }
                try {
                    var entity = DbEntity.parseEntity(this.types, element.asType());
                    if (entity == null) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Can't parse entity from type: " + element, element);
                        continue;
                    }

                    this.generator.generateRowMapper(entity);
                    this.generator.generateListResultSetMapper(entity);
                    this.generator.generateResultSetMapper(entity);
                } catch (ProcessingErrorException e) {
                    e.printError(processingEnv);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException("Kora internal error: failed to generate JDBC entity mappers for " + element, e);
                }
            }
        }
    }

}
