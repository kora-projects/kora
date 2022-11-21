package ru.tinkoff.kora.json.annotation.processor.extension;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;
import ru.tinkoff.kora.json.annotation.processor.dto.*;
import ru.tinkoff.kora.json.annotation.processor.extension.module.TestModule;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class JsonKoraExtensionTest {

    @Test
    void test() throws Exception {
        var classLoader = TestUtils.annotationProcess(List.of(
            TestModule.class,
            DtoOnlyReader.class,
            DtoOnlyWriter.class,
            DtoWithInnerDto.class,
            DtoWithJsonFieldWriter.class,
            DtoWithJsonSkip.class,
            DtoWithSupportedTypes.class,
            SealedDto.class,
            AnnotatedSealedDto.class
        ), new KoraAppProcessor(), new JsonAnnotationProcessor());
        var clazz = classLoader.loadClass(TestModule.class.getName() + "Graph");
        @SuppressWarnings("unchecked")
        var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
        assertThat(constructors[0].newInstance().get()).isNotNull();
    }
}
