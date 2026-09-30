package io.koraframework.scheduling.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asClassName
import io.koraframework.ksp.common.TagUtils.addTag
import java.time.ZoneId

private val schedulingModuleClassName = ClassName("io.koraframework.scheduling.common", "SchedulingModule")

/**
 * Optional time zone of CRON jobs: a [ZoneId] component tagged with `SchedulingModule`, the JVM default time zone when absent.
 */
internal fun zoneIdParameter(): ParameterSpec = ParameterSpec.builder("zoneId", ZoneId::class.asClassName().copy(nullable = true))
    .addTag(schedulingModuleClassName)
    .build()
