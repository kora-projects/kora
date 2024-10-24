package ru.tinkoff.kora.database.annotation.processor.jdbc;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

public class JdbcEntityAnnotationProcessor extends AbstractKoraProcessor {
    private JdbcEntityGenerator generator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("ru.tinkoff.kora.database.jdbc.EntityJdbc");
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new JdbcEntityGenerator(processingEnv.getTypeUtils(), processingEnv.getElementUtils(), processingEnv.getFiler());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@JdbcEntity only works on records and java bean like classes");
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
                    throw new RuntimeException(e);
                }
            }
        }

        return false;
    }

}

