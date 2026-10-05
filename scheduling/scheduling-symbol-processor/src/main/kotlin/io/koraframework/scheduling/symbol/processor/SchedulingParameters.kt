package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findEnumValue
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

/**
 * The `unit` of a scheduling annotation: the generated job converts it with `Duration.of`, which rejects estimated units longer than a day.
 */
internal fun durationUnit(annotation: KSAnnotation, function: KSFunctionDeclaration): ClassName {
    val unit = annotation.findEnumValue("unit")!!
    val chronoUnit = ChronoUnit.valueOf(unit.simpleName)
    if (chronoUnit.isDurationEstimated && chronoUnit != ChronoUnit.DAYS) {
        throw ProcessingErrorException("Unit ChronoUnit.${chronoUnit.name} has an estimated duration and can't be used for a scheduled job, use DAYS or a smaller unit", function)
    }
    return unit
}
