package ru.tinkoff.kora.database.annotation.processor.cassandra;

import com.palantir.javapoet.ClassName;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CassandraEntityAnnotationProcessor extends AbstractKoraProcessor {
    private CassandraEntityGenerator generator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(CassandraTypes.CASSANDRA_ENTITY);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new CassandraEntityGenerator(processingEnv.getTypeUtils(), processingEnv.getElementUtils(), processingEnv.getFiler());
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotatedList : annotatedElements.values()) {
            for (var annotated : annotatedList) {
                var element = annotated.element();
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@EntityJdbc only works on records and java bean like classes");
                    continue;
                }
                try {
                    var entity = DbEntity.parseEntity(this.types, element.asType());
                    if (entity == null) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Can't parse entity from type: " + element, element);
                        continue;
                    }

                    this.generator.generateRowMapper(entity);
                    this.generator.generateResultSetMapper(entity);
                    this.generator.generateListResultSetMapper(entity);
                } catch (ProcessingErrorException e) {
                    e.printError(processingEnv);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
