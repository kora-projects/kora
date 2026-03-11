package io.koraframework.kafka.annotation.processor.consumer;

import com.palantir.javapoet.ClassName;
import io.koraframework.annotation.processor.common.AbstractKoraProcessor;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kafka.annotation.processor.KafkaClassNames;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KafkaListenerAnnotationProcessor extends AbstractKoraProcessor {

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return Set.of(KafkaClassNames.kafkaListener);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var typeElements = annotatedElements.getOrDefault(KafkaClassNames.kafkaListener, List.of())
            .stream()
            .map(AnnotatedElement::element)
            .map(Element::getEnclosingElement)
            .map(TypeElement.class::cast)
            .collect(Collectors.toSet());
        for (var element : typeElements) {
            try {
                processController(element);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processController(TypeElement controller) throws IOException {
        var methodGenerator = new KafkaConsumerHandlerGenerator();
        var kafkaConfigGenerator = new KafkaConsumerConfigGenerator();
        var kafkaConsumerContainerGenerator = new KafkaConsumerContainerGenerator();
        var generator = new KafkaConsumerModuleGenerator(processingEnv, methodGenerator, kafkaConfigGenerator, kafkaConsumerContainerGenerator);
        var file = generator.generateModule(controller);
        file.writeTo(this.processingEnv.getFiler());
    }
}
