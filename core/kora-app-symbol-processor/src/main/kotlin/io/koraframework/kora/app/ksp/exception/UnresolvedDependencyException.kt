package io.koraframework.kora.app.ksp.exception

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.kora.app.ksp.DependencyModuleHintProvider
import io.koraframework.kora.app.ksp.GraphBuilder
import io.koraframework.kora.app.ksp.component.DependencyClaim
import io.koraframework.kora.app.ksp.declaration.ComponentDeclaration
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.*
import javax.tools.Diagnostic

class UnresolvedDependencyException(
    val stack: Deque<GraphBuilder.ResolutionFrame>,
    val declaration: ComponentDeclaration,
    val dependencyClaim: DependencyClaim,
    hints: List<DependencyModuleHintProvider.Hint>,
) : ProcessingErrorException(listOf(ProcessingError(getMessage(stack, declaration, dependencyClaim, hints).trimIndent(), dependencyClaim.source ?: declaration.source, Diagnostic.Kind.ERROR))) {


    companion object {
        private fun getMessage(
            stack: Deque<GraphBuilder.ResolutionFrame>,
            declaration: ComponentDeclaration,
            dependencyClaim: DependencyClaim,
            hints: List<DependencyModuleHintProvider.Hint>,
        ): String {
            val msg = StringBuilder()
            msg.append("No component found for dependency:\n  ")
            msg.append(dependencyClaim.type.toTypeName())
            if (dependencyClaim.tag == null) {
                msg.append(" (no tags)")
            } else {
                msg.append(" with @Tag(${dependencyClaim.tag}::class)")
            }
            msg.append("\n\nNote:\n  Kotlin nullable and non-nullable types are different dependency keys.")

            val requestedMsg = getRequestedMessage(declaration)
            msg.append("\n\nRequired at:\n  ").append(requestedMsg)
            val source = dependencyClaim.source
            if (source is KSValueParameter) {
                msg.append("\n  parameter: ")
                    .append(source.type.toTypeName())
                    .append(" ")
                    .append(source.name?.asString() ?: "<unnamed>")
            }

            val treeMsg = getDependencyTreeSimpleMessage(stack, declaration, dependencyClaim)
            msg.append("\n\n").append(treeMsg)
            if (hints.isNotEmpty()) {
                msg.append("\n\nHint:")
                for (hint in hints) {
                    msg.append("\n  - ").append(hint.message().trim().replace("\n", "\n    "))
                }
            }
            msg.append("\n\nFix:")
            msg.append("\n  - Add @${CommonClassNames.component.canonicalName} to an implementation of ${dependencyClaim.type.toTypeName()}.")
            msg.append("\n  - Add a module function that returns ${dependencyClaim.type.toTypeName()}.")
            msg.append("\n  - Include a module that provides ${dependencyClaim.type.toTypeName()} in @KoraApp.")
            return msg.toString()
        }


        private fun getRequestedMessage(declaration: ComponentDeclaration): String {
            var element: KSDeclaration? = declaration.source
            var factoryMethod: KSFunctionDeclaration? = null
            var module: KSDeclaration? = null
            do {
                if (element is KSFunctionDeclaration) {
                    factoryMethod = element
                } else if (element is KSClassDeclaration) {
                    module = element
                    break
                } else if (element == null) {
                    continue
                }
                element = element.parentDeclaration
            } while (element != null)

            return if (module != null && factoryMethod != null && factoryMethod.isConstructor()) {
                "${module.qualifiedName!!.asString()}#${factoryMethod}(${
                    factoryMethod.parameters.joinToString(", ") {
                        it.type.toTypeName().toString()
                    }
                })"
            } else {
                "${module!!.qualifiedName!!.asString()}#${factoryMethod!!.qualifiedName!!.asString()}(${
                    factoryMethod.parameters.joinToString(", ") {
                        it.type.toTypeName().toString()
                    }
                })"
            }
        }

        private fun getDependencyTreeSimpleMessage(
            stack: Deque<GraphBuilder.ResolutionFrame>,
            declaration: ComponentDeclaration,
            dependencyClaim: DependencyClaim,
        ): String {
            val msg = StringBuilder()
            msg.append("Dependency resolution path:")

            val stackFrames = mutableListOf<GraphBuilder.ResolutionFrame>()
            val i = stack.descendingIterator()
            while (i.hasNext()) {
                val iFrame: GraphBuilder.ResolutionFrame = i.next()
                if (iFrame is GraphBuilder.ResolutionFrame.Root) {
                    stackFrames.add(iFrame)
                    break
                }
                stackFrames.add(iFrame)
            }

            // reversed order
            val delimiterRoot = "\n  @--- "
            val delimiterOverriden = "\n  ^~~~ "
            val delimiter = "\n  ^--- "
            for (i1 in stackFrames.indices.reversed()) {
                val iFrame = stackFrames[i1]
                if (iFrame is GraphBuilder.ResolutionFrame.Root) {
                    val rootDeclaration = iFrame.declaration
                    val rootDeclarationAsStr = rootDeclaration.declarationString()
                    if (rootDeclaration is ComponentDeclaration.FromModuleComponent) {
                        val currentModuleTypeName = rootDeclaration.module.element.qualifiedName!!.asString()
                        val originalModuleTypeName = if (rootDeclaration.method.findOverridee()?.parentDeclaration != null) {
                            rootDeclaration.method.findOverridee()!!.parentDeclaration!!.qualifiedName!!.asString()
                        } else {
                            rootDeclaration.module.element.qualifiedName!!.asString()
                        }

                        if (currentModuleTypeName != originalModuleTypeName) {
                            msg.append(delimiterRoot).append(rootDeclarationAsStr.replace(originalModuleTypeName, currentModuleTypeName))
                            msg.append(delimiter).append(rootDeclarationAsStr)
                        } else {
                            msg.append(delimiterRoot).append(rootDeclarationAsStr)
                        }
                    } else {
                        msg.append(delimiterRoot).append(rootDeclarationAsStr)
                    }
                } else {
                    val c = iFrame as GraphBuilder.ResolutionFrame.Component
                    if (c.declaration is ComponentDeclaration.FromModuleComponent && c.declaration.isOverriden()) {
                        msg.append(delimiterOverriden).append(c.declaration.declarationString())
                    } else {
                        msg.append(delimiter).append(c.declaration.declarationString())
                    }
                }
            }

            msg.append(delimiter).append(declaration.declarationString())

            val errorMissing = " [MISSING]"
            if (dependencyClaim.tag == null) {
                msg.append(delimiter)
                    .append(dependencyClaim.type.toTypeName()).append("   ")
                    .append(errorMissing)
            } else {
                msg.append(delimiter)
                    .append(dependencyClaim.type.toTypeName())
                    .append("  @Tag(").append(dependencyClaim.tag).append("::class)   ")
                    .append(errorMissing)
            }

            return msg.toString()
        }
    }

    private fun getRequestedMessage(declaration: ComponentDeclaration): String {
        var element: KSDeclaration? = declaration.source
        var factoryMethod: KSFunctionDeclaration? = null
        var module: KSDeclaration? = null
        do {
            if (element is KSFunctionDeclaration) {
                factoryMethod = element
            } else if (element is KSClassDeclaration) {
                module = element
                break
            } else if (element == null) {
                continue
            }
            element = element.parentDeclaration
        } while (element != null)

        return if (module != null && factoryMethod != null && factoryMethod.isConstructor()) {
            "Dependency requested at: ${module.qualifiedName!!.asString()}#${factoryMethod}(${
                factoryMethod.parameters.joinToString(", ") {
                    it.type.toTypeName().toString()
                }
            })"
        } else {
            "Dependency requested at: ${module!!.qualifiedName!!.asString()}#${factoryMethod!!.qualifiedName!!.asString()}(${
                factoryMethod.parameters.joinToString(", ") {
                    it.type.toTypeName().toString()
                }
            })"
        }
    }

}
