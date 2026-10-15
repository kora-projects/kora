package io.koraframework.kora.app.ksp

import io.koraframework.application.graph.*
import io.koraframework.ksp.common.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.function.*
import kotlin.math.*

class KoraBigGraphTest : AbstractSymbolProcessorTest() {
    @Test
    fun test() {
        val nodesCount = 1500
        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")
        for (i in 0 until nodesCount) {
            sb.append("  @Root\n")
            sb.append("  fun component").append(i).append("() = \"\"\n")
        }
        sb.append("}\n")
        compile0(listOf(KoraAppProcessorProvider()), sb.toString())
            .assertSuccess()

        val appClass = loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        val draw = `object`.get()
        assertThat(draw.nodes).hasSize(nodesCount)

        val startNano = System.nanoTime()
        val graph = draw.init()
        val durationMs = (System.nanoTime() - startNano) / 1_000_000.0

        println(
            """
            ==================================================
            ❄️ KORA COLD START GRAPH BENCHMARK ❄️
            ==================================================
            🚀 Nodes Count   : ${draw.size()}
            ⏱️ Startup Time : ${round(durationMs * 100) / 100} ms
            ==================================================
            """.trimIndent()
        )

        assertThat(graph).isNotNull()
    }

    @Disabled("For real performance tests")
    @Test
    fun testRealisticEnterpriseGraphColdStart() {
        val nodesCount = 150
        val layersCount = 5

        val nodesPerLayer = nodesCount / layersCount
        val nodeToLayerMap = IntArray(nodesCount)
        val layerToNodesMap = Array(layersCount) { ArrayList<Int>() }

        var currentLayer = 0
        for (i in 0 until nodesCount) {
            if (currentLayer < layersCount - 1 && layerToNodesMap[currentLayer].size >= nodesPerLayer) {
                currentLayer++
            }
            nodeToLayerMap[i] = currentLayer
            layerToNodesMap[currentLayer].add(i)
        }

        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")

        for (i in 0 until nodesCount) {
            sb.append("  interface Comp").append(i).append(" {}\n")
        }

        for (i in 0 until nodesCount) {
            val layer = nodeToLayerMap[i]

            if (layer == layersCount - 1 || i == nodesCount - 1) {
                sb.append("  @Root\n")
            }

            sb.append("  @Tag(Comp").append(i).append("::class)\n")
            sb.append("  fun component").append(i).append("(")

            val deps = ArrayList<Int>()

            if (layer == 0) {
            } else if (layer == 1) {
                val propNodes = layerToNodesMap[0]
                deps.add(propNodes[i % propNodes.size])
            } else if (layer == layersCount - 1) {
                val propNodes = layerToNodesMap[0]
                deps.add(propNodes[i % propNodes.size])
                deps.add(i - 1)
            } else {
                deps.add(i - 1)
                val propNodes = layerToNodesMap[0]
                deps.add(propNodes[i % propNodes.size])
                val repoNodes = layerToNodesMap[1]
                deps.add(repoNodes[i % repoNodes.size])
            }

            for ((d, index) in deps.withIndex()) {
                sb.append("@Tag(Comp").append(index).append("::class) dep").append(index).append(": String")
                if (d < deps.size - 1) {
                    sb.append(", ")
                }
            }

            sb.append(") = \"\"\n")
        }
        sb.append("}\n")

        compile0(listOf(KoraAppProcessorProvider()), sb.toString())
            .assertSuccess()

        val appClass = loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        val draw = `object`.get()
        assertThat(draw.nodes).hasSize(nodesCount)

        val startNano = System.nanoTime()
        val graph = draw.init()
        val durationMs = (System.nanoTime() - startNano) / 1_000_000.0

        // For profiling
//        (0..1000).forEach { _ ->
//            val warmupGraph = draw.init();
//            warmupGraph.release();
//        }

        println(
            """
            ==================================================
            ❄️ KORA COLD START REALISTIC GRAPH BENCHMARK ❄️
            ==================================================
            🚀 Total Nodes    : ${draw.size()} (Realistic Microservice)
            📐 Graph Depth    : $layersCount layers
            ⏱️ Startup Time   : ${round(durationMs * 100) / 100} ms
            ==================================================
            """.trimIndent()
        )

        assertThat(graph).isNotNull
        graph.release()
    }
}
