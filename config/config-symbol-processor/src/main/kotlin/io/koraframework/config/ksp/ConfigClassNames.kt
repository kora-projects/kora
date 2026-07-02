package io.koraframework.config.ksp

import com.squareup.kotlinpoet.ClassName
import io.koraframework.ksp.common.CommonClassNames

object ConfigClassNames {
    val config = CommonClassNames.config
    val configSourceAnnotation = ClassName("io.koraframework.config.common.annotation", "ConfigSource")
    val configValueMapperAnnotation = ClassName("io.koraframework.config.common.annotation", "ConfigMapper")
    val configValue = ClassName("io.koraframework.config.common", "ConfigValue")
    val pathElement = ClassName("io.koraframework.config.common", "PathElement")
    val pathElementKey = pathElement.nestedClass("Key")

    val configValueMapper = CommonClassNames.configValueMapper
    val configValueException = ClassName("io.koraframework.config.common.exception", "ConfigValueException")
    val objectValue = ClassName("io.koraframework.config.common", "ConfigValue", "ObjectValue")
}
