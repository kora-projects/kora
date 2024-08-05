package ru.tinkoff.kora.database.common.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.common.annotation.processor.jdbc.AbstractJdbcRepositoryTest;
import ru.tinkoff.kora.database.jdbc.JdbcRepository;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TagPropagationTest extends AbstractJdbcRepositoryTest {

    @Test
    public void testAbstractClassRepository() {
        var result = compile(
            List.of(new RepositoryAnnotationProcessor()),
            """
            @Repository
            @Tag({TestRepository.class, JdbcRepository.class, Integer.class})
            public interface TestRepository extends JdbcRepository {
                @Query("INSERT INTO table(value) VALUES (:value)")
                void abstractMethod(String value);
            }
            """
        );

        result.assertSuccess();

        var repositoryClass = result.loadClass("$TestRepository_Impl");

        var annotation = repositoryClass.getAnnotation(Tag.class);

        assertNotNull(annotation, "@Tag annotation is not propagated");

        var tagClasses = Arrays.stream(annotation.value())
            .map(Class::getCanonicalName)
            .toList();

        Assertions.assertThat(tagClasses)
            .containsExactlyInAnyOrder(
                testPackage() + ".TestRepository",
                JdbcRepository.class.getCanonicalName(),
                Integer.class.getCanonicalName()
            );
    }
}
