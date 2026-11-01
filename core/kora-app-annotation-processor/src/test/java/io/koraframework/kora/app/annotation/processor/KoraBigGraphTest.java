package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class KoraBigGraphTest extends AbstractKoraAppTest {
    @Test
    public void test() {
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n");
        for (int i = 0; i < 1500; i++) {
            sb.append("  @Root\n");
            sb.append("  default String declaration").append(i).append("() { return \"\"; }\n");
        }
        sb.append("}\n");
        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(1500);
        draw.init();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testWideAll() {
        var items = 4000;
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n")
            .append("  interface Handler {}\n")
            .append("  class HandlerImpl implements Handler {}\n")
            .append("  class Consumer { Consumer(int size) { if (size != ").append(items).append(") throw new IllegalStateException(\"size \" + size); } }\n")
            .append("  static <T> int size(All<T> all) { int i = 0; for (var item : all) i++; return i; }\n")
            .append("  @Root\n")
            .append("  default Consumer all(All<Handler> all) { return new Consumer(size(all)); }\n")
            .append("  @Root\n")
            .append("  default Consumer allValues(All<ValueOf<Handler>> all) { return new Consumer(size(all)); }\n")
            .append("  @Root\n")
            .append("  default Consumer allPromises(All<PromiseOf<Handler>> all) { return new Consumer(size(all)); }\n");
        for (int i = 0; i < items; i++) {
            if (i % 2 == 0) {
                sb.append("  default Handler handler").append(i).append("() { return new Handler() {}; }\n");
            } else {
                sb.append("  default HandlerImpl handler").append(i).append("() { return new HandlerImpl(); }\n");
            }
        }
        sb.append("}\n");
        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(items + 3);
        draw.init();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testHolderConstructorCodeSize() {
        // every consumer has 600 node references in its dependency lists, 500 of such components do not fit into one method
        var items = 300;
        var consumers = 80;
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n")
            .append("  interface Handler {}\n")
            .append("  interface GenericHandler<T> extends Handler {}\n")
            .append("  class Consumer {}\n");
        for (int i = 0; i < items; i++) {
            if (i % 2 == 0) {
                sb.append("  default Handler handler").append(i).append("() { return new Handler() {}; }\n");
            } else {
                sb.append("  default GenericHandler<java.util.List<? extends String>> handler").append(i).append("() { return new GenericHandler<>() {}; }\n");
            }
        }
        for (int i = 0; i < consumers; i++) {
            sb.append("  @Root\n");
            sb.append("  default Consumer consumer").append(i).append("(All<Handler> all) { return new Consumer(); }\n");
        }
        sb.append("}\n");
        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(items + consumers);
        var types = draw.getNodes().stream().map(Node::type).toList();
        assertThat(types).contains(compileResult.loadClass("ExampleApplication$Handler"));
        assertThat(types).filteredOn(t -> t instanceof java.lang.reflect.ParameterizedType)
            .hasSize(items / 2)
            .allSatisfy(t -> assertThat(t.getTypeName()).endsWith("ExampleApplication$GenericHandler<java.util.List<? extends java.lang.String>>"));
        draw.init();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testWideAllOfGenericAndWrapped() {
        var items = 300;
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication {\n")
            .append("  interface Cache<K, V> {}\n")
            .append("  class StringCache implements Cache<String, String> {}\n")
            .append("  class IntCache implements Cache<String, Integer> {}\n")
            .append("  class Consumer { Consumer(int size) { if (size != ").append(items).append(") throw new IllegalStateException(\"size \" + size); } }\n")
            .append("  static <T> int size(All<T> all) { int i = 0; for (var item : all) i++; return i; }\n")
            .append("  @Root\n")
            .append("  default Consumer all(All<Cache<String, ?>> all) { return new Consumer(size(all)); }\n")
            .append("  @Root\n")
            .append("  default Consumer allValues(All<ValueOf<Cache<String, ?>>> all) { return new Consumer(size(all)); }\n");
        for (int i = 0; i < items; i++) {
            switch (i % 3) {
                case 0 -> sb.append("  default StringCache cache").append(i).append("() { return new StringCache(); }\n");
                case 1 -> sb.append("  default IntCache cache").append(i).append("() { return new IntCache(); }\n");
                default -> sb.append("  default Wrapped<Cache<String, ?>> cache").append(i).append("() { return () -> new IntCache(); }\n");
            }
        }
        sb.append("}\n");
        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(items + 2);
        draw.init();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testWideAllOfInaccessibleType() {
        var items = 1000;
        var sb = new StringBuilder("\n")
            .append("@KoraApp\n")
            .append("public interface ExampleApplication extends io.koraframework.kora.app.annotation.processor.otherpackage.HiddenHandlerModule {\n")
            .append("  @Root\n")
            .append("  default String root(Handlers handlers) { if (handlers.size() != ").append(items).append(") throw new IllegalStateException(\"size \" + handlers.size()); return \"\"; }\n");
        for (int i = 0; i < items; i++) {
            sb.append("  default PublicHandler handler").append(i).append("() { return new PublicHandler(); }\n");
        }
        sb.append("}\n");
        var draw = compile(sb.toString());
        assertThat(draw.getNodes()).hasSize(items + 2);
        draw.init();
    }
}
