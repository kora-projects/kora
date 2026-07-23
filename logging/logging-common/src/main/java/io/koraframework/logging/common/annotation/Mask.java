package io.koraframework.logging.common.annotation;

import io.koraframework.logging.common.masking.MaskingFull;
import io.koraframework.logging.common.masking.MaskingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mask {

    Class<? extends MaskingStrategy> value() default MaskingFull.class;
}
