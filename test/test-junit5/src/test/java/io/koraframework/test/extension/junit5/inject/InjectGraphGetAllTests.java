package io.koraframework.test.extension.junit5.inject;

import io.koraframework.common.annotation.Tag;
import io.koraframework.test.extension.junit5.KoraAppGraph;
import io.koraframework.test.extension.junit5.KoraAppTest;
import io.koraframework.test.extension.junit5.testdata.TestApplication;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KoraAppTest(TestApplication.class)
public class InjectGraphGetAllTests {

    private static final List<String> TAGGED_STRINGS = List.of("holder-1-1", "wrapped-1", "other-1");

    @Test
    void getAllWithoutTagReturnsComponentsWithAnyTag(KoraAppGraph graph) {
        assertThat(graph.getAll(String.class)).containsExactlyInAnyOrderElementsOf(TAGGED_STRINGS);
    }

    @Test
    void getAllWithAnyTagReturnsComponentsWithAnyTag(KoraAppGraph graph) {
        assertThat(graph.getAll(String.class, Tag.Any.class)).containsExactlyInAnyOrderElementsOf(TAGGED_STRINGS);
    }
}
