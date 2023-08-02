package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public abstract class AbstractRepositoryTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.common.annotation.*;
            import ru.tinkoff.kora.database.common.*;
            """;
    }

    protected TestObject compile(Object connectionFactory, List<?> arguments, @Language("java") String... sources) {
        var compileResult = compile(List.of(new RepositoryAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        Assertions.assertThat(compileResult.warnings()).hasSize(0);

        try {
            var repositoryClass = compileResult.loadClass("$TestRepository_Impl");
            var realArgs = new Object[arguments.size() + 1];
            realArgs[0] = connectionFactory;
            System.arraycopy(arguments.toArray(), 0, realArgs, 1, arguments.size());
            for (int i = 0; i < realArgs.length; i++) {
                if (realArgs[i] instanceof GeneratedResultCallback<?> gr) {
                    realArgs[i] = gr.get();
                }
            }
            var repository = repositoryClass.getConstructors()[0].newInstance(realArgs);
            return new TestObject(repositoryClass, repository);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected TestObject compileForArgs(List<?> arguments, @Language("java") String... sources) {
        var compileResult = compile(List.of(new RepositoryAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        Assertions.assertThat(compileResult.warnings()).hasSize(0);

        try {
            var repositoryClass = compileResult.loadClass("$TestRepository_Impl");
            var realArgs = arguments.toArray();
            var repository = repositoryClass.getConstructors()[0].newInstance(realArgs);
            return new TestObject(repositoryClass, repository);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
