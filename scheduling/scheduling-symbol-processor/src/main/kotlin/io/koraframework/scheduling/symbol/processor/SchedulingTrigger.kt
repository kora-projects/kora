package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotation

data class SchedulingTrigger(val schedulerType: SchedulerType, val annotation: KSAnnotation)
