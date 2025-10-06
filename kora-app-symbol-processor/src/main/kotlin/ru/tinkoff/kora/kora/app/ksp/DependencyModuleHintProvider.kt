package ru.tinkoff.kora.kora.app.ksp

import com.fasterxml.jackson.core.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.regex.Pattern

class DependencyModuleHintProvider(private val resolver: Resolver) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private var hints: List<ModuleHint> = mutableListOf()
    private var tips: List<ManualTip> = mutableListOf()

    init {
        try {
            DependencyModuleHintProvider::class.java.getResourceAsStream("/kora-modules.json").use { r ->
                JsonFactory(
                    JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)
                ).createParser(r).use { parser -> hints = ModuleHint.parseList(parser) }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        try {
            DependencyModuleHintProvider::class.java.getResourceAsStream("/kora-tips.json").use { r ->
                JsonFactory(
                    JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)
                ).createParser(r).use { parser -> tips = ManualTip.parseList(parser) }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    inner class Hint(
        val type: KSType, val artifact: String, val module: String, val tags: Set<String>
    ) {
        fun message(): String {
            if (tags.isEmpty()) {
                return """
                        Missing component: ${type.toTypeName()}
                            Component can be provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("$artifact")
                                Module interface:  $module
                                """.trimIndent()
            } else {
                val tagForMsg = if (this.tags == setOf("ru.tinkoff.kora.json.common.annotation.Json")) {
                    "@ru.tinkoff.kora.json.common.annotation.Json"
                } else if (this.tags.size == 1) {
                    "@Tag(${this.tags.iterator().next() + ".class"})"
                } else {
                    tags.joinToString(", ", "@Tag({", "})") { "$it.class" }
                }

                return """
                        Missing component: ${type.toTypeName()} with $tagForMsg
                            Component can be provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("$artifact")
                                Module interface:  $module
                                """.trimIndent()
            }
        }
    }

    inner class Tip(
        val type: KSType, val tags: Set<String>, val tip: String
    ) {
        fun message(): String = tip
    }

    fun findHints(missingType: KSType, missingTag: Set<String>): List<Hint> {
        log.trace("Checking hints for {}/{}", missingTag, missingType)
        val result = mutableListOf<Hint>()
        for (hint in hints) {
            val matcher = hint.typeRegex.matcher(missingType.toTypeName().toString())
            if (matcher.matches()) {
                if (this.tagMatches(missingTag, hint.tags)) {
                    log.trace("Hint {} matched!", hint)
                    if (hint.tags.isEmpty()) {
                        result.add(Hint(missingType, hint.artifact, hint.moduleName, setOf()))
                    } else {
                        result.add(Hint(missingType, hint.artifact, hint.moduleName, hint.tags))
                    }
                } else {
                    log.trace("Hint {} doesn't match because of tag", hint)
                }
            } else {
                log.trace("Hint {} doesn't match because of regex", hint)
            }
        }
        return result
    }

    fun findTips(missingType: KSType, missingTag: Set<String>): List<Tip> {
        log.trace("Checking tips for {}/{}", missingTag, missingType)
        val result = mutableListOf<Tip>()
        for (tip in tips) {
            val matcher = tip.typeRegex.matcher(missingType.toTypeName().toString())
            if (matcher.matches()) {
                if (this.tagMatches(missingTag, tip.tags)) {
                    log.trace("Tip {} matched!", tip)
                    if (tip.tags.isEmpty()) {
                        result.add(Tip(missingType, setOf(), tip.tip))
                    } else {
                        result.add(Tip(missingType, tip.tags, tip.tip))
                    }
                } else {
                    log.trace("Tip {} doesn't match because of tag", tip)
                }
            } else {
                log.trace("Tip {} doesn't match because of regex", tip)
            }
        }
        return result
    }

    private fun tagMatches(missingTags: Set<String>, hintTags: Set<String>): Boolean {
        if (missingTags.isEmpty() && hintTags.isEmpty()) {
            return true
        }

        if (missingTags.size != hintTags.size) {
            return false
        }

        for (missingTag in missingTags) {
            if (!hintTags.contains(missingTag)) {
                return false
            }
        }
        return true
    }

    internal data class ModuleHint(
        val tags: Set<String>,
        val typeRegex: Pattern,
        val moduleName: String,
        val artifact: String
    ) {

        companion object {
            @Throws(IOException::class)
            internal fun parseList(p: JsonParser): List<ModuleHint> {
                var token = p.nextToken()
                if (token != JsonToken.START_ARRAY) {
                    throw JsonParseException(p, "Expecting START_ARRAY token, got $token")
                }
                token = p.nextToken()
                if (token == JsonToken.END_ARRAY) {
                    return java.util.List.of()
                }
                val result = ArrayList<ModuleHint>(16)
                while (token != JsonToken.END_ARRAY) {
                    val element = parse(p)
                    result.add(element)
                    token = p.nextToken()
                }
                return result
            }

            @Throws(IOException::class)
            internal fun parse(p: JsonParser): ModuleHint {
                assert(p.currentToken() == JsonToken.START_OBJECT)
                var next = p.nextToken()
                var typeRegex: String? = null
                val tags: MutableSet<String> = HashSet()
                var moduleName: String? = null
                var artifact: String? = null
                while (next != JsonToken.END_OBJECT) {
                    if (next != JsonToken.FIELD_NAME) {
                        throw JsonParseException(p, "expected FIELD_NAME, got $next")
                    }
                    val name = p.currentName()
                    when (name) {
                        "tags" -> {
                            if (p.nextToken() != JsonToken.START_ARRAY) {
                                throw JsonParseException(p, "expected START_ARRAY, got $next")
                            }
                            next = p.nextToken()
                            while (next != JsonToken.END_ARRAY) {
                                if (next != JsonToken.VALUE_STRING) {
                                    throw JsonParseException(p, "expected VALUE_STRING, got $next")
                                }
                                tags.add(p.valueAsString)
                                next = p.nextToken()
                            }
                        }

                        "typeRegex" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw JsonParseException(p, "expected VALUE_STRING, got $next")
                            }
                            typeRegex = p.valueAsString
                        }

                        "moduleName" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw JsonParseException(p, "expected VALUE_STRING, got $next")
                            }
                            moduleName = p.valueAsString
                        }

                        "artifact" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw JsonParseException(p, "expected VALUE_STRING, got $next")
                            }
                            artifact = p.valueAsString
                        }

                        else -> {
                            p.nextToken()
                            p.skipChildren()
                        }
                    }
                    next = p.nextToken()
                }
                if (typeRegex == null || moduleName == null || artifact == null) {
                    throw JsonParseException(p, "Some required fields missing")
                }
                return ModuleHint(tags, Pattern.compile(typeRegex.trim()), moduleName, artifact)
            }

            private val log = LoggerFactory.getLogger(DependencyModuleHintProvider::class.java)
        }
    }

    internal data class ManualTip(val tags: Set<String>, val typeRegex: Pattern, val tip: String) {

        companion object {

            @Throws(IOException::class)
            fun parseList(p: JsonParser): List<ManualTip> {
                var token = p.nextToken()
                if (token != JsonToken.START_ARRAY) {
                    throw JsonParseException(p, "Expecting START_ARRAY token, got $token")
                }
                token = p.nextToken()
                if (token == JsonToken.END_ARRAY) {
                    return listOf()
                }
                val result = java.util.ArrayList<ManualTip>(16)
                while (token != JsonToken.END_ARRAY) {
                    val element = parse(p)
                    result.add(element)
                    token = p.nextToken()
                }
                return result
            }

            @Throws(IOException::class)
            fun parse(p: JsonParser): ManualTip {
                assert(p.currentToken() == JsonToken.START_OBJECT)
                var next = p.nextToken()
                var typeRegex: String? = null
                val tags: MutableSet<String> = java.util.HashSet()
                var tip: String? = null
                while (next != JsonToken.END_OBJECT) {
                    if (next != JsonToken.FIELD_NAME) {
                        throw JsonParseException(p, "expected FIELD_NAME, got $next")
                    }
                    val name = p.currentName()
                    when (name) {
                        "tags" -> {
                            if (p.nextToken() != JsonToken.START_ARRAY) {
                                throw JsonParseException(p, "expected START_ARRAY, got $next")
                            }
                            next = p.nextToken()
                            while (next != JsonToken.END_ARRAY) {
                                if (next != JsonToken.VALUE_STRING) {
                                    throw JsonParseException(p, "expected VALUE_STRING, got $next")
                                }
                                tags.add(p.valueAsString)
                                next = p.nextToken()
                            }
                        }

                        "typeRegex" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw JsonParseException(p, "expected VALUE_STRING, got $next")
                            }
                            typeRegex = p.valueAsString
                        }

                        "tip" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw JsonParseException(p, "expected VALUE_STRING, got $next")
                            }
                            tip = p.valueAsString
                        }

                        else -> {
                            p.nextToken()
                            p.skipChildren()
                        }
                    }
                    next = p.nextToken()
                }

                if (typeRegex == null || tip == null) {
                    throw JsonParseException(p, "Some required fields missing: typeRegex=$typeRegex, tip=$tip")
                }
                return ManualTip(tags, Pattern.compile(typeRegex), tip)
            }
        }
    }

}
