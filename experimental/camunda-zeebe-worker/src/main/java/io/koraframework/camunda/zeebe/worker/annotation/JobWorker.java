package io.koraframework.camunda.zeebe.worker.annotation;

import io.koraframework.common.AopAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AopAnnotation
public @interface JobWorker {

    /**
     * @return Job Worker Type {@link io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1#jobType(String)}
     */
    String value();
}
