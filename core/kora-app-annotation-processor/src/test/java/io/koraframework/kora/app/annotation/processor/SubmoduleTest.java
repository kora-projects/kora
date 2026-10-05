package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.JavaCompilation;
import io.koraframework.application.graph.ApplicationGraphDraw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The @KoraSubmodule is compiled on its own, like a separate Gradle module, and the @KoraApp is compiled against its classes.
 */
class SubmoduleTest {
    private static final String IMPORTS = """
        import io.koraframework.common.annotation.*;
        import io.koraframework.common.annotation.Module;
        import io.koraframework.application.graph.*;
        import org.jspecify.annotations.Nullable;
        """;

    private static final String SUBMODULE = """
        @KoraSubmodule
        public interface Feature {}
        """;

    private static final String OFF_CONDITION = """
        public class OffCondition implements GraphCondition {
            @Override
            public ConditionResult eval() { return new ConditionResult.Failed("off"); }
        }
        """;

    @Test
    void recordComponent(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            @Component
            public record Svc(Long l) {}
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                default Long l() { return 5L; }
                @Root
                default String root(Svc s) { return "" + s.l(); }
            }
            """);
        assertThat(root).isEqualTo("5");
    }

    @Test
    void conditionalComponent(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, OFF_CONDITION, """
            @Component
            @Conditional(tag = OffCondition.class)
            public class DisabledService {}
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Tag(OffCondition.class)
                default GraphCondition off() { return new OffCondition(); }
                @Root
                default String root(@Nullable DisabledService s) { return String.valueOf(s); }
            }
            """);
        assertThat(root).isEqualTo("null");
    }

    @Test
    void conditionalModuleMethod(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, OFF_CONDITION, """
            @Module
            public interface FeatureModule {
                @Conditional(tag = OffCondition.class)
                default Long disabled() { return 1L; }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Tag(OffCondition.class)
                default GraphCondition off() { return new OffCondition(); }
                @Root
                default String root(@Nullable Long l) { return String.valueOf(l); }
            }
            """);
        assertThat(root).isEqualTo("null");
    }

    @Test
    void defaultComponentClassCanBeOverridden(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            public interface Greeter { String hi(); }
            """, """
            @Component
            @DefaultComponent
            public class DefaultGreeter implements Greeter { public String hi() { return "default"; } }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                default Greeter custom() { return () -> "custom"; }
                @Root
                default String root(Greeter g) { return g.hi(); }
            }
            """);
        assertThat(root).isEqualTo("custom");
    }

    @Test
    void factoryModuleMethod(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            public class InnerModule {
                public Long l() { return 7L; }
            }
            """, """
            @Module
            public interface FeatureModule {
                @FactoryModule
                default InnerModule inner() { return new InnerModule(); }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(Long l) { return "" + l; }
            }
            """);
        assertThat(root).isEqualTo("7");
    }

    @Test
    void inheritedModuleMethods(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            public interface BaseModule {
                default Long base() { return 7L; }
                default Integer overridden() { return 1; }
            }
            """, """
            @Module
            public interface FeatureModule extends BaseModule {
                @Override
                default Integer overridden() { return 8; }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(Long l, Integer i) { return l + ":" + i; }
            }
            """);
        assertThat(root).isEqualTo("7:8");
    }

    @Test
    void throwingConstructor(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            @Component
            public class FileBackedService { public FileBackedService() throws java.io.IOException {} }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(FileBackedService s) { return "ok"; }
            }
            """);
        assertThat(root).isEqualTo("ok");
    }

    @Test
    void throwingModuleMethod(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            @Module
            public interface FeatureModule {
                default Long l() throws java.io.IOException { return 1L; }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(Long l) { return "" + l; }
            }
            """);
        assertThat(root).isEqualTo("1");
    }

    @Test
    void submoduleModuleExtendsAnnotatedModule(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            @Module
            public interface ParentModule {
                default Long l() { return 5L; }
            }
            """, """
            @Module
            public interface ChildModule extends ParentModule {
                default Integer i() { return 1; }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(Long l, Integer i) { return l + "" + i; }
            }
            """);
        assertThat(root).isEqualTo("51");
    }

    @Test
    void submoduleModuleExtendsGenericInterfaceUnused(TestInfo info) throws Exception {
        var root = compileAndGetRoot(info, List.of(SUBMODULE, """
            public interface Base<T> {
                default java.util.List<T> list() { return java.util.List.of(); }
            }
            """, """
            @Module
            public interface ChildModule extends Base<String> {
                default Integer i() { return 1; }
            }
            """), """
            @KoraApp
            public interface ExampleApplication extends Feature {
                @Root
                default String root(Integer i) { return "" + i; }
            }
            """);
        assertThat(root).isEqualTo("1");
    }

    private Object compileAndGetRoot(TestInfo info, List<String> submoduleSources, String appSource) throws Exception {
        var name = info.getTestMethod().orElseThrow().getName();
        var pkg = "submoduletest." + name;
        var base = Path.of("build/in-test-generated/submodule/" + name);
        new JavaCompilation()
            .withSources(writeSources(base.resolve("sub-src"), pkg, submoduleSources))
            .withTargetClassesDir(base.resolve("sub-classes"))
            .withGeneratedSourcesDir(base.resolve("sub-gen"))
            .withProcessor(new KoraAppProcessor(), new KoraSubmoduleProcessor())
            .compile();
        var cl = new JavaCompilation()
            .withSources(writeSources(base.resolve("app-src"), pkg, List.of(appSource)))
            .withClassPathEntry(base.resolve("sub-classes"))
            .withTargetClassesDir(base.resolve("app-classes"))
            .withGeneratedSourcesDir(base.resolve("app-gen"))
            .withProcessor(new KoraAppProcessor(), new KoraSubmoduleProcessor())
            .compile();
        @SuppressWarnings("unchecked")
        var draw = ((Supplier<ApplicationGraphDraw>) cl.loadClass(pkg + ".ExampleApplicationGraph").getConstructor().newInstance()).get();
        var graph = draw.init();
        var rootNode = draw.getNodes().stream().filter(n -> n.type().equals(String.class)).findFirst().orElseThrow();
        return graph.get(rootNode);
    }

    private static List<Path> writeSources(Path dir, String pkg, List<String> sources) throws Exception {
        var result = new ArrayList<Path>();
        var pkgDir = dir.resolve(pkg.replace('.', '/'));
        Files.createDirectories(pkgDir);
        for (var source : sources) {
            var m = Pattern.compile("(?:class|interface|record)\\s+(\\w+)").matcher(source);
            if (!m.find()) {
                throw new IllegalArgumentException(source);
            }
            var file = pkgDir.resolve(m.group(1) + ".java");
            Files.writeString(file, "package " + pkg + ";\n" + IMPORTS + source);
            result.add(file);
        }
        return result;
    }
}
