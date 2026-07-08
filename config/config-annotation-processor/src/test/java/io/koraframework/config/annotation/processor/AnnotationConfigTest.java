package io.koraframework.config.annotation.processor;

import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class AnnotationConfigTest extends AbstractConfigTest {
    @Test
    public void testIntSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              int value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", 42));
    }

    @Test
    public void testIntArraySupported() {
        var mockMapper = Mockito.mock(ConfigValueMapper.class);
        when(mockMapper.map(any())).thenReturn((Object) new int[] {42});

        var mapper = this.compileConfig(List.of(mockMapper), """
            @ConfigMapper
            public interface TestConfig {
              int[] value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value",  List.of(42))).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", (Object) new int[] {42}));

        verify(mockMapper).map(any());
    }

    @Test
    public void testIntArrayNullableSupported() {
        var mockMapper = Mockito.mock(ConfigValueMapper.class);
        when(mockMapper.map(any())).thenReturn((Object) new int[] {42});

        var mapper = this.compileConfig(List.of(mockMapper), """
            @ConfigMapper
            public interface TestConfig {
              @Nullable
              int[] value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", List.of(42))).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", (Object) new int[] {42}));

        verify(mockMapper).map(any());
    }

    @Test
    public void testIntegerSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              @Nullable
              Integer value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", 42));
        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", new Object[]{null}));
    }

    @Test
    public void testStringSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              String value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "test"));
        assertThatThrownBy(() -> mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isInstanceOf(ConfigValueException.class)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    public void testLongSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              Long value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", 42L)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", 42L));
        assertThatThrownBy(() -> mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isInstanceOf(ConfigValueException.class)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    public void testBooleanSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              Boolean value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", true)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", true));
        assertThatThrownBy(() -> mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isInstanceOf(ConfigValueException.class)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    public void testDoubleSupported() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              Double value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", 42.5)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", 42.5));
        assertThatThrownBy(() -> mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isInstanceOf(ConfigValueException.class)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    public void testDefaultValues() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              default String value() { return "default-value"; }
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "test"));
        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "default-value"));
    }

    @Test
    public void testDefaultAndNullable() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig {
              default String value1() { return "default-value"; }

              @Nullable
              String value2();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "test", null));
        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "default-value", null));
    }

    @Test
    public void testInterfaceWithUnknownType() {
        var mockMapper = Mockito.mock(ConfigValueMapper.class);
        when(mockMapper.map(any())).thenAnswer(invocation -> {
            if (invocation.getArguments()[0] instanceof ConfigValue.NullValue) {
                throw new IllegalArgumentException();
            }
            return Duration.ofDays(3000);
        });

        var mapper = this.compileConfig(List.of(mockMapper), """
            @ConfigMapper
            public interface TestConfig {
              java.time.Duration value();

              java.time.@Nullable Duration value2();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", Duration.ofDays(3000), null));

        verify(mockMapper).map(any());
    }

    @Test
    public void testInterfaceWithUnknownTypeAndMapping() {
        var mapper = this.compileConfig(List.of(newGeneratedObject("TestExtractor")), """
            @ConfigMapper
            public interface TestConfig {
              @Mapping(TestExtractor.class)
              @Tag(TestExtractor.class)
              java.time.Duration value1();

              @Mapping(TestFinalExtractor.class)
              java.time.@Nullable Duration value2();
            }
            """, """
            import io.koraframework.config.common.mapper.ConfigValueMapper;import io.koraframework.config.common.ConfigValue;

            public class TestExtractor implements ConfigValueMapper<java.time.Duration> {
                public java.time.Duration map(ConfigValue<?> value) {
                  return value instanceof ConfigValue.NullValue ? null : java.time.Duration.ofDays(3000);
                }
            }
            """, """
            import io.koraframework.config.common.mapper.ConfigValueMapper;import io.koraframework.config.common.ConfigValue;

            public final class TestFinalExtractor implements ConfigValueMapper<java.time.Duration> {
                public java.time.Duration map(ConfigValue<?> value) {
                  return value instanceof ConfigValue.NullValue ? null : java.time.Duration.ofDays(3000);
                }
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", Duration.ofDays(3000), null));
    }

    @Test
    public void testInterfaceWithSuper() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig extends SuperTestConfig{
              String value1();
            }
            """, """
            public interface SuperTestConfig {
              default String value2() {  return "default-value"; }

              String value3();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test1", "value2", "test2", "value3", "test3")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "test1", "test2", "test3"));
    }

    @Test
    public void testInterfaceNoFieldsWithSuper() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public interface TestConfig extends SuperTestConfig{
            }
            """, """
            public interface SuperTestConfig {
              default String value2() {  return "default-value"; }
    
              String value3();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value2", "test2", "value3", "test3")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl",  "test2", "test3"));
    }

    @Test
    public void testInterfaceWithArray() {
        var mockMapper = Mockito.mock(ConfigValueMapper.class);
        when(mockMapper.map(any())).thenAnswer(invocation -> new int[]{1, 2, 3});

        var mapper = this.compileConfig(List.of(mockMapper), """
            @ConfigMapper
            public interface TestConfig {
              int[] value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", (Object) new int[]{1, 2, 3}));

        verify(mockMapper).map(any());
    }

    @Test
    public void testRecord() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public record TestConfig(String value) {
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("TestConfig", "test"));
    }

    @Test
    public void testRecordAllNullable() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public record TestConfig(@Nullable String value) {
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("TestConfig", "test"));
    }

    @Test
    public void testRecordWithUnknownType() {
        var mockMapper = Mockito.mock(ConfigValueMapper.class);
        when(mockMapper.map(any())).thenReturn(Duration.ofDays(3000));

        var mapper = this.compileConfig(List.of(mockMapper), """
            @ConfigMapper
            public record TestConfig (
              java.time.Duration value
            ){}
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("TestConfig", Duration.ofDays(3000)));

        verify(mockMapper).map(any());
    }

    @Test
    public void testPojo() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public class TestConfig {
              private String value;

              public String getValue() {
                return this.value;
              }

              public void setValue(String value) {
                this.value = value;
              }

              @Override
              public boolean equals(Object obj) {
                return obj instanceof TestConfig that && java.util.Objects.equals(this.value, that.value);
              }

              public int hashCode() { return java.util.Objects.hashCode(value); }

              @Override
              public String toString() {
                return "TestConfig[%s]".formatted(value);
              }
            }
            """);

        var expected = newObject("TestConfig");
        invoke(expected, "setValue", "test");

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(expected);
    }

    @Test
    public void testPojoWithDefault() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public class TestConfig {
              private String value = "default-value";

              public String getValue() {
                return this.value;
              }

              public void setValue(String value) {
                this.value = value;
              }

              @Override
              public boolean equals(Object obj) {
                return obj instanceof TestConfig that && java.util.Objects.equals(this.value, that.value);
              }

              public int hashCode() { return java.util.Objects.hashCode(value); }
            }
            """);

        var expected = newObject("TestConfig");

        invoke(expected, "setValue", "test");
        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(expected);

        invoke(expected, "setValue", "default-value");
        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of()).root()))
            .isEqualTo(expected);
    }

    @Test
    public void testPojoWithConstructor() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public class TestConfig {
              @Nullable
              private final String value1;

              private final String value2;

              public TestConfig(String value1, @Nullable String value2) {
                this.value1 = value1;
                this.value2 = value2;
              }

              public String getValue1() {
                return this.value1;
              }

              public String getValue2() {
                return this.value2;
              }

              @Override
              public boolean equals(Object obj) {
                return obj instanceof TestConfig that && java.util.Objects.equals(this.value1, that.value1) && java.util.Objects.equals(this.value2, that.value2);
              }

              public int hashCode() { return java.util.Objects.hash(value1, value2); }

              public String toString() {
                return "TestConfig(value1=%s, value2=%s)".formatted(this.value1, this.value2);
              }
            }
            """);

        var expected = newObject("TestConfig", "test", null);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(expected);
    }

    @Test
    public void testPojoWithFluent() {
        var mapper = this.compileConfig(List.of(), """
            @ConfigMapper
            public class TestConfig {
              @Nullable
              private final String value1;
              @Nullable
              private final String value2;

              public TestConfig(String value1, @Nullable String value2) {
                this.value1 = value1;
                this.value2 = value2;
              }

              public String value1() {
                return this.value1;
              }

              public String value2() {
                return this.value2;
              }

              @Override
              public boolean equals(Object obj) {
                return obj instanceof TestConfig that && java.util.Objects.equals(this.value1, that.value1) && java.util.Objects.equals(this.value2, that.value2);
              }

              public int hashCode() { return java.util.Objects.hash(value1, value2); }

              public String toString() {
                return "TestConfig(value1=%s, value2=%s)".formatted(this.value1, this.value2);
              }
            }
            """);

        var expected = newObject("TestConfig", "test", null);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(expected);

        assertThatThrownBy(() -> mapper.map(ConfigMappingUtils.fromMap(Map.of("value2", "test")).root()));
    }

}
