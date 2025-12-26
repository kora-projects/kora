package ru.tinkoff.kora.kora.app.ksp.exception

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.kora.app.ksp.DependencyModuleHintProvider
import ru.tinkoff.kora.kora.app.ksp.GraphBuilder
import ru.tinkoff.kora.kora.app.ksp.component.DependencyClaim
import ru.tinkoff.kora.kora.app.ksp.declaration.ComponentDeclaration
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*
import javax.tools.Diagnostic

class UnresolvedDependencyException(
    val stack: Deque<GraphBuilder.ResolutionFrame>,
    val declaration: ComponentDeclaration,
    val dependencyClaim: DependencyClaim,
    hints: List<DependencyModuleHintProvider.Hint>,
) : ProcessingErrorException(listOf(ProcessingError(getMessage(stack, declaration, dependencyClaim, hints).trimIndent(), declaration.source, Diagnostic.Kind.ERROR))) {


    companion object {
        private fun getMessage(
            stack: Deque<GraphBuilder.ResolutionFrame>,
            declaration: ComponentDeclaration,
            dependencyClaim: DependencyClaim,
            hints: List<DependencyModuleHintProvider.Hint>,
        ): String {
            val msg = if (dependencyClaim.tag == null) {
                StringBuilder(
                    "Required dependency type wasn't found in graph and can't be auto created: ${dependencyClaim.type.toTypeName()} (no tags)\n" +
                        "Keep in mind that nullable & non nullable types are different in Kotlin.\n" +
                        "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component factory is plugged in."
                )
            } else {
                val tagMsg = "@Tag(${dependencyClaim.tag}::class)"
                StringBuilder(
                    "Required dependency type wasn't found in graph and can't be auto created: ${dependencyClaim.type.toTypeName()} with tag ${tagMsg}.\n" +
                        "Keep in mind that nullable & non nullable types are different in Kotlin).\n" +
                        "Please check class for @${CommonClassNames.component.canonicalName} annotation or that required module with component factory is plugged in."
                )
            }

            if (hints.isNotEmpty()) {
                msg.append("\n\nHints:")
                for (hint in hints) {
                    msg.append("\n  - Hint: ").append(hint.message())
                }
            }

            val claimMsg = "Required dependency claim: $dependencyClaim"
            msg.append("\n\n").append(claimMsg)

            val requestedMsg = getRequestedMessage(declaration)
            msg.append("\n").append(requestedMsg)

            val treeMsg = getDependencyTreeSimpleMessage(stack, declaration, dependencyClaim)
            msg.append("\n").append(treeMsg)
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

        private fun getDependencyTreeSimpleMessage(
            stack: Deque<GraphBuilder.ResolutionFrame>,
            declaration: ComponentDeclaration,
            dependencyClaim: DependencyClaim,
        ): String {
            val msg = StringBuilder()
            msg.append("Dependency resolution tree:")

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

            val errorMissing = " [ ERROR: MISSING COMPONENT ]"
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
