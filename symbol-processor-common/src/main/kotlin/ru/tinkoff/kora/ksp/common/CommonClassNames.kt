package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

object CommonClassNames {
    val publisher = ClassName("org.reactivestreams", "Publisher")
    val mono = ClassName("reactor.core.publisher", "Mono")
    val flux = ClassName("reactor.core.publisher", "Flux")
    val flow = ClassName("kotlinx.coroutines.flow", "Flow")
    val list = List::class.asClassName()
    val synchronousSink = ClassName("reactor.core.publisher", "SynchronousSink")

    val aopAnnotation = ClassName("ru.tinkoff.kora.common", "AopAnnotation")
    val mapping = ClassName("ru.tinkoff.kora.common", "Mapping")
    val mappings = ClassName("ru.tinkoff.kora.common", "Mapping", "Mappings")
    val namingStrategy = ClassName("ru.tinkoff.kora.common", "NamingStrategy")
    val tag = ClassName("ru.tinkoff.kora.common", "Tag")
    val tagAny = ClassName("ru.tinkoff.kora.common", "Tag", "Any")
    val nameConverter = ClassName("ru.tinkoff.kora.common.naming", "NameConverter")
    val koraApp = ClassName("ru.tinkoff.kora.common", "KoraApp")
    val koraSubmodule = ClassName("ru.tinkoff.kora.common", "KoraSubmodule")
    val module = ClassName("ru.tinkoff.kora.common", "Module")
    val component = ClassName("ru.tinkoff.kora.common", "Component")
    val defaultComponent = ClassName("ru.tinkoff.kora.common", "DefaultComponent")

    val node = ClassName("ru.tinkoff.kora.application.graph", "Node")
    val lifecycle = ClassName("ru.tinkoff.kora.application.graph", "Lifecycle")
    val all = ClassName("ru.tinkoff.kora.application.graph", "All")
    val typeRef = ClassName("ru.tinkoff.kora.application.graph", "TypeRef")
    val wrapped = ClassName("ru.tinkoff.kora.application.graph", "Wrapped")
    val wrappedUnwrappedValue = ClassName("ru.tinkoff.kora.application.graph", "Wrapped", "UnwrappedValue")
    val promiseOf = ClassName("ru.tinkoff.kora.application.graph", "PromiseOf")
    val valueOf = ClassName("ru.tinkoff.kora.application.graph", "ValueOf")
    val applicationGraphDraw = ClassName("ru.tinkoff.kora.application.graph", "ApplicationGraphDraw")
    val graphInterceptor = ClassName("ru.tinkoff.kora.application.graph", "GraphInterceptor")
    val promisedProxy = ClassName("ru.tinkoff.kora.common", "PromisedProxy")
    val refreshListener = ClassName("ru.tinkoff.kora.application.graph", "RefreshListener")



    fun KSType.isMono() = this.toClassName().canonicalName == mono.canonicalName
    fun KSType.isFlux() = this.toClassName().canonicalName == flux.canonicalName
    fun KSType.isFlow() = this.toClassName().canonicalName == flow.canonicalName
    fun KSType.isPublisher() = this.toClassName().canonicalName == publisher.canonicalName
    fun KSType.isList() = this.toClassName().canonicalName == list.canonicalName

    fun KSType.isFuture(): Boolean {
        val className = this.toClassName()
        return className.canonicalName == Future::class.java.canonicalName
            || className.canonicalName == CompletionStage::class.java.canonicalName
            || className.canonicalName == CompletableFuture::class.java.canonicalName
    }

    fun KSTypeReference.isVoid(): Boolean {
        val typeAsStr = resolve().toClassName().canonicalName
        return Void::class.java.canonicalName == typeAsStr || "void" == typeAsStr
    }
}
