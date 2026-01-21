package ru.tinkoff.kora.kora.app.annotation.processor.exception;

import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.DependencyModuleHintProvider;
import ru.tinkoff.kora.kora.app.annotation.processor.GraphBuilder;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
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

        this(component, dependencyClaim, List.of(new ProcessingError(constructErrorMessage(koraApp, component, dependencyClaim, stack, hints), component.source())), stack);
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
        if (dependencyClaim.tag() == null) {
            var format = """
                Required dependency type wasn't found in graph and can't be auto created: %s (no tags)
                Please check class for @%s annotation or that required module with declaration factory is plugged in.""";
            msg.append(String.format(format, TypeName.get(dependencyClaim.type()), CommonClassNames.component.simpleName()));
        } else {
            var tagMsg = "@Tag(" + dependencyClaim.tag() + ".class)";
            var format = """
                Required dependency type wasn't found in graph and can't be auto created: %s with tag %s.
                Please check class for @%s annotation or that required module with declaration factory is plugged in.""";
            msg.append(String.format(format, TypeName.get(dependencyClaim.type()), tagMsg, CommonClassNames.component.simpleName()));
        }
        if (!hints.isEmpty()) {
            msg.append("\n\nHints:");
            for (var hint : hints) {
                msg.append("\n  - Hint: ").append(hint.message());
            }
        }

        var requestedMsg = getRequestedMessage(component);
        msg.append("\n").append(requestedMsg);

        var treeMsg = getDependencyTreeSimpleMessage(koraApp, stack, component, dependencyClaim);
        msg.append("\n").append(treeMsg);
        return msg.toString();
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
            return "Dependency requested at: %s.%s".formatted(module.getEnclosingElement(), factoryMethod);
        } else {
            return "Dependency requested at: %s#%s".formatted(module, factoryMethod);
        }
    }

    private static String getDependencyTreeSimpleMessage(TypeElement koraApp,
                                                         Deque<GraphBuilder.ResolutionFrame> stack,
                                                         ComponentDeclaration declaration,
                                                         DependencyClaim dependencyClaim) {
        var msg = new StringBuilder();
        msg.append("Dependency resolution tree:");

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

        var errorMissing = " [ ERROR: MISSING COMPONENT ]";
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
