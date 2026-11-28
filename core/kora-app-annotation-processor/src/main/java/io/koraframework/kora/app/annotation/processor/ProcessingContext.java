package io.koraframework.kora.app.annotation.processor;

import io.koraframework.kora.app.annotation.processor.component.ResolvedComponent;
import io.koraframework.kora.app.annotation.processor.extension.Extensions;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

public class ProcessingContext {
    public final Types types;
    public final Elements elements;
    public final Messager messager;
    public final Filer filer;
    public final ServiceTypesHelper serviceTypeHelper;
    public final Extensions extensions;
    public final DependencyModuleHintProvider dependencyModuleHintProvider;

    public List<ResolvedComponent> components;
    public int componentsPerPartition;
    public int currentPartition;
    public int[] componentPartitions = new int[0];

    public ProcessingContext(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.messager = env.getMessager();
        this.filer = env.getFiler();
        this.serviceTypeHelper = new ServiceTypesHelper(elements, types);
        this.extensions = Extensions.load(this.getClass().getClassLoader(), env);
        this.dependencyModuleHintProvider = new DependencyModuleHintProvider(env);
    }

    public int partitionOf(int componentIndex) {
        if (componentIndex >= 0 && componentIndex < componentPartitions.length) {
            return componentPartitions[componentIndex];
        }
        return componentsPerPartition > 0 ? componentIndex / componentsPerPartition : 0;
    }
}
