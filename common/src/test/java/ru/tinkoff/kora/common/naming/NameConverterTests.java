package ru.tinkoff.kora.common.naming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

final class NameConverterTests {

    static Stream<Arguments> data() {
        return Stream.of(
            Arguments.of(new NoopNameConverter(), "My Foo Bar", "My Foo Bar"),
            Arguments.of(new NoopNameConverter(), "my foo bar", "my foo bar"),
            Arguments.of(new NoopNameConverter(), "MyFooBar", "MyFooBar"),
            Arguments.of(new NoopNameConverter(), "myfoobar", "myfoobar"),
            Arguments.of(new SnakeCaseNameConverter(), "My Foo Bar", "my_foo_bar"),
            Arguments.of(new SnakeCaseNameConverter(), "my foo bar", "my_foo_bar"),
            Arguments.of(new SnakeCaseNameConverter(), "MyFooBar", "my_foo_bar"),
            Arguments.of(new SnakeCaseNameConverter(), "myfoobar", "myfoobar"),
            Arguments.of(new SnakeCaseUpperNameConverter(), "My Foo Bar", "MY_FOO_BAR"),
            Arguments.of(new SnakeCaseUpperNameConverter(), "my foo bar", "MY_FOO_BAR"),
            Arguments.of(new SnakeCaseUpperNameConverter(), "MyFooBar", "MY_FOO_BAR"),
            Arguments.of(new SnakeCaseUpperNameConverter(), "myfoobar", "MYFOOBAR"),
            Arguments.of(new PascalCaseNameConverter(), "My Foo Bar", "MyFooBar"),
            Arguments.of(new PascalCaseNameConverter(), "my foo bar", "MyFooBar"),
            Arguments.of(new PascalCaseNameConverter(), "MyFooBar", "MyFooBar"),
            Arguments.of(new PascalCaseNameConverter(), "myfoobar", "Myfoobar"),
            Arguments.of(new CamelCaseNameConverter(), "My Foo Bar", "myFooBar"),
            Arguments.of(new CamelCaseNameConverter(), "my foo bar", "myFooBar"),
            Arguments.of(new CamelCaseNameConverter(), "MyFooBar", "myFooBar"),
            Arguments.of(new CamelCaseNameConverter(), "myfoobar", "myfoobar")
        );
    }

    @MethodSource("data")
    @ParameterizedTest
    void testConversion(NameConverter converter, String input, String expected) {
        Assertions.assertEquals(expected, converter.convert(input));
    }
}
