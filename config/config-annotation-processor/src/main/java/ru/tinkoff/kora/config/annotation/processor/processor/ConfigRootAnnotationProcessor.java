package ru.tinkoff.kora.config.annotation.processor.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.config.annotation.processor.ConfigParserGenerator;
import ru.tinkoff.kora.config.annotation.processor.ConfigRootModuleGenerator;
import ru.tinkoff.kora.config.annotation.processor.exception.NewRoundWantedException;
import ru.tinkoff.kora.config.common.ConfigRoot;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigRootAnnotationProcessor extends AbstractKoraProcessor {
    private final List<TypeElement> previouslyUnprocessedElements = new ArrayList<>();
    private boolean initialized = false;

    private ConfigRootModuleGenerator moduleGenerator;
    private ConfigParserGenerator configParserGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ConfigRoot.class.getCanonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var configRoot = processingEnv.getElementUtils().getTypeElement(ConfigRoot.class.getCanonicalName());
        if (configRoot == null) {
            return;
        }
        this.moduleGenerator = new ConfigRootModuleGenerator(processingEnv);
        this.configParserGenerator = new ConfigParserGenerator(processingEnv);
        this.initialized = true;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!this.initialized) {
            return false;
        }
        try {
            return process0(roundEnv);
        } catch (ProcessingErrorException e) {
            e.printError(this.processingEnv);
            return false;
        }
    }

    public boolean process0(RoundEnvironment roundEnv) {
        var types = roundEnv.getElementsAnnotatedWith(ConfigRoot.class)
            .stream()
            .filter(e -> e.getKind().isClass())
            .map(TypeElement.class::cast)
            .collect(Collectors.toList());

        types.addAll(previouslyUnprocessedElements);
        previouslyUnprocessedElements.clear();

        for (var type : types) {
            try {
                var configParser = this.configParserGenerator.generate(roundEnv, type.asType());
                CommonUtils.safeWriteTo(this.processingEnv, configParser);
                var module = this.moduleGenerator.generateModule(roundEnv, type);
                CommonUtils.safeWriteTo(this.processingEnv, module);
            } catch (NewRoundWantedException e) {
                previouslyUnprocessedElements.add(e.getElement());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
}
