package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.*;
import io.koraframework.application.graph.internal.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

public class KoraBigGraphTest extends AbstractKoraAppTest {

    @Test
    public void testColdStart() throws Exception {
        int nodesCount = 1500;
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n");
        for (int i = 0; i < nodesCount; i++) {
            sb.append("  @Root\n");
            sb.append("  default String component").append(i).append("() { return \"\"; }\n");
        }
        sb.append("}\n");

        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(nodesCount);

        var startNano = System.nanoTime();
        var graph = draw.init();
        var durationMs = (System.nanoTime() - startNano) / 1_000_000.0;

        System.out.printf("""
            ==================================================
            ❄️ KORA COLD START GRAPH BENCHMARK ❄️
            ==================================================
            🚀 Nodes Count   : %d
            ⏱️ Startup Time : %.2f ms
            ==================================================
            """, draw.size(), durationMs);

        assertThat(graph).isNotNull();
    }

    @Disabled("For real performance tests")
    @Test
    public void testRealisticEnterpriseGraphColdStart() throws Exception {
        int nodesCount = 10000;
        int layersCount = 5;

        int nodesPerLayer = nodesCount / layersCount;
        int[] nodeToLayerMap = new int[nodesCount];
        List<Integer>[] layerToNodesMap = new List[layersCount];
        for (int i = 0; i < layersCount; i++) {
            layerToNodesMap[i] = new ArrayList<>();
        }

        int currentLayer = 0;
        for (int i = 0; i < nodesCount; i++) {
            if (currentLayer < layersCount - 1 && layerToNodesMap[currentLayer].size() >= nodesPerLayer) {
                currentLayer++;
            }
            nodeToLayerMap[i] = currentLayer;
            layerToNodesMap[currentLayer].add(i);
        }

        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n");

        for (int i = 0; i < nodesCount; i++) {
            sb.append("  interface Comp").append(i).append(" {}\n");
        }

        for (int i = 0; i < nodesCount; i++) {
            int layer = nodeToLayerMap[i];

            if (layer == layersCount - 1 || i == nodesCount - 1) {
                sb.append("  @Root\n");
            }

            sb.append("  @Tag(Comp").append(i).append(".class)\n");
            sb.append("  default String component").append(i).append("(");

            List<Integer> deps = new ArrayList<>();

            if (layer == 0) {
            } else if (layer == 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));
            } else if (layer == layersCount - 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));

                deps.add(i - 1);
            } else {
                deps.add(i - 1);

                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));
                var repoNodes = layerToNodesMap[1];
                deps.add(repoNodes.get(i % repoNodes.size()));
            }

            for (int d = 0; d < deps.size(); d++) {
                int depId = deps.get(d);
                sb.append("@Tag(Comp").append(depId).append(".class) String dep").append(depId);
                if (d < deps.size() - 1) sb.append(", ");
            }

            sb.append(") { return \"\"; }\n");
        }
        sb.append("}\n");

        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(nodesCount);

        // For profiling
//        for (int i = 0; i <= 100000; i++) {
//            var warmupGraph = draw.init();
//            warmupGraph.release();
//        }

        var startNano = System.nanoTime();
        var graph = draw.init();
        var durationMs = (System.nanoTime() - startNano) / 1_000_000.0;

        System.out.printf("""
            ==================================================
            ❄️ KORA COLD START REALISTIC GRAPH BENCHMARK ❄️
            ==================================================
            🚀 Total Nodes    : %d (Realistic Microservice)
            📐 Graph Depth    : %d layers
            ⏱️ Startup Time   : %.2f ms
            ==================================================
            """, draw.size(), layersCount, durationMs);

        assertThat(graph).isNotNull();
        assertThat(graph.get(draw.getNodes().get(nodesCount - 1))).isEqualTo("");
        graph.release();
    }

    @Disabled("For real performance tests")
    @Test
    public void testRealisticEnterpriseGraphRandomRefresh() throws Exception {
        int nodesCount = 150;
        int layersCount = 5;

        int nodesPerLayer = nodesCount / layersCount;
        int[] nodeToLayerMap = new int[nodesCount];
        List<Integer>[] layerToNodesMap = new List[layersCount];
        for (int i = 0; i < layersCount; i++) {
            layerToNodesMap[i] = new ArrayList<>();
        }

        int currentLayer = 0;
        for (int i = 0; i < nodesCount; i++) {
            if (currentLayer < layersCount - 1 && layerToNodesMap[currentLayer].size() >= nodesPerLayer) {
                currentLayer++;
            }
            nodeToLayerMap[i] = currentLayer;
            layerToNodesMap[currentLayer].add(i);
        }

        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n")
            .append("  java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();\n");

        for (int i = 0; i < nodesCount; i++) {
            sb.append("  interface Comp").append(i).append(" {}\n");
        }

        for (int i = 0; i < nodesCount; i++) {
            int layer = nodeToLayerMap[i];

            if (layer == layersCount - 1 || i == nodesCount - 1) {
                sb.append("  @Root\n");
            }

            sb.append("  @Tag(Comp").append(i).append(".class)\n");
            sb.append("  default String component").append(i).append("(");

            List<Integer> deps = new ArrayList<>();

            if (layer == 0) {
            } else if (layer == 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));
            } else if (layer == layersCount - 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));
                deps.add(i - 1);
            } else {
                deps.add(i - 1);
                var propNodes = layerToNodesMap[0];
                deps.add(propNodes.get(i % propNodes.size()));
                var repoNodes = layerToNodesMap[1];
                deps.add(repoNodes.get(i % repoNodes.size()));
            }

            for (int d = 0; d < deps.size(); d++) {
                int depId = deps.get(d);
                sb.append("@Tag(Comp").append(depId).append(".class) String dep").append(depId);
                if (d < deps.size() - 1) sb.append(", ");
            }

            if (layer == 0) {
                sb.append(") { return \"val-\" + nodeIndex").append(i).append("() + \"-\" + counter.incrementAndGet(); }\n")
                    .append("  default int nodeIndex").append(i).append("() { return ")
                    .append(i)
                    .append("; }\n");
            } else {
                sb.append(") {\n")
                    .append("    var sb = new StringBuilder();\n");
                for (int d = 0; d < deps.size(); d++) {
                    sb.append("    sb.append(dep").append(deps.get(d)).append(");\n");
                }
                sb.append("    return sb.toString();\n")
                    .append("  }\n");
            }
        }
        sb.append("}\n");

        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(nodesCount);

        var graph = draw.init();
        var random = new Random(42);
        var rootNode = draw.getNodes().get(nodesCount - 1);
        var layer0Nodes = layerToNodesMap[0];

        for (int i = 0; i < 10000; i++) {
            var randomNode = draw.getNodes().get(layer0Nodes.get(random.nextInt(layer0Nodes.size())));

            graph.refresh(randomNode);

            String newValue = (String) graph.get(randomNode);
            String afterRootValue = (String) graph.get(rootNode);

            assertThat(afterRootValue).contains(newValue);
        }

        var finalRandomNode = (NodeImpl<?>) draw.getNodes().get(layer0Nodes.get(random.nextInt(layer0Nodes.size())));

        var startNano = System.nanoTime();
        graph.refresh(finalRandomNode);
        var durationMs = (System.nanoTime() - startNano) / 1_000_000.0;

        System.out.printf("""
            ==================================================
            🎲 KORA RANDOM REFRESH PERFORMANCE BENCHMARK 🎲
            ==================================================
            🚀 Total Nodes      : %d
            📐 Graph Depth      : %d layers
            🔔 Triggered From   : Node index %d
            ⏱️ Refresh Time     : %.4f ms
            ==================================================
            """, draw.size(), layersCount, finalRandomNode.index, durationMs);

        assertThat(graph).isNotNull();
        graph.release();
    }

    @Test
    public void testPureRealisticGraphDrawBuildTime() {
        int nodesCount = 150;
        int layersCount = 5;

        int nodesPerLayer = nodesCount / layersCount;
        int[] nodeToLayerMap = new int[nodesCount];
        List<Integer>[] layerToNodesMap = new List[layersCount];
        for (int i = 0; i < layersCount; i++) {
            layerToNodesMap[i] = new ArrayList<>();
        }

        int currentLayer = 0;
        int expectedCreateEdges = 0;
        for (int i = 0; i < nodesCount; i++) {
            if (currentLayer < layersCount - 1 && layerToNodesMap[currentLayer].size() >= nodesPerLayer) {
                currentLayer++;
            }
            nodeToLayerMap[i] = currentLayer;
            layerToNodesMap[currentLayer].add(i);

            if (currentLayer == 0) {
            } else if (currentLayer == 1 || currentLayer == layersCount - 1) {
                expectedCreateEdges += currentLayer == 1 ? 1 : 2;
            } else {
                expectedCreateEdges += 3;
            }
        }

        var startPureNano = System.nanoTime();

        var draw = new ApplicationGraphDraw(ExampleApplication.class, nodesCount, expectedCreateEdges, 0, 0, expectedCreateEdges);
        List<Node<?>> allNodes = new ArrayList<>(nodesCount);

        for (int i = 0; i < nodesCount; i++) {
            int layer = nodeToLayerMap[i];
            List<Node<?>> deps = new ArrayList<>();

            if (layer == 0) {
            } else if (layer == 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(allNodes.get(propNodes.get(i % propNodes.size())));
            } else if (layer == layersCount - 1) {
                var propNodes = layerToNodesMap[0];
                deps.add(allNodes.get(propNodes.get(i % propNodes.size())));
                deps.add(allNodes.get(i - 1));
            } else {
                deps.add(allNodes.get(i - 1));
                var propNodes = layerToNodesMap[0];
                deps.add(allNodes.get(propNodes.get(i % propNodes.size())));
                var repoNodes = layerToNodesMap[1];
                deps.add(allNodes.get(repoNodes.get(i % repoNodes.size())));
            }

            Type fakeType = new FakeType(i);
            Class<?> fakeTag = FakeTag.class;

            int[] createDeps = null;
            if (!deps.isEmpty()) {
                createDeps = new int[deps.size()];
                for (int d = 0; d < deps.size(); d++) {
                    createDeps[d] = ((NodeImpl<?>) deps.get(d)).index;
                }
            }

            var node = draw.addNode(fakeType, fakeTag, null, createDeps, null, null, null, graph -> "");
            allNodes.add(node);

            draw.findNodeByType(fakeType);
        }

        var totalPureNano = System.nanoTime() - startPureNano;

        var durationMs = totalPureNano / 1_000_000.0;
        var avgPerNodeNano = totalPureNano / nodesCount;

        System.out.printf("""
        ==================================================
        🎯 PURE KORA GRAPH DRAW BENCHMARK (NO JAVAC) 🎯
        ==================================================
        🚀 Total Nodes        : %d
        📐 Graph Depth        : %d layers
        ⏱️ Clean Draw Time    : %.4f ms
        📊 Avg Time per Node  : %d ns
        ==================================================
        """, draw.size(), layersCount, durationMs, avgPerNodeNano);

        assertThat(draw.getNodes()).hasSize(nodesCount);
    }

    private interface ExampleApplication {}
    private interface FakeTag {}
    private record FakeType(int id) implements Type {
        @Override public String getTypeName() { return "Comp" + id; }
    }
}
