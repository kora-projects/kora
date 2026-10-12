package io.koraframework.annotation.processor.common;

import org.intellij.lang.annotations.Language;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compiles user sources together with Kora annotation processors the way a user build with
 * {@code -Xlint:all -Werror} does, and returns the warnings javac reports for GENERATED sources.
 * Warnings in the hand-written inputs are returned separately so a test can prove the input is clean.
 */
public abstract class AbstractStrictLintTest extends AbstractAnnotationProcessorTest {
    public record LintResult(List<String> generatedWarnings, List<String> inputWarnings, List<String> otherWarnings, List<String> errors) {}

    private static final Pattern TYPE_NAME = Pattern.compile("(?:class|interface|@interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    protected LintResult compileStrict(List<Processor> processors, @Language("java") String... sources) throws Exception {
        var testPackage = testPackage();
        var dir = Paths.get(".", "build", "in-test-generated", "sources").resolve(testPackage.replace('.', '/'));
        Files.createDirectories(dir);
        var sourceList = new ArrayList<Path>();
        for (var source : sources) {
            var m = TYPE_NAME.matcher(source);
            if (!m.find()) throw new IllegalArgumentException(source);
            var text = "package %s;\n%s\n".formatted(testPackage, commonImports()) + source;
            var path = dir.resolve(m.group(1) + ".java");
            Files.writeString(path, text, StandardCharsets.UTF_8);
            sourceList.add(path);
        }
        var inputs = new HashSet<String>();
        for (var p : sourceList) inputs.add(p.toAbsolutePath().normalize().toUri().toString());
        var jc = new JavaCompilation()
            .withSources(sourceList)
            .withProcessors(processors)
            .withOption("-Xlint:all")
            .withOption("-Xlint:-processing")
            .withOption("-Xlint:-preview")
            .withOption("-Xmaxwarns")
            .withOption("10000")
            .withOption("-Werror");
        try {
            jc.compile();
        } catch (TestUtils.CompilationErrorException ignore) {
        }
        var gen = new ArrayList<String>();
        var in = new ArrayList<String>();
        var err = new ArrayList<String>();
        var other = new ArrayList<String>();
        for (var d : jc.diagnostics()) {
            var srcName = d.getSource() == null ? null : d.getSource().toUri().toString().replaceAll(".*[/!]", "");
            var msg = (srcName == null ? "" : srcName + ":" + d.getLineNumber() + ": ")
                + "[" + d.getCode() + "] " + d.getMessage(Locale.ENGLISH);
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                err.add(msg);
            } else if (d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                if (d.getSource() == null) {
                    other.add(msg);
                    continue;
                }
                if (!"file".equals(d.getSource().toUri().getScheme())) {
                    other.add(d.getSource().toUri() + " " + msg);
                    continue;
                }
                var uri = Path.of(d.getSource().toUri()).toAbsolutePath().normalize().toUri().toString();
                (inputs.contains(uri) ? in : gen).add(msg);
            }
        }
        return new LintResult(gen, in, other, err);
    }

    protected static void assertLintClean(LintResult r) {
        var realErrors = r.errors().stream().filter(e -> !e.contains("warnings found and -Werror specified")).toList();
        if (!realErrors.isEmpty()) {
            throw new AssertionError("compilation failed for a non-lint reason:\n" + String.join("\n", realErrors));
        }
        if (!r.inputWarnings().isEmpty()) {
            throw new AssertionError("hand-written input is not lint-clean:\n" + String.join("\n", r.inputWarnings()));
        }
        if (r.errors().stream().anyMatch(e -> e.contains("warnings found and -Werror specified")) && r.generatedWarnings().isEmpty()) {
            throw new AssertionError("javac -Werror fails because of warnings raised by the annotation processors:\n" + String.join("\n", r.otherWarnings()) + "\n" + String.join("\n", r.errors()));
        }
        if (!r.generatedWarnings().isEmpty()) {
            throw new AssertionError("javac -Xlint:all -Werror fails on GENERATED code:\n" + String.join("\n", r.generatedWarnings()));
        }
    }
}
