package io.koraframework.application.graph;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeRefTest {
    List<String> list;
    Map.Entry<String, Integer> entry;

    @Test
    void topLevelTypeRefIsInterchangeableWithReflectedType() throws Exception {
        var reflected = TypeRefTest.class.getDeclaredField("list").getGenericType();
        var typeRef = TypeRef.of(List.class, String.class);

        assertInterchangeable(typeRef, reflected);
    }

    @Test
    void memberTypeRefIsInterchangeableWithReflectedType() throws Exception {
        var reflected = TypeRefTest.class.getDeclaredField("entry").getGenericType();
        var typeRef = TypeRef.of(Map.Entry.class, String.class, Integer.class);

        assertThat(typeRef.getOwnerType()).isEqualTo(Map.class);
        assertInterchangeable(typeRef, reflected);
    }

    private static void assertInterchangeable(TypeRef<?> typeRef, Type reflected) {
        assertThat(typeRef).isEqualTo(reflected);
        assertThat(reflected).isEqualTo(typeRef);
        assertThat(typeRef.hashCode()).isEqualTo(reflected.hashCode());

        var map = new HashMap<Type, String>();
        map.put(reflected, "found");
        assertThat(map.get(typeRef)).isEqualTo("found");
    }
}
