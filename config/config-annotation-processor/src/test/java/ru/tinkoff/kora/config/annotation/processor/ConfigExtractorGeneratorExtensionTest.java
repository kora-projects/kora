package ru.tinkoff.kora.config.annotation.processor;


import com.typesafe.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.config.annotation.processor.cases.*;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigRootAnnotationProcessor;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigSourceAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigExtractorGeneratorExtensionTest {

    @Test
    void extensionTest() throws Exception {
        var graphDraw = createGraphDraw(AppWithConfig.class);
        var graph = graphDraw.init().block();
        var values = graphDraw.getNodes()
            .stream()
            .map(n -> graph.get(n))
            .collect(Collectors.toList());

        var props = new Properties();
        props.put("foo.bar.baz1", 1);
        props.put("foo.bar.baz2", true);
        props.put("foo.bar.baz3", "asd");
        props.put("foo.bar.baz4", List.of(1, false, "zxc"));

        var pojoConfig = values.stream().filter(PojoConfig.class::isInstance).map(PojoConfig.class::cast).findFirst().orElseThrow();
        Assertions.assertEquals(pojoConfig, new PojoConfig(
            1,
            2,
            3L,
            4L,
            5.0,
            6.0,
            true,
            false,
            "some string value",
            List.of(1, 2, 3, 4, 5),
            new SomeConfig(1, "baz"),
            props
        ));

        var recordConfig = values.stream().filter(RecordConfig.class::isInstance).map(RecordConfig.class::cast).findFirst().orElseThrow();
        Assertions.assertEquals(recordConfig, new RecordConfig(
            1,
            2,
            3L,
            4L,
            5.0,
            6.0,
            true,
            false,
            "some string value",
            List.of(1, 2, 3, 4, 5),
            new SomeConfig(1, "baz"),
            props
        ));
    }

    @Test
    void extensionTestWithComponentOf() throws Exception {
        var graphDraw = createGraphDraw(AppWithConfigWithModule.class, PojoConfigRootWithComponentOf.class);
        var graph = graphDraw.init().block();
        var values = graphDraw.getNodes()
            .stream()
            .map(n -> graph.get(n))
            .collect(Collectors.toList());

        assertThat(values).hasSize(12);
        assertThat(values.stream().anyMatch(o -> o instanceof MockLifecycle)).isTrue();
        assertThat(values.stream().anyMatch(o -> o instanceof PojoConfig)).isTrue();
        assertThat(values.stream().anyMatch(o -> o instanceof RecordConfig)).isTrue();
    }

    @Test
    void appWithConfigSource() throws Exception {
        var graphDraw = createGraphDraw(AppWithConfigSource.class);
        var graph = graphDraw.init().block();
        var values = graphDraw.getNodes()
            .stream()
            .map(n -> graph.get(n))
            .collect(Collectors.toList());

        assertThat(values).hasSize(4);
        assertThat(values.stream().anyMatch(o -> o instanceof MockLifecycle)).isTrue();
        assertThat(values.stream().anyMatch(o -> o instanceof Wrapped<?> wrapped && wrapped.value() instanceof Config)).isTrue();

        var config = values.stream()
            .filter(o -> o instanceof AppWithConfigSource.SomeConfig)
            .map(AppWithConfigSource.SomeConfig.class::cast)
            .findFirst();
        assertThat(config)
            .isNotEmpty()
            .hasValue(new AppWithConfigSource.SomeConfig("field", 42));

    }

    ApplicationGraphDraw createGraphDraw(Class<?>... targetClasses) throws Exception {
        try {
            var classLoader = TestUtils.annotationProcess(List.of(targetClasses), new KoraAppProcessor(), new ConfigRootAnnotationProcessor(), new ConfigSourceAnnotationProcessor());
            var targetClass = Arrays.stream(targetClasses)
                .filter(c -> c.getSimpleName().contains("App"))
                .findFirst()
                .get();
            var clazz = classLoader.loadClass(targetClass.getName() + "Graph");
            @SuppressWarnings("unchecked")
            var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
            return constructors[0].newInstance().get();
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }
}
