package ru.tinkoff.kora.konvert.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph
import java.util.Arrays

class KonvertKoraExtensionTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.mcarle.konvert.api.Konverter
            import io.mcarle.konvert.api.Konvert
            import io.mcarle.konvert.api.Mapping
            import ru.tinkoff.kora.konvert.ksp.extension.*

            """.trimIndent()
    }

    private fun compile(@Language("kotlin") vararg sources: String): GraphUtil.GraphContainer {
        val patchedSources = Arrays.copyOf(sources, sources.size + 1)

        @Language("kotlin")
        val main = """
            @KoraApp
            interface TestApp {
                @Root
                fun root(mapper: CarMapper): String {
                    return ""
                }
            }
            """.trimIndent()
        patchedSources[sources.size] = main
        super.compile0(*patchedSources)
        compileResult.assertSuccess()
        return loadClass("TestAppGraph").toGraph()
    }

    @Test
    fun konverterInterfaceIsInjectableAcrossKspRounds() {
        val graph = compile(
            """
            data class Car(val make: String, val numberOfSeats: Int)
            """.trimIndent(),
            """
            data class CarDto(val make: String, val seatCount: Int)
            """.trimIndent(),
            """
            @Konverter
            interface CarMapper {
                @Konvert(mappings = [Mapping(source = "numberOfSeats", target = "seatCount")])
                fun carToDto(car: Car): CarDto
            }
            """.trimIndent()
        )
        assertThat(graph.draw.size()).isEqualTo(2)
    }

    @Test
    fun plainInterfaceWithoutKonverterIsNotProvided() {
        super.compile0(
            """
            interface NotAMapper {
                fun map(value: String): String
            }
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp {
                @Root
                fun root(mapper: NotAMapper): String {
                    return ""
                }
            }
            """.trimIndent()
        )
        assertThat(compileResult.isFailed()).isTrue()
    }

    @Test
    fun konverterWithMultipleFunctionsRegistersOnce() {
        val graph = compile(
            """
            data class Car(val make: String, val numberOfSeats: Int)
            """.trimIndent(),
            """
            data class CarDto(val make: String, val seatCount: Int)
            """.trimIndent(),
            """
            @Konverter
            interface CarMapper {
                @Konvert(mappings = [Mapping(source = "numberOfSeats", target = "seatCount")])
                fun carToDto(car: Car): CarDto

                @Konvert(mappings = [Mapping(source = "seatCount", target = "numberOfSeats")])
                fun dtoToCar(dto: CarDto): Car
            }
            """.trimIndent()
        )
        assertThat(graph.draw.size()).isEqualTo(2)
    }

    @Test
    fun taggedKonverterResolvesForMatchingTag() {
        super.compile0(
            """
            class KonvertTag {}
            """.trimIndent(),
            """
            data class Car(val make: String, val numberOfSeats: Int)
            """.trimIndent(),
            """
            data class CarDto(val make: String, val seatCount: Int)
            """.trimIndent(),
            """
            @Konverter
            @Tag(KonvertTag::class)
            interface CarMapper {
                @Konvert(mappings = [Mapping(source = "numberOfSeats", target = "seatCount")])
                fun carToDto(car: Car): CarDto
            }
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp {
                @Root
                fun root(@Tag(KonvertTag::class) mapper: CarMapper): String {
                    return ""
                }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.size()).isEqualTo(2)
    }

    @Test
    fun nestedKonverterInterfaceIsInjectable() {
        super.compile0(
            """
            data class Car(val make: String, val numberOfSeats: Int)
            """.trimIndent(),
            """
            data class CarDto(val make: String, val seatCount: Int)
            """.trimIndent(),
            """
            interface Mappers {
                @Konverter
                interface CarMapper {
                    @Konvert(mappings = [Mapping(source = "numberOfSeats", target = "seatCount")])
                    fun carToDto(car: Car): CarDto
                }
            }
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp {
                @Root
                fun root(mapper: Mappers.CarMapper): String {
                    return ""
                }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.size()).isEqualTo(2)
    }
}
