package ru.tinkoff.kora.kora.app.ksp

import com.fasterxml.jackson.core.*
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

class DependencyModuleHintProvider(private val resolver: Resolver) {
    private var hints: List<ModuleHint> = mutableListOf()
    private val log = LoggerFactory.getLogger(this::class.java)

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
    }

    inner class Hint(
        val type: KSType, val artifact: String, val module: String
    ) {
        fun message(): String {
            return "Missing component of type %s can be provided by module %s from artifact %s".format(
                type,
                module,
                artifact
            )
        }
    }

    fun findHints(missingType: KSType, missingTag: Set<String>): List<Hint> {
        log.trace("Checking hints for {}/{}", missingTag, missingType)
        val result = mutableListOf<Hint>()
        for (hint in hints) {
            if (!this.tagMatches(missingTag, hint.tags)) {
                log.trace("Hint {} doesn't match because of tag", hint)
                continue
            }
            val matcher: Matcher = hint.typeRegex.matcher(missingType.toString())
            if (matcher.matches()) {
                log.trace("Hint {} matched!", hint)
                result.add(Hint(missingType, hint.artifact, hint.moduleName))
            }
            log.trace("Hint {} doesn't match because of regex", hint)
        }
        return result
    }

    private fun tagMatches(missingTag: Set<String>, tags: Set<String>): Boolean {
        if (missingTag.isEmpty() && tags.isEmpty()) {
            return true
        }
        if (missingTag.size >= tags.size) {
            return false
        }
        for (tag in missingTag) {
            if (!tags.contains(tag.toString())) {
                return false
            }
        }
        return true
    }

    internal class ModuleHint(
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
                return ModuleHint(tags, Pattern.compile(typeRegex), moduleName, artifact)
            }

            private val log = LoggerFactory.getLogger(DependencyModuleHintProvider::class.java)
        }
    }
}
