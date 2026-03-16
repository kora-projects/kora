package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.ClassName;
import io.koraframework.kora.app.annotation.processor.component.ResolvedComponent;

import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

public record ResolvedGraph(TypeElement root, List<TypeElement> allModules, List<ResolvedComponent> components, Map<ClassName, ResolvedComponent> conditionByTag) {}
