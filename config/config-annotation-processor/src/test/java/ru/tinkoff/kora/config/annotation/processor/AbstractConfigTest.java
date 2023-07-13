package ru.tinkoff.kora.config.annotation.processor;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class AbstractConfigTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.config.common.annotation.*;
            """;
    }

    protected ConfigValueExtractor<Object> compileConfig(List<?> arguments, @Language("java") String... sources) {
        super.compile(List.of(new ConfigParserAnnotationProcessor(), new ConfigSourceAnnotationProcessor()), sources);
        this.compileResult.assertSuccess();
        var args = arguments
            .stream()
            .map(o -> o instanceof GeneratedResultCallback<?> c ? c.get() : o)
            .toArray();

        try {
            return (ConfigValueExtractor<Object>) this.compileResult.loadClass("$TestConfig_ConfigValueExtractor")
                .getConstructors()[0]
                .newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
