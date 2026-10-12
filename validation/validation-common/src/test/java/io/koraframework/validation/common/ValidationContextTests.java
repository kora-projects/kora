package io.koraframework.validation.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ValidationContextTests extends Assertions {

    @Test
    void stringPathAddedValid() {
        // given
        var context = ValidationContext.builder().build();

        // when
        context = context.addPath("field1").addPath("field2");

        // then
        assertEquals("field2", context.path().value());
        assertEquals("field1.field2", context.path().full());
    }

    @Test
    void indexPathAddedValid() {
        // given
        var context = ValidationContext.builder().build();

        // when
        context = context.addPath("field1").addPath(1).addPath("field2");

        // then
        assertEquals("field2", context.path().value());
        assertEquals("field1.[1].field2", context.path().full());
    }

    @Test
    void indexPathAddedToRootValid() {
        // given
        var context = ValidationContext.builder().build();

        // when
        var root = context.addPath(0).addPath("name");
        var nested = context.addPath("items").addPath(0).addPath("name");

        // then
        assertEquals("[0]", context.addPath(0).path().full());
        assertEquals("[0].name", root.path().full());
        assertEquals("items.[0].name", nested.path().full());
    }
}
