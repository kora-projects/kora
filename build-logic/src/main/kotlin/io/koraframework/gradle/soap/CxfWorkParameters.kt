package io.koraframework.gradle.soap

import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkParameters

interface CxfWorkParameters : WorkParameters {
    val args: ListProperty<String>
}
