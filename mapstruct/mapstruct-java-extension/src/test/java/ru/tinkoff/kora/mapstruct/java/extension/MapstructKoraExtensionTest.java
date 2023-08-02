package ru.tinkoff.kora.mapstruct.java.extension;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mapstruct.ap.MappingProcessor;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class MapstructKoraExtensionTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import org.mapstruct.*;
            import org.mapstruct.Mapping;
            """;
    }

    protected GraphContainer compile(@Language("java") String... sources) {
        @Language("java")
        var realSources = Arrays.copyOf(sources, sources.length + 1);
        realSources[sources.length] = """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(TestMapper testMapper) {
                return "";
              }
            }        
            """;
        this.compile(List.of(new KoraAppProcessor(), new MappingProcessor()), realSources);
        compileResult.assertSuccess();

        return loadGraph("TestApp");
    }

    @Test
    public void test() {
        var graph = compile("""
            public enum CarType {TYPE1, TYPE2}
            """, """
            public record Car(String make, int numberOfSeats, CarType type) {
            }
            """, """
            public record CarDto(String make, int seatCount, String type) {
            }
            """, """
            @Mapper
            public interface TestMapper {
                @Mapping(source = "numberOfSeats", target = "seatCount")
                CarDto carToCarDto(Car car);
            }
            """);
        assertThat(graph.draw().size()).isEqualTo(2);
    }

    @Test
    public void testWithDependencies() {
        var graph = compile("""
                import java.util.Date;
                import java.text.SimpleDateFormat;
                import java.text.ParseException;
                            
                public final class DateMapper {
                            
                    public String asString(Date date) {
                        return date != null ? new SimpleDateFormat( "yyyy-MM-dd" )
                            .format( date ) : null;
                    }
                            
                    public Date asDate(String date) {
                        try {
                            return date != null ? new SimpleDateFormat( "yyyy-MM-dd" )
                                .parse( date ) : null;
                        }
                        catch ( ParseException e ) {
                            throw new RuntimeException( e );
                        }
                    }
                }
                """,
            """
                public enum CarType {TYPE1, TYPE2}
                """, """
                public record Car(java.util.Date make, int numberOfSeats, CarType type) {
                }
                """, """
                public record CarDto(String make, int seatCount, String type) {
                }
                """, """
                @Mapper(uses = DateMapper.class, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR, componentModel = "jakarta")
                public interface TestMapper {
                            
                    @Mapping(source = "numberOfSeats", target = "seatCount")
                    CarDto carToCarDto(Car car);
                }
                """);
        assertThat(graph.draw().size()).isEqualTo(3);
    }
}
