package ru.tinkoff.kora.config.ksp

import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.ksp.common.CommonClassNames

object ConfigClassNames {
    val config = CommonClassNames.config
    val configSourceAnnotation = ClassName("ru.tinkoff.kora.config.common.annotation", "ConfigSource")
    val configValueExtractorAnnotation = ClassName("ru.tinkoff.kora.config.common.annotation", "ConfigValueExtractor")
    val configValue = ClassName("ru.tinkoff.kora.config.common", "ConfigValue")
    val pathElement = ClassName("ru.tinkoff.kora.config.common", "PathElement")
    val pathElementKey = pathElement.nestedClass("Key")

    val configValueExtractor = CommonClassNames.configValueExtractor
    val configValueExtractionException = ClassName("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractionException")
    val objectValue = ClassName("ru.tinkoff.kora.config.common", "ConfigValue", "ObjectValue")
}
