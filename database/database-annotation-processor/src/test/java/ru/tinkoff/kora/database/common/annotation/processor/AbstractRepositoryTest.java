package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class AbstractRepositoryTest extends AbstractAnnotationProcessorTest {
    protected static class TestRepository {
        private final Class<?> repositoryClass;
        private final Object repositoryObject;

        protected TestRepository(Class<?> repositoryClass, Object repositoryObject) {
            this.repositoryClass = repositoryClass;
            this.repositoryObject = repositoryObject;
        }

        public Object invoke(String method, Object... args) {
            for (var repositoryClassMethod : repositoryClass.getMethods()) {
                if (repositoryClassMethod.getName().equals(method) && repositoryClassMethod.getParameters().length == args.length) {
                    try {
                        return repositoryClassMethod.invoke(this.repositoryObject, args);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            throw new IllegalArgumentException();
        }
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
            var repository = repositoryClass.getConstructors()[0].newInstance(realArgs);
            return new TestRepository(repositoryClass, repository);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
