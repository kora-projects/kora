package ru.tinkoff.kora.test.extension.junit5.testdata;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Tag(TestApplication.ComplexOther.class)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ComplexOtherMetaTag {
}
