package ru.tinkoff.kora.database.annotation.processor.cassandra;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

public class CassandraUdtAnnotationProcessor extends AbstractKoraProcessor {
    private UserDefinedTypeResultExtractorGenerator resultExtractorGenerator;
    private UserDefinedTypeStatementSetterGenerator statementSetterGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CassandraTypes.UDT_ANNOTATION.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.resultExtractorGenerator = new UserDefinedTypeResultExtractorGenerator(processingEnv);
        this.statementSetterGenerator = new UserDefinedTypeStatementSetterGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                var type = element.asType();
                this.statementSetterGenerator.generate(type);
                this.resultExtractorGenerator.generate(type);
            }
        }
        return false;
    }
}
