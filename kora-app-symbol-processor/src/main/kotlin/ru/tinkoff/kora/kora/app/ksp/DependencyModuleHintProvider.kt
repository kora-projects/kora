package ru.tinkoff.kora.kora.app.ksp

import com.fasterxml.jackson.core.*
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.regex.Pattern

class DependencyModuleHintProvider {
    private val log = LoggerFactory.getLogger(this::class.java)

    private var hints: List<KoraHint> = mutableListOf()

    init {
        try {
            DependencyModuleHintProvider::class.java.getResourceAsStream("/kora-hints.json").use { r ->
                JsonFactory(
                    JsonFactoryBuilder().disable(JsonFactory.Feature.INTERN_FIELD_NAMES)
                ).createParser(r).use { parser -> hints = KoraHint.parseList(parser) }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    interface Hint {

        fun message(): String

        data class ModuleHint(
            val type: KSType,
            val tags: Set<String>,
            val module: String,
            val artifact: String
        ) : Hint {
            override fun message(): String {
                if (tags.isEmpty()) {
                    return """
                        Missing component: ${type.toTypeName()}
                            Component is provided by standard Kora module you may forgot to plug it:
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
                            Component is provided by standard Kora module you may forgot to plug it:
                                Gradle dependency:  implementation("$artifact")
                                Module interface:  $module
                                """.trimIndent()
                }
            }
        }

        data class TipHint(
            val type: KSType,
            val tags: Set<String>,
            val tip: String
        ) : Hint {
            override fun message(): String = tip
        }
    }

    fun findHints(missingType: KSType, missingTag: Set<String>): List<Hint> {
        log.trace("Checking hints for {}/{}", missingTag, missingType)
        val result = mutableListOf<Hint>()
        for (hint in hints) {
            val matcher = hint.typeRegex.matcher(missingType.toTypeName().toString())
            if (matcher.matches()) {
                if (tagMatches(missingTag, hint.tags)) {
                    log.trace("Hint {} matched!", hint)
                    when (hint) {
                        is KoraHint.KoraModuleHint -> result.add(Hint.ModuleHint(missingType, hint.tags, hint.artifact, hint.moduleName))
                        is KoraHint.KoraTipHint -> result.add(Hint.TipHint(missingType, hint.tags, hint.tip))
                        else -> throw UnsupportedOperationException("Unknown hint type: $hint")
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

    interface KoraHint {

        val typeRegex: Pattern

        val tags: Set<String>

        data class KoraModuleHint(
            override val typeRegex: Pattern,
            override val tags: Set<String>,
            val moduleName: String,
            val artifact: String
        ) : KoraHint

        data class KoraTipHint(
            override val typeRegex: Pattern,
            override val tags: Set<String>,
            val tip: String
        ) : KoraHint

        companion object {

            @Throws(IOException::class)
            internal fun parseList(p: JsonParser): List<KoraHint> {
                var token = p.nextToken()
                if (token != JsonToken.START_ARRAY) {
                    throw JsonParseException(p, "Expecting START_ARRAY token, got $token")
                }
                token = p.nextToken()
                if (token == JsonToken.END_ARRAY) {
                    return listOf()
                }
                val result = ArrayList<KoraHint>(16)
                while (token != JsonToken.END_ARRAY) {
                    val element = parse(p)
                    result.add(element)
                    token = p.nextToken()
                }
                return result
            }

            @Throws(IOException::class)
            internal fun parse(p: JsonParser): KoraHint {
                assert(p.currentToken() == JsonToken.START_OBJECT)
                var next = p.nextToken()
                var typeRegex: String? = null
                val tags: MutableSet<String> = HashSet()
                var moduleName: String? = null
                var artifact: String? = null
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

                if (typeRegex == null) {
                    throw JsonParseException(p, "Some required fields missing: typeRegex=$typeRegex")
                } else if (!(moduleName != null && artifact != null || tip != null)) {
                    throw JsonParseException(p, "Some required fields missing: moduleName=$moduleName, artifact=$artifact, tip=$tip")
                }

                return if (tip != null) {
                    KoraTipHint(Pattern.compile(typeRegex.trim()), tags, tip)
                } else {
                    KoraModuleHint(Pattern.compile(typeRegex.trim()), tags, moduleName!!, artifact!!)
                }
            }
        }
    }
}
