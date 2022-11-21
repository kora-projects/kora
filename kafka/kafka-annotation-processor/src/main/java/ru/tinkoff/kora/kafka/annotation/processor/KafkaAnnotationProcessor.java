package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.JavaFile;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class KafkaAnnotationProcessor extends AbstractKoraProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(KafkaClassNames.kafkaIncoming.canonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var kafkaIncoming = this.elements.getTypeElement(KafkaClassNames.kafkaIncoming.canonicalName());
        try {
            for (var element : roundEnv.getElementsAnnotatedWith(kafkaIncoming)
                .stream()
                .map(Element::getEnclosingElement)
                .collect(Collectors.toSet())) {
                processController(element);
            }
        } catch (ProcessingErrorException e) {
            e.printError(this.processingEnv);
        } catch (Exception e) {
            this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }

        return false;
    }

    private void processController(Element controller) throws IOException {
        var methodGenerator = new KafkaConsumerGenerator(processingEnv);
        var kafkaConfigGenerator = new KafkaConfigGenerator(processingEnv);
        var generator = new KafkaModuleGenerator(processingEnv, methodGenerator, kafkaConfigGenerator);
        JavaFile file = generator.generateModule(controller);

        if (file != null) {
            file.writeTo(this.processingEnv.getFiler());
        }
    }
}
