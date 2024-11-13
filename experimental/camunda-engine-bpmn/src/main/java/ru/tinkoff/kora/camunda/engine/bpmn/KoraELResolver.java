package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;

import java.beans.FeatureDescriptor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KoraELResolver extends ELResolver {

    private final Map<String, Object> componentByKey;

    public KoraELResolver(KoraDelegateWrapperFactory wrapperFactory,
                          List<KoraDelegate> koraDelegates,
                          List<JavaDelegate> javaDelegates) {
        this.componentByKey = new HashMap<>();
        for (JavaDelegate delegate : javaDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegate.getClass().getSimpleName(), wrapped);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), wrapped);
        }

        for (KoraDelegate delegate : koraDelegates) {
            JavaDelegate wrapped = wrapperFactory.wrap(delegate);
            this.componentByKey.put(delegate.key(), wrapped);
            this.componentByKey.put(delegate.getClass().getSimpleName(), wrapped);
            this.componentByKey.put(delegate.getClass().getCanonicalName(), wrapped);
        }
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base == null) {
            // according to javadoc, can only be a String
            String key = (String) property;
            Object value = componentByKey.get(key);
            if (value != null) {
                context.setPropertyResolved(true);
                return value;
            }
        }
        return null;
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (base == null) {
            String key = (String) property;
            if (componentByKey.containsKey(key)) {
                throw new ProcessEngineException(
                    "Cannot set value of '" + property + "', it resolves to a CamundaComponent defined in the Kora application."
                );
            }
        }
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return Object.class;
    }
}
