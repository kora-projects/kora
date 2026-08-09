package io.koraframework.database.annotation.processor.mongo;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MongoEntityAnnotationProcessor extends AbstractKoraProcessor {

    private MongoCodecGenerator generator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(MongoTypes.MONGO_ENTITY);
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.generator = new MongoCodecGenerator(processingEnv.getTypeUtils(), processingEnv.getElementUtils(), processingEnv.getFiler());
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        for (var annotatedList : annotatedElements.values()) {
            for (var annotated : annotatedList) {
                var element = annotated.element();
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, """
                        Mongo entity type is invalid:
                          %s

                        Problem:
                          @EntityMongo can be used only on records and Java bean-like classes.

                        Hint:
                          Kora needs record components, or bean fields with a getter and a setter, to generate a BSON codec.

                        Fix:
                          Move @EntityMongo to a record/class entity type, or remove the annotation.
                        """.formatted(element), element);
                    continue;
                }
                try {
                    var entity = MongoEntity.parse(this.types, element.asType());
                    if (entity == null) {
                        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, """
                            Mongo entity type is invalid:
                              %s

                            Problem:
                              Entity fields can not be resolved from this type.

                            Hint:
                              Kora maps record components, or bean fields that have both a getter and a setter of the field type.

                            Fix:
                              Turn the type into a record, add getters and setters, or supply a custom Codec for it.
                            """.formatted(element), element);
                        continue;
                    }

                    this.generator.generate(entity);
                } catch (ProcessingErrorException e) {
                    e.printError(this.processingEnv);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException("Kora internal error: failed to generate Mongo codec for " + element, e);
                }
            }
        }
    }
}
