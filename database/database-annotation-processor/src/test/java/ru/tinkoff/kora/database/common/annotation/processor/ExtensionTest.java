package ru.tinkoff.kora.database.common.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.database.annotation.processor.RepositoryAnnotationProcessor;
import ru.tinkoff.kora.database.common.annotation.processor.app.TestKoraApp;
import ru.tinkoff.kora.database.common.annotation.processor.app.TestKoraAppTagged;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtensionTest {

    @Test
    void test() throws Exception {
        var classLoader = TestUtils.annotationProcess(TestKoraApp.class, new KoraAppProcessor(), new RepositoryAnnotationProcessor());
        var clazz = classLoader.loadClass(TestKoraApp.class.getName() + "Graph");
        @SuppressWarnings("unchecked")
        var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
        var graphDraw = constructors[0].newInstance().get();
        assertThat(graphDraw).isNotNull();
        assertThat(graphDraw.size()).isEqualTo(3);
    }

    @Test
    void testTagged() throws Exception {
        var classLoader = TestUtils.annotationProcess(TestKoraAppTagged.class, new RepositoryAnnotationProcessor());
        var clazz = classLoader.loadClass("ru.tinkoff.kora.database.common.annotation.processor.app.$TestKoraAppTagged_TestRepository_Impl");
        var constructors = clazz.getConstructors();
        var parameters = constructors[0].getParameters();
        var connectionFactory = parameters[0];
        var executor = parameters[1];
        assertThat(connectionFactory.isAnnotationPresent(Tag.class)).isTrue();
        var classes = Arrays.asList(connectionFactory.getAnnotation(Tag.class).value());
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0)).isAssignableFrom(TestKoraAppTagged.ExampleTag.class);
        assertThat(executor.isAnnotationPresent(Tag.class)).isTrue();
        classes = Arrays.asList(executor.getAnnotation(Tag.class).value());
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0)).isAssignableFrom(TestKoraAppTagged.ExampleTag.class);
    }
}
