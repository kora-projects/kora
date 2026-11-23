package io.koraframework.json.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InheritanceTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testWriterIncludesSuperclassFields() {
        compile("""
            public class Base {
                private final String id;
                public Base(String id) { this.id = id; }
                public String getId() { return id; }
            }
            """, """
            @Json
            public final class Child extends Base {
                private final String name;
                public Child(String id, String name) { super(id); this.name = name; }
                public String getName() { return name; }
            }
            """);

        var mapper = mapper("Child");
        var o = mapper.read("{\"id\":\"1\",\"name\":\"n\"}");

        assertThat(mapper.toString(o)).isEqualTo("{\"id\":\"1\",\"name\":\"n\"}");
    }

    @Test
    public void testWriterIncludesGenericSuperclassFields() {
        compile("""
            public class Base<T> {
                private final T value;
                public Base(T value) { this.value = value; }
                public T getValue() { return value; }
            }
            """, """
            @Json
            public final class Child extends Base<String> {
                private final int count;
                public Child(String value, int count) { super(value); this.count = count; }
                public int getCount() { return count; }
            }
            """);

        var mapper = mapper("Child");
        var o = mapper.read("{\"value\":\"v\",\"count\":2}");

        assertThat(mapper.toString(o)).isEqualTo("{\"value\":\"v\",\"count\":2}");
    }

    @Test
    public void testWriterSkipsInheritedFieldsWithoutAccessor() {
        compile("""
            public class Base {
                private final String secret = "s";
                protected transient int counter;
            }
            """, """
            @Json
            public final class Child extends Base {
                private final int x;
                public Child(int x) { this.x = x; }
                public int getX() { return x; }
            }
            """);

        var mapper = mapper("Child");

        assertThat(mapper.toString(newObject("Child", 1))).isEqualTo("{\"x\":1}");
    }

    @Test
    public void testWriterForLibraryBaseClass() {
        compile("""
            @JsonWriter
            public final class Child extends java.util.AbstractList<String> {
                private final String name;
                public Child(String name) { this.name = name; }
                public String getName() { return name; }
                @Override public String get(int index) { throw new IndexOutOfBoundsException(); }
                @Override public int size() { return 0; }
            }
            """);

        assertThat(writer("Child").toString(newObject("Child", "n"))).isEqualTo("{\"name\":\"n\"}");
    }

    @Test
    public void testWriterSkipsInheritedFieldHiddenBySubclassField() {
        compile("""
            public class Base {
                private final String name = "b";
                public String getName() { return name; }
            }
            """, """
            @Json
            public final class Child extends Base {
                private final String name;
                public Child(String name) { this.name = name; }
                @Override public String getName() { return name; }
            }
            """);

        assertThat(writer("Child").toString(newObject("Child", "c"))).isEqualTo("{\"name\":\"c\"}");
    }

    @Test
    public void testWriterSkipsInheritedFieldWithProtectedGetterFromOtherPackage() {
        compile("""
            @JsonWriter
            public final class Child extends io.koraframework.json.annotation.processor.dto.BaseWithProtectedGetter {
                private final int x;
                public Child(int x) { this.x = x; }
                public int getX() { return x; }
            }
            """);

        assertThat(writer("Child").toString(newObject("Child", 1))).isEqualTo("{\"x\":1}");
    }

    @Test
    public void testWriterForExceptionSubclassWritesOnlyOwnFields() {
        compile("""
            @JsonWriter
            public final class ApiError extends RuntimeException {
                private final String code;
                public ApiError(String code) { super(code, null, false, false); this.code = code; }
                public String getCode() { return code; }
            }
            """);

        assertThat(writer("ApiError").toString(newObject("ApiError", "E1"))).isEqualTo("{\"code\":\"E1\"}");
    }
}
