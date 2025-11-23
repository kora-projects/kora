package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import org.slf4j.LoggerFactory
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.exc.StreamReadException
import tools.jackson.core.json.JsonFactoryBuilder
import java.io.IOException
import java.util.regex.Pattern

class DependencyModuleHintProvider(private val resolver: Resolver) {
    private var hints: List<ModuleHint> = mutableListOf()
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        try {
            DependencyModuleHintProvider::class.java.getResourceAsStream("/kora-modules.json").use { r ->
                JsonFactoryBuilder().build().createParser(ObjectReadContext.empty(), r).use { parser -> hints = ModuleHint.parseList(parser) }
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
                return "Missing component of type $type which can be provided by kora module, you may forgot to plug it\nModule class: $module \nArtifact dependency: $artifact\n"
            } else {
                val tagsAsStr = tags.joinToString(", ", "{", "}") { "$it.class" }
                return "Missing component of type $type which can be provided by kora module, you may forgot to plug it\nModule class: $module (required tags: `@Tag($tagsAsStr)`)\nArtifact dependency: $artifact\n"
            }
        }
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
                    throw StreamReadException(p, "Expecting START_ARRAY token, got $token")
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
                    if (next != JsonToken.PROPERTY_NAME) {
                        throw StreamReadException(p, "expected FIELD_NAME, got $next")
                    }
                    val name = p.currentName()
                    when (name) {
                        "tags" -> {
                            if (p.nextToken() != JsonToken.START_ARRAY) {
                                throw StreamReadException(p, "expected START_ARRAY, got $next")
                            }
                            next = p.nextToken()
                            while (next != JsonToken.END_ARRAY) {
                                if (next != JsonToken.VALUE_STRING) {
                                    throw StreamReadException(p, "expected VALUE_STRING, got $next")
                                }
                                tags.add(p.valueAsString)
                                next = p.nextToken()
                            }
                        }

                        "typeRegex" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw StreamReadException(p, "expected VALUE_STRING, got $next")
                            }
                            typeRegex = p.valueAsString
                        }

                        "moduleName" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw StreamReadException(p, "expected VALUE_STRING, got $next")
                            }
                            moduleName = p.valueAsString
                        }

                        "artifact" -> {
                            if (p.nextToken() != JsonToken.VALUE_STRING) {
                                throw StreamReadException(p, "expected VALUE_STRING, got $next")
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
                    throw StreamReadException(p, "Some required fields missing")
                }
                return ModuleHint(tags, Pattern.compile(typeRegex.trim()), moduleName, artifact)
            }

            private val log = LoggerFactory.getLogger(DependencyModuleHintProvider::class.java)
        }
    }
}
