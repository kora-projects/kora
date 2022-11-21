package ru.tinkoff.kora.annotation.processor.common;

import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestContext {
    private final List<ContextElement<?>> elements = new ArrayList<>();

    public <T> void addMock(TypeRef<T> type) {
        var o = Mockito.mock(type.getRawType());
        elements.add(new ContextElement<>(type, null, o));
    }

    public <T> void addContextElement(TypeRef<T> type, T o) {
        elements.add(new ContextElement<>(type, null, o));
    }

    public <T> void addContextElement(TypeRef<T> typeRef, Class<?>[] tag, T o) {
        elements.add(new ContextElement<>(typeRef, tag, o));
    }

    public <T> T findInstance(TypeRef<T> of) {
        for (var element : elements) {
            if (element.typeRef.equals(of)) {
                @SuppressWarnings("unchecked")
                var value = (T) element.o;
                return value;
            }
        }
        return null;
    }

    public void resetMocks() {
        for (var element : elements) {
            if (MockUtil.isMock(element.o) || MockUtil.isSpy(element.o)) {
                Mockito.<Object>reset(element.o);
            }
        }
    }

    record ContextElement<T>(TypeRef<T> typeRef, Class<?>[] tag, T o) {}

    public <T> T newInstance(Class<T> type) {
        @SuppressWarnings("unchecked")
        Constructor<T> constructor = (Constructor<T>) type.getConstructors()[0];
        var params = new Object[constructor.getParameterCount()];
        param:
        for (int i = 0; i < constructor.getGenericParameterTypes().length; i++) {
            var paramType = constructor.getGenericParameterTypes()[i];
            var param = constructor.getParameters()[i];
            var tag = param.getAnnotation(Tag.class);
            var tags = tag != null ? tag.value() : null;
            for (var element : elements) {
                if (element.typeRef.equals(paramType) && Arrays.equals(tags, element.tag)) {
                    params[i] = element.o;
                    continue param;
                }
            }
            throw new RuntimeException("Constructor param of type %s with tags %s was not found".formatted(paramType, Arrays.toString(tags)));
        }
        try {
            return constructor.newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
