package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
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
        var realArgs = new ArrayList<Object>(arguments);
        realArgs.add(0, connectionFactory);
        var repositoryClass = compileResult.loadClass("$TestRepository_Impl");
        return new TestObject(repositoryClass, realArgs);
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
