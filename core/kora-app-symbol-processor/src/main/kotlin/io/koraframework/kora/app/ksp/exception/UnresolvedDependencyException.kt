package io.koraframework.kora.app.ksp.exception

import com.google.devtools.ksp.processing.Resolver
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
    resolver: Resolver,
    val stack: Deque<GraphBuilder.ResolutionFrame>,
    val declaration: ComponentDeclaration,
    val dependencyClaim: DependencyClaim,
    hints: List<DependencyModuleHintProvider.Hint>,
    sameTypeDifferentTag: List<ComponentDeclaration>,
    sameTypeDifferentNullability: List<ComponentDeclaration> = listOf(),
) : ProcessingErrorException(listOf(ProcessingError(getMessage(resolver, stack, declaration, dependencyClaim, hints, sameTypeDifferentTag, sameTypeDifferentNullability), dependencyClaim.source ?: declaration.source, Diagnostic.Kind.ERROR))) {


    companion object {
        private const val MAX_CANDIDATES = 5

        private fun getMessage(
            resolver: Resolver,
            stack: Deque<GraphBuilder.ResolutionFrame>,
            declaration: ComponentDeclaration,
            dependencyClaim: DependencyClaim,
            hints: List<DependencyModuleHintProvider.Hint>,
            sameTypeDifferentTag: List<ComponentDeclaration>,
            sameTypeDifferentNullability: List<ComponentDeclaration>,
        ): String {
            val msg = StringBuilder()
            msg.append("No component found for dependency:\n  ")
            msg.append(dependencyClaim.type.toTypeName())
            msg.append(DependencySourceFormatter.tagSuffix(resolver, dependencyClaim.tag))

            msg.append(DependencySourceFormatter.requiredAtSection(declaration, dependencyClaim.source))

            val treeMsg = getDependencyTreeSimpleMessage(resolver, stack, declaration, dependencyClaim)
            msg.append("\n\n").append(treeMsg)
            if (sameTypeDifferentNullability.isNotEmpty()) {
                msg.append("\n\nNote:")
                msg.append("\n  Found component(s) with the same type but different nullability. Kotlin nullable and non-nullable types are different dependency keys:")
                appendCandidates(resolver, msg, sameTypeDifferentNullability)
            }
            if (sameTypeDifferentTag.isNotEmpty()) {
                msg.append("\n\nNote:")
                msg.append("\n  Found component(s) of the same type with other tags. Maybe the tag was forgotten or mixed up:")
                appendCandidates(resolver, msg, sameTypeDifferentTag)
            }
            if (hints.isNotEmpty()) {
                msg.append("\n\nHint:")
                for (hint in hints) {
                    // hint lines are aligned under the bullet, whatever indentation hint author used
                    msg.append("\n  - ").append(hint.message().trim().lines().joinToString("\n    ") { it.trim() })
                }
            }
            msg.append("\n\nFix:")
            if (sameTypeDifferentNullability.isNotEmpty()) {
                msg.append("\n  - Make the dependency and the component types agree on nullability.")
            }
            appendTagFixes(resolver, msg, dependencyClaim, sameTypeDifferentTag)
            msg.append("\n  - Add @${CommonClassNames.component.simpleName} to an implementation of ${dependencyClaim.type.toTypeName()}.")
            msg.append("\n  - Add a module function that returns ${dependencyClaim.type.toTypeName()}.")
            msg.append("\n  - Include a module that provides ${dependencyClaim.type.toTypeName()} in @KoraApp.")
            return msg.toString()
        }

        private fun appendCandidates(resolver: Resolver, msg: StringBuilder, candidates: List<ComponentDeclaration>) {
            for (candidate in candidates.take(MAX_CANDIDATES)) {
                msg.append("\n  - ").append(candidate.type.toTypeName())
                    .append(DependencySourceFormatter.tagSuffix(resolver, candidate.tag))
                    .append(" from ").append(candidate.declarationString())
            }
            if (candidates.size > MAX_CANDIDATES) {
                msg.append("\n  - ... and ").append(candidates.size - MAX_CANDIDATES).append(" more")
            }
        }


        /**
         * Suggests how to make the dependency and the found same type components agree on the tag, in both directions:
         * request the tag the components have, or change the tag of the component.
         */
        private fun appendTagFixes(resolver: Resolver, msg: StringBuilder, claim: DependencyClaim, sameTypeDifferentTag: List<ComponentDeclaration>) {
            if (sameTypeDifferentTag.isEmpty()) {
                return
            }
            for (tag in sameTypeDifferentTag.mapNotNull { it.tag }.distinct()) {
                msg.append("\n  - Request the dependency with ").append(DependencySourceFormatter.tag(resolver, tag)).append(" to use the component with this tag.")
            }
            val claimTag = claim.tag
            if (claimTag != null && sameTypeDifferentTag.any { it.tag == null }) {
                msg.append("\n  - Remove ").append(DependencySourceFormatter.tag(resolver, claimTag)).append(" from the dependency to use the component without tags.")
            }
            if (claimTag != null) {
                msg.append("\n  - Or add ").append(DependencySourceFormatter.tag(resolver, claimTag)).append(" to the component declaration so it matches this dependency.")
            } else {
                msg.append("\n  - Or remove the tag from the component declaration so it matches this dependency.")
            }
        }

        private fun getDependencyTreeSimpleMessage(
            resolver: Resolver,
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

            msg.append(delimiter).append(dependencyClaim.type.toTypeName())
            val claimTag = dependencyClaim.tag
            if (claimTag != null) {
                msg.append(" ").append(DependencySourceFormatter.tag(resolver, claimTag))
            }
            msg.append(" [MISSING]")

            return msg.toString()
        }
    }

}
