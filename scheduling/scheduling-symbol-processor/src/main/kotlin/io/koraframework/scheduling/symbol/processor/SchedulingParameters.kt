package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.TagUtils.addTag
import java.time.ZoneId

private val schedulingModuleClassName = ClassName("io.koraframework.scheduling.common", "SchedulingModule")

/**
 * Optional time zone of CRON jobs: a [ZoneId] component tagged with `SchedulingModule`, the JVM default time zone when absent.
 */
internal fun zoneIdParameter(): ParameterSpec = ParameterSpec.builder("zoneId", ZoneId::class.asClassName().copy(nullable = true))
    .addTag(schedulingModuleClassName)
    .build()

/**
 * The job component of a `@Conditional` component exists under the same condition, otherwise it is scheduled
 * and fails on every execution, or fails the graph, when the condition is not met.
 */
internal fun conditionalOf(type: KSClassDeclaration): List<AnnotationSpec> = listOfNotNull(
    type.findAnnotation(CommonClassNames.conditional)?.toAnnotationSpec()
)
