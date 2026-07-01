package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.ApplicationGraphDraw;
import org.intellij.lang.annotations.Language;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractKoraAppTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.application.graph.*;
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
