package io.koraframework.openfeature.symbol.processor

import com.squareup.kotlinpoet.ClassName
import io.koraframework.ksp.common.CommonClassNames

object ConfigClassNames {

    val configSourceAnnotation = ClassName("io.koraframework.config.common.annotation", "ConfigSource")
    val configValueExtractor = CommonClassNames.configValueExtractor
}
