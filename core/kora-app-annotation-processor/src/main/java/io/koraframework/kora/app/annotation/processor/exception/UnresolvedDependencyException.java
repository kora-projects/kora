package io.koraframework.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.kora.app.annotation.processor.DependencyModuleHintProvider;
import io.koraframework.kora.app.annotation.processor.GraphBuilder;
import io.koraframework.kora.app.annotation.processor.component.DependencyClaim;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class UnresolvedDependencyException extends ProcessingErrorException {
    private final ComponentDeclaration component;
    private final DependencyClaim dependencyClaim;
    private final Deque<GraphBuilder.ResolutionFrame> stack;

    public UnresolvedDependencyException(TypeElement koraApp,
                                         ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         Deque<GraphBuilder.ResolutionFrame> stack,
                                         List<DependencyModuleHintProvider.Hint> hints) {

        this(component, dependencyClaim, List.of(getError(koraApp, component, dependencyClaim, stack, hints)), stack);
    }

    private static ProcessingError getError(TypeElement koraApp, ComponentDeclaration component, DependencyClaim dependencyClaim, Deque<GraphBuilder.ResolutionFrame> stack, List<DependencyModuleHintProvider.Hint> hints) {
        var errorSource = dependencyClaim.source() == null ? component.source() : dependencyClaim.source();
        return new ProcessingError(constructErrorMessage(koraApp, component, dependencyClaim, stack, hints), errorSource);
    }


    public UnresolvedDependencyException(ComponentDeclaration component,
                                         DependencyClaim dependencyClaim,
                                         List<ProcessingError> errors,
                                         Deque<GraphBuilder.ResolutionFrame> stack) {
        super(errors);
        this.component = component;
        this.dependencyClaim = dependencyClaim;
        this.stack = stack;
    }

    public ComponentDeclaration getComponent() {
        return component;
    }

    public DependencyClaim getDependencyClaim() {
        return dependencyClaim;
    }

    public Deque<GraphBuilder.ResolutionFrame> getStack() {
        return stack;
    }

    private static String constructErrorMessage(TypeElement koraApp, ComponentDeclaration component, DependencyClaim dependencyClaim, Deque<GraphBuilder.ResolutionFrame> stack, List<DependencyModuleHintProvider.Hint> hints) {
        var msg = new StringBuilder();
        msg.append("No component found for dependency:\n  ");
        msg.append(TypeName.get(dependencyClaim.type()));
        if (dependencyClaim.tag() == null) {
            msg.append(" (no tags)");
        } else {
            msg.append(" with ").append(formatTag(dependencyClaim.tag()));
        }

        var requestedMsg = getRequestedMessage(component);
        msg.append("\n\nRequired at:\n  ").append(requestedMsg);
        var source = dependencyClaim.source();
        if (source instanceof VariableElement variableElement) {
            msg.append("\n  parameter: ").append(variableElement.asType()).append(" ").append(variableElement.getSimpleName());
        }

        var treeMsg = getDependencyTreeSimpleMessage(koraApp, stack, component, dependencyClaim);
        msg.append("\n\n").append(treeMsg);
        if (!hints.isEmpty()) {
            msg.append("\n\nHint:");
            for (var hint : hints) {
                msg.append("\n  - ").append(hint.message().strip().replace("\n", "\n    "));
            }
        }
        msg.append("\n\nFix:");
        msg.append("\n  - Add @").append(CommonClassNames.component.simpleName()).append(" to an implementation of ").append(TypeName.get(dependencyClaim.type())).append('.');
        msg.append("\n  - Add a module method that returns ").append(TypeName.get(dependencyClaim.type())).append('.');
        msg.append("\n  - Include a module that provides ").append(TypeName.get(dependencyClaim.type())).append(" in @KoraApp.");
        return msg.toString();
    }

    private static String formatTag(String tag) {
        return "@Tag(" + tag + ".class)";
    }

    private static String getRequestedMessage(ComponentDeclaration declaration) {
        var element = declaration.source();
        var factoryMethod = (ExecutableElement) null;
        var module = (TypeElement) null;
        do {
            if (element instanceof ExecutableElement) {
                factoryMethod = (ExecutableElement) element;
            } else if (element instanceof TypeElement) {
                module = (TypeElement) element;
                break;
            } else if (element == null) {
                continue;
            }
            element = element.getEnclosingElement();
        } while (element != null);

        if (module != null && factoryMethod != null && factoryMethod.getKind() == ElementKind.CONSTRUCTOR) {
            return "%s.%s".formatted(module.getEnclosingElement(), factoryMethod);
        } else {
            return "%s#%s".formatted(module, factoryMethod);
        }
    }

    private static String getDependencyTreeSimpleMessage(TypeElement koraApp,
                                                         Deque<GraphBuilder.ResolutionFrame> stack,
                                                         ComponentDeclaration declaration,
                                                         DependencyClaim dependencyClaim) {
        var msg = new StringBuilder();
        msg.append("Dependency resolution path:");

        var stackFrames = new ArrayList<GraphBuilder.ResolutionFrame>();
        var i = stack.descendingIterator();
        while (i.hasNext()) {
            var iFrame = i.next();
            if (iFrame instanceof GraphBuilder.ResolutionFrame.Root root) {
                stackFrames.add(root);
                break;
            }
            stackFrames.add(iFrame);
        }

        // reversed order
        var delimiterRoot = "\n  @--- ";
        var delimiter = "\n  ^--- ";
        for (int i1 = stackFrames.size() - 1; i1 >= 0; i1--) {
            var iFrame = stackFrames.get(i1);
            if (iFrame instanceof GraphBuilder.ResolutionFrame.Root root) {
                var rootDeclaration = root.componentDeclaration();
                var rootDeclarationAsStr = rootDeclaration.declarationString();
                var koraAppName = koraApp.getQualifiedName().toString();
                if (rootDeclaration instanceof ComponentDeclaration.FromModuleComponent mc && !rootDeclarationAsStr.contains(koraAppName)) {
                    var moduleTypeName = mc.module().element().getQualifiedName().toString();
                    msg.append(delimiterRoot).append(rootDeclarationAsStr.replace(moduleTypeName, koraAppName));
                    msg.append(delimiter).append(rootDeclarationAsStr);
                } else {
                    msg.append(delimiterRoot).append(rootDeclarationAsStr);
                }
            } else {
                var c = (GraphBuilder.ResolutionFrame.Component) iFrame;
                msg.append(delimiter).append(c.declaration().declarationString());
            }
        }

        msg.append(delimiter).append(declaration.declarationString());

        var errorMissing = " [MISSING]";
        if (dependencyClaim.tag() == null) {
            msg.append(delimiter)
                .append(dependencyClaim.type()).append("   ")
                .append(errorMissing)
                .append("\n");
        } else {
            msg.append(delimiter)
                .append(dependencyClaim.type())
                .append("  @Tag(").append(dependencyClaim.tag()).append(".class)   ")
                .append(errorMissing)
                .append("\n");
        }

        return msg.toString();
    }

}
