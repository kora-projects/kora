package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AbstractRepositoryTest extends AbstractAnnotationProcessorTest {
    protected static class TestRepository {
        public final Class<?> repositoryClass;
        private final Object repositoryObject;

        protected TestRepository(Class<?> repositoryClass, Object repositoryObject) {
            this.repositoryClass = repositoryClass;
            this.repositoryObject = repositoryObject;
        }

        @SuppressWarnings("unchecked")
        public <T> T invoke(String method, Object... args) {
            for (var repositoryClassMethod : repositoryClass.getMethods()) {
                if (repositoryClassMethod.getName().equals(method) && repositoryClassMethod.getParameters().length == args.length) {
                    try {
                        var result = repositoryClassMethod.invoke(this.repositoryObject, args);
                        if (result instanceof Mono<?> mono) {
                            return (T) mono.block();
                        }
                        if (result instanceof Future<?> future) {
                            return (T) future.get();
                        }
                        return (T) result;
                    } catch (InvocationTargetException e) {
                        if (e.getTargetException() instanceof RuntimeException re) {
                            throw re;
                        } else {
                            throw new RuntimeException(e);
                        }
                    } catch (IllegalAccessException | ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            throw new IllegalArgumentException();
        }
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.database.common.annotation.*;
            import ru.tinkoff.kora.database.common.*;
            """;
    }

    protected TestRepository compile(Object connectionFactory, List<?> arguments, @Language("java") String... sources) {
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
            return new TestRepository(repositoryClass, repository);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
