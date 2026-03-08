package io.koraframework.config.ksp

import com.squareup.kotlinpoet.ClassName
import io.koraframework.ksp.common.CommonClassNames

object ConfigClassNames {
    val config = CommonClassNames.config
    val configSourceAnnotation = ClassName("io.koraframework.config.common.annotation", "ConfigSource")
    val configValueExtractorAnnotation = ClassName("io.koraframework.config.common.annotation", "ConfigValueExtractor")
    val configValue = ClassName("io.koraframework.config.common", "ConfigValue")
    val pathElement = ClassName("io.koraframework.config.common", "PathElement")
    val pathElementKey = pathElement.nestedClass("Key")

    val configValueExtractor = CommonClassNames.configValueExtractor
    val configValueExtractionException = ClassName("io.koraframework.config.common.extractor", "ConfigValueExtractionException")
    val objectValue = ClassName("io.koraframework.config.common", "ConfigValue", "ObjectValue")
}
