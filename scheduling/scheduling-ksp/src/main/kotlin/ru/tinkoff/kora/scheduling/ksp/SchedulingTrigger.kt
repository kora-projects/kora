package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.symbol.KSAnnotation

data class SchedulingTrigger(val schedulerType: SchedulerType, val annotation: KSAnnotation)
