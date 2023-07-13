package ru.tinkoff.kora.config.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AnnotationConfigTest extends AbstractConfigTest {
    @Test
    public void testIntSupported() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              int value();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", 42));
    }

    @Test
    public void testIntegerSupported() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              @Nullable
              Integer value();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", 42));
        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", new Object[]{null}));
    }

    @Test
    public void testStringSupported() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              String value();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "test"));
        assertThatThrownBy(() -> extractor.extract(MapConfigFactory.fromMap(Map.of()).root()))
            .isInstanceOf(ConfigValueExtractionException.class)
            .hasMessageStartingWith("Expected value, but got null")
            .hasMessageEndingWith("path: 'ROOT.value'");
    }

    @Test
    public void testDefaultValues() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              default String value() { return "default-value"; }
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "test"));
        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "default-value"));
    }

    @Test
    public void testDefaultAndNullable() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              default String value1() { return "default-value"; }
              
              @Nullable
              String value2();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "test", null));
        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of()).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "default-value", null));
    }

    @Test
    public void testInterfaceWithUnknownType() {
        var mapper = Mockito.mock(ConfigValueExtractor.class);
        when(mapper.extract(any())).thenAnswer(invocation -> invocation.getArguments()[0] instanceof ConfigValue.NullValue
            ? null :
            Duration.ofDays(3000)
        );

        var extractor = this.compileConfig(List.of(mapper), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              java.time.Duration value();
              
              @Nullable
              java.time.Duration value2();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", Duration.ofDays(3000), null));

        verify(mapper, times(2)).extract(any());
    }

    @Test
    public void testInterfaceWithUnknownTypeAndMapping() {
        var extractor = this.compileConfig(List.of(newGeneratedObject("TestExtractor")), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              @Mapping(TestExtractor.class)
              @Tag(TestExtractor.class)
              java.time.Duration value1();
              
              @Nullable
              @Mapping(TestFinalExtractor.class)
              java.time.Duration value2();
            }
            """, """
            import ru.tinkoff.kora.config.common.ConfigValue;

            public class TestExtractor implements ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<java.time.Duration> {
                public java.time.Duration extract(ConfigValue<?> value) {
                  return value instanceof ConfigValue.NullValue ? null : java.time.Duration.ofDays(3000);
                }
            }
            """, """
            import ru.tinkoff.kora.config.common.ConfigValue;

            public final class TestFinalExtractor implements ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<java.time.Duration> {
                public java.time.Duration extract(ConfigValue<?> value) {
                  return value instanceof ConfigValue.NullValue ? null : java.time.Duration.ofDays(3000);
                }
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", Duration.ofDays(3000), null));
    }

    @Test
    public void testInterfaceWithSuper() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig extends SuperTestConfig{
              String value1();
            }
            """, """
            public interface SuperTestConfig {
              default String value2() {  return "default-value"; }
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value1", "test1", "value2", "test2")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", "test1", "test2"));
    }

    @Test
    public void testInterfaceWithArray() {
        var mapper = Mockito.mock(ConfigValueExtractor.class);
        when(mapper.extract(any())).thenAnswer(invocation -> new int[]{1, 2, 3});

        var extractor = this.compileConfig(List.of(mapper), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public interface TestConfig {
              int[] value();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", (Object) new int[]{1, 2, 3}));

        verify(mapper).extract(any());
    }

    @Test
    public void testRecord() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public record TestConfig(String value) {
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("TestConfig", "test"));
    }

    @Test
    public void testRecordAllNullable() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public record TestConfig(@Nullable String value) {
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(newObject("TestConfig", "test"));
    }

    @Test
    public void testRecordWithUnknownType() {
        var mapper = Mockito.mock(ConfigValueExtractor.class);
        when(mapper.extract(any())).thenReturn(Duration.ofDays(3000));

        var extractor = this.compileConfig(List.of(mapper), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            public record TestConfig (
              java.time.Duration value
            ){}
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value1", "test")).root()))
            .isEqualTo(newObject("TestConfig", Duration.ofDays(3000)));

        verify(mapper).extract(any());
    }

    @Test
    public void testPojo() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
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
            }
            """);

        var expected = newObject("TestConfig");
        invoke(expected, "setValue", "test");

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(expected);
    }

    @Test
    public void testPojoWithDefault() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
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
        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", "test")).root()))
            .isEqualTo(expected);

        invoke(expected, "setValue", "default-value");
        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of()).root()))
            .isEqualTo(expected);
    }

}
