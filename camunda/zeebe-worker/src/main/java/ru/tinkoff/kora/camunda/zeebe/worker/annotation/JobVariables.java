package ru.tinkoff.kora.camunda.zeebe.worker.annotation;

import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface JobVariables {

}
