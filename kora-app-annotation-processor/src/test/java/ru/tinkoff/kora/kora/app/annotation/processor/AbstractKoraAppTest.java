package ru.tinkoff.kora.kora.app.annotation.processor;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractKoraAppTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.application.graph.*;
            import java.util.Optional;
            """;
    }

    protected ApplicationGraphDraw compile(@Language("java") String... sources) {
        var compileResult = compile(List.of(new KoraAppProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        try {
            var appClass = compileResult.loadClass("ExampleApplicationGraph");
            @SuppressWarnings("unchecked")
            var object = (Supplier<ApplicationGraphDraw>) appClass.getConstructor().newInstance();
            return object.get();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected ApplicationGraphDraw compileWithAop(@Language("java") String... sources) {
        var compileResult = compile(List.of(new AopAnnotationProcessor(), new KoraAppProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        try {
            var appClass = compileResult.loadClass("ExampleApplicationGraph");
            @SuppressWarnings("unchecked")
            var object = (Supplier<ApplicationGraphDraw>) appClass.getConstructor().newInstance();
            return object.get();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
