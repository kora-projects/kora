package ru.tinkoff.kora.validation.common;

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
}
