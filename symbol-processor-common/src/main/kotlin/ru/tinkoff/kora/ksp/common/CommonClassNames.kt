package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.*
import java.util.concurrent.*

object CommonClassNames {
    val nullable = ClassName("jakarta.annotation", "Nullable")
    val publisher = ClassName("org.reactivestreams", "Publisher")
    val mono = ClassName("reactor.core.publisher", "Mono")
    val deferred = ClassName("kotlinx.coroutines", "Deferred")
    val flux = ClassName("reactor.core.publisher", "Flux")
    val flow = ClassName("kotlinx.coroutines.flow", "Flow")
    val list = List::class.asClassName()
    val future = Future::class.asClassName()
    val completableFuture = CompletableFuture::class.asClassName()
    val synchronousSink = ClassName("reactor.core.publisher", "SynchronousSink")
    val await = MemberName("kotlinx.coroutines.future", "await")
    val flowBuilder = MemberName("kotlinx.coroutines.flow", "flow")

    val context = ClassName("ru.tinkoff.kora.common", "Context")
    val contextReactor = ClassName("ru.tinkoff.kora.common", "Context", "Reactor")
    val aopAnnotation = ClassName("ru.tinkoff.kora.common", "AopAnnotation")
    val aopProxy = ClassName("ru.tinkoff.kora.common", "AopProxy")
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
    val root = ClassName("ru.tinkoff.kora.common.annotation", "Root")

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

    val generated = ClassName("ru.tinkoff.kora.common.annotation", "Generated")

    val config = ClassName("ru.tinkoff.kora.config.common", "Config")
    val configValueExtractionException = ClassName("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractionException")
    val configValue = ClassName("ru.tinkoff.kora.config.common", "ConfigValue")
    val configValueExtractor = ClassName("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractor")
    val configValueExtractorAnnotation = ClassName("ru.tinkoff.kora.config.common.annotation", "ConfigValueExtractor");

    val isNotEmpty = MemberName("kotlin.collections", "isNotEmpty")

    val telemetryConfig = ClassName("ru.tinkoff.kora.telemetry.common", "TelemetryConfig")
    val meterRegistry = ClassName("io.micrometer.core.instrument", "MeterRegistry")

    fun KSType.isList(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == List::class.qualifiedName
            || className == MutableList::class.qualifiedName
            || className == ArrayList::class.qualifiedName
            || className == LinkedList::class.qualifiedName
    }

    fun KSType.isSet(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == Set::class.qualifiedName
            || className == MutableSet::class.qualifiedName
            || className == HashSet::class.qualifiedName
            || className == TreeSet::class.qualifiedName
            || className == SortedSet::class.qualifiedName
            || className == LinkedHashSet::class.qualifiedName
            || className == CopyOnWriteArraySet::class.qualifiedName
            || className == ConcurrentSkipListSet::class.qualifiedName
    }

    fun KSType.isQueue(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == Queue::class.qualifiedName
            || className == Deque::class.qualifiedName
    }

    fun KSType.isCollection(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == Collection::class.qualifiedName
            || className == MutableCollection::class.qualifiedName
            || isList()
            || isSet()
            || isQueue()
    }

    fun KSType.isMap(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == Map::class.qualifiedName
            || className == MutableMap::class.qualifiedName
            || className == HashMap::class.qualifiedName
            || className == TreeMap::class.qualifiedName
            || className == ConcurrentMap::class.qualifiedName
            || className == ConcurrentHashMap::class.qualifiedName
            || className == LinkedHashMap::class.qualifiedName
            || className == SortedMap::class.qualifiedName
            || className == NavigableMap::class.qualifiedName
            || className == ConcurrentSkipListMap::class.qualifiedName
            || className == IdentityHashMap::class.qualifiedName
            || className == WeakHashMap::class.qualifiedName
            || className == EnumMap::class.qualifiedName
    }

    fun KSType.isIterable(): Boolean {
        val className = this.declaration.let {
            if (it is KSClassDeclaration)
                it.toClassName().canonicalName
            else
                this.toString()
        }
        return className == Iterable::class.qualifiedName
            || className == MutableIterable::class.qualifiedName
            || isCollection()
            || isMap()
    }

    fun KSType.isMono() = declaration is KSClassDeclaration && declaration.qualifiedName!!.asString() == mono.canonicalName
    fun KSType.isFlux() = declaration is KSClassDeclaration && declaration.qualifiedName!!.asString() == flux.canonicalName
    fun KSType.isFlow() = declaration is KSClassDeclaration && declaration.qualifiedName!!.asString() == flow.canonicalName
    fun KSType.isPublisher() = declaration is KSClassDeclaration && declaration.qualifiedName!!.asString() == publisher.canonicalName
    fun KSType.isFuture() = declaration is KSClassDeclaration && (declaration.qualifiedName!!.asString() == future.canonicalName)
    fun KSType.isDeferred() = declaration is KSClassDeclaration && declaration.qualifiedName!!.asString() == deferred.canonicalName

    fun KSType.isCompletionStage(): Boolean {
        declaration.let {
            if (it !is KSClassDeclaration) {
                return false
            }
            val className = it.toClassName()
            return className.canonicalName == CompletionStage::class.qualifiedName
                || className.canonicalName == CompletableFuture::class.qualifiedName
        }
    }

    fun KSTypeReference.isVoid(): Boolean {
        val typeAsStr = resolve().declaration.let{ if (it is KSClassDeclaration) it.toClassName() else null}?.canonicalName
        return Void::class.qualifiedName == typeAsStr || "void" == typeAsStr || Unit::class.qualifiedName == typeAsStr
    }

    fun KSTypeReference.isDeferred(): Boolean {
        val typeAsStr = resolve().declaration.let{ if (it is KSClassDeclaration) it.toClassName() else null}?.canonicalName
        return typeAsStr == deferred.canonicalName
    }
}
