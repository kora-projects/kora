package ru.tinkoff.kora.scheduling.annotation.processor;

import javax.lang.model.element.AnnotationMirror;

public record SchedulingTrigger(SchedulerType schedulerType, AnnotationMirror triggerAnnotation) {}
