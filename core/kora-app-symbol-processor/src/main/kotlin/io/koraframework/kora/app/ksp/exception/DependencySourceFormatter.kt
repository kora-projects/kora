package io.koraframework.kora.app.ksp.exception

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration

/**
 * Formats dependency sources for error messages: annotations are placed before the type,
 * the requested parameter is printed with canonical names and all other parameters with simple names.
 */
object DependencySourceFormatter {

    fun requiredAt(declaration: ComponentDeclaration, claimSource: KSAnnotated?): String {
        val function = findFunction(claimSource) ?: findFunction(declaration.source)
        if (function == null || function.parentDeclaration !is KSClassDeclaration) {
            return declaration.declarationString()
        }
        return signature(function, claimSource)
    }

    /**
     * "Required at" section for a dependency claim of the component: its signature and the requested parameter.
     * Starts with blank line so it can be appended right after the previous section.
     */
    fun requiredAtSection(declaration: ComponentDeclaration, claimSource: KSAnnotated?): String {
        var msg = "\n\nRequired at:\n  " + requiredAt(declaration, claimSource)
        if (claimSource is KSValueParameter) {
            msg += "\n  parameter: " + parameter(claimSource)
        }
        return msg
    }

    /**
     * Section describing where the erroneous element is: "Required at" with the parameter for a parameter,
     * "Declared at" with the signature for a function or constructor, empty otherwise.
     * Starts with blank line so it can be appended right after the previous section.
     */
    fun locationSection(source: KSNode?): String {
        if (source is KSValueParameter) {
            val function = source.parent as? KSFunctionDeclaration
            if (function != null && function.parentDeclaration is KSClassDeclaration) {
                return "\n\nRequired at:\n  " + signature(function, source) + "\n  parameter: " + parameter(source)
            }
        }
        if (source is KSFunctionDeclaration && source.parentDeclaration is KSClassDeclaration) {
            return "\n\nDeclared at:\n  " + signature(source, null)
        }
        return ""
    }

    fun signature(function: KSFunctionDeclaration, requestedParameter: KSNode?): String {
        // signature is printed as section body indented by 2 spaces, so parameters go one level deeper
        val params = if (function.parameters.isEmpty()) {
            "()"
        } else {
            function.parameters.joinToString(",\n    ", "(\n    ", ")") { parameterType(it, it == requestedParameter) }
        }
        return functionName(function) + params
    }

    /**
     * Found public constructors as "found: ..." line value, each constructor on its own line.
     */
    fun constructors(constructors: List<KSFunctionDeclaration>): String {
        if (constructors.isEmpty()) {
            return "no public constructors"
        }
        return constructors.joinToString("", "${constructors.size} public constructors:") { "\n    - " + compactSignature(it) }
    }

    /**
     * Single line signature with simple type names, for listing several functions.
     */
    fun compactSignature(function: KSFunctionDeclaration): String {
        return functionName(function) + function.parameters.joinToString(", ", "(", ")") { parameterType(it, false) }
    }

    fun parameter(parameter: KSValueParameter): String {
        return parameterType(parameter, true) + " " + (parameter.name?.asString() ?: "<unnamed>")
    }

    /**
     * Tag as it is written in code: `@Pg` for tag annotations and `@Tag(Some::class)` for tag classes, with canonical names.
     */
    fun tag(resolver: Resolver, tag: String): String {
        val declaration = resolver.getClassDeclarationByName(tag)
        if (declaration != null && declaration.classKind == ClassKind.ANNOTATION_CLASS) {
            return "@$tag"
        }
        return "@Tag($tag::class)"
    }

    /**
     * Tag suffix for a component or dependency type: " (no tags)" or " with @Tag(...)".
     */
    fun tagSuffix(resolver: Resolver, tag: String?): String {
        return if (tag == null) " (no tags)" else " with " + tag(resolver, tag)
    }

    /**
     * Type with canonical names and without type annotations noise.
     */
    fun type(type: KSType): String {
        return typeName(type, true)
    }

    private fun functionName(function: KSFunctionDeclaration): String {
        val owner = function.parentDeclaration
        val ownerName = owner?.qualifiedName?.asString() ?: owner?.simpleName?.asString() ?: function.packageName.asString()
        return if (function.isConstructor()) {
            ownerName
        } else {
            ownerName + "#" + function.simpleName.asString()
        }
    }

    private fun findFunction(element: KSNode?): KSFunctionDeclaration? {
        var e = element
        while (e != null) {
            if (e is KSFunctionDeclaration) {
                return e
            }
            if (e is KSClassDeclaration) {
                return null
            }
            e = e.parent
        }
        return null
    }

    private fun parameterType(parameter: KSValueParameter, canonical: Boolean): String {
        // annotation can be declared both on parameter and on its type
        val annotations = (parameter.annotations + parameter.type.annotations)
            .distinctBy { it.annotationType.resolve().declaration.qualifiedName?.asString() ?: it.shortName.asString() }
            .toList()
        return annotationsPrefix(annotations) + typeName(parameter.type.resolve(), canonical)
    }

    private fun type(type: KSType, canonical: Boolean): String {
        return annotationsPrefix(type.annotations.toList()) + typeName(type, canonical)
    }

    private fun typeName(type: KSType, canonical: Boolean): String {
        if (type.isError) {
            return type.toString()
        }
        val declaration = type.declaration
        val name = if (declaration is KSTypeParameter) {
            declaration.name.asString()
        } else {
            declarationName(declaration, canonical)
        }
        val args = if (type.arguments.isEmpty()) {
            ""
        } else {
            type.arguments.joinToString(", ", "<", ">") { typeArgument(it, canonical) }
        }
        return name + args + if (type.isMarkedNullable) "?" else ""
    }

    private fun typeArgument(argument: KSTypeArgument, canonical: Boolean): String {
        val argumentType = argument.type?.resolve()
        return when {
            argument.variance == Variance.STAR || argumentType == null -> "*"
            argument.variance == Variance.COVARIANT -> "out " + type(argumentType, canonical)
            argument.variance == Variance.CONTRAVARIANT -> "in " + type(argumentType, canonical)
            else -> type(argumentType, canonical)
        }
    }

    private fun declarationName(declaration: KSDeclaration, canonical: Boolean): String {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return declaration.simpleName.asString()
        if (canonical) {
            return qualifiedName
        }
        val packageName = declaration.packageName.asString()
        return if (packageName.isEmpty()) qualifiedName else qualifiedName.removePrefix("$packageName.")
    }

    private fun annotationsPrefix(annotations: List<KSAnnotation>): String {
        return annotations.joinToString("") { annotation(it) + " " }
    }

    private fun annotation(annotation: KSAnnotation): String {
        val name = "@" + declarationName(annotation.annotationType.resolve().declaration, false)
        val defaults = annotation.defaultArguments.associate { it.name?.asString() to it.value }
        val arguments = annotation.arguments.filter { arg ->
            val argName = arg.name?.asString()
            !defaults.containsKey(argName) || defaults[argName] != arg.value
        }
        if (arguments.isEmpty()) {
            return name
        }
        if (arguments.size == 1 && arguments[0].name?.asString() == "value") {
            return name + "(" + annotationValue(arguments[0].value) + ")"
        }
        return arguments.joinToString(", ", "$name(", ")") { it.name?.asString() + " = " + annotationValue(it.value) }
    }

    private fun annotationValue(value: Any?): String = when (value) {
        is KSType -> {
            val declaration = value.declaration
            if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_ENTRY) {
                declarationName(declaration, false)
            } else {
                declarationName(declaration, false) + "::class"
            }
        }
        is KSClassDeclaration -> declarationName(value, false)
        is KSAnnotation -> annotation(value)
        is List<*> -> if (value.size == 1) annotationValue(value[0]) else value.joinToString(", ", "[", "]") { annotationValue(it) }
        is String -> "\"" + value + "\""
        else -> value.toString()
    }
}
