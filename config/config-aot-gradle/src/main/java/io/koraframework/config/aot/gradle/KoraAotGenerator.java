package io.koraframework.config.aot.gradle;

import io.github.classgraph.*;
import io.koraframework.config.aot.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class KoraAotGenerator {
    private static final int MAX_FIELDS_PER_CHUNK = 350;
    private static final String ANNOTATION_NAME = "io.koraframework.config.common.annotation.ConfigSource";

    private final Set<File> javaSourceDirs;
    private final List<File> classpathFiles;
    private final String configResource;
    private final String configFile;
    private final String configFormat;
    private final boolean configEnabled; // Новый флаг
    private final boolean flywayEnabled; // Новый флаг
    private final File outputDir;
    private final AppLogger logger;

    public KoraAotGenerator(Set<File> javaSourceDirs,
                            List<File> classpathFiles,
                            String configResource,
                            String configFile,
                            String configFormat,
                            boolean configEnabled,
                            boolean flywayEnabled,
                            File outputDir,
                            AppLogger logger) {
        this.javaSourceDirs = javaSourceDirs;
        this.classpathFiles = classpathFiles;
        this.configResource = configResource;
        this.configFile = configFile;
        this.configFormat = configFormat;
        this.configEnabled = configEnabled;
        this.flywayEnabled = flywayEnabled;
        this.outputDir = outputDir;
        this.logger = logger;
    }

    public void execute() throws Exception {
        if (outputDir.exists()) {
            delete(outputDir);
        }
        outputDir.mkdirs();

        try (var scanResult = new ClassGraph()
            .overrideClasspath(classpathFiles)
            .enableAnnotationInfo()
            .enableMethodInfo()
            .enableClassInfo()
            .enableExternalClasses()
            .scan()) {

            boolean hasConfig = false;
            if (configEnabled) {
                hasConfig = processConfig(scanResult);
            } else {
                logger.lifecycle("[Kora-Aot] Configuration processing is explicitly disabled.");
            }

            boolean hasFlyway = false;
            if (flywayEnabled) {
                if (scanResult.getClassInfo("org.flywaydb.core.Flyway") != null) {
                    hasFlyway = checkFlywayMigrations(scanResult);
                    if (hasFlyway) {
                        logger.lifecycle("[Kora-Aot] Flyway migrations detected. Generating AOT Flyway module.");
                        generateAotFlywayModule(scanResult, outputDir);
                    } else {
                        logger.lifecycle("[Kora-Aot] No Flyway migrations found. Skipping Flyway AOT generation.");
                    }
                } else {
                    logger.lifecycle("[Kora-Aot] Flyway classes not found in classpath. Skipping Flyway AOT generation.");
                }
            } else {
                logger.lifecycle("[Kora-Aot] Flyway processing is explicitly disabled.");
            }

            if (!hasConfig && !hasFlyway) {
                logger.lifecycle("[Kora-Aot] No custom AOT enhancements found. Generating blank MainAotModule.");
            } else {
                logger.lifecycle("[Kora-Aot] Generating MainAotModule with detected features.");
            }

            generateMainAotModule(outputDir, hasConfig, hasFlyway);
        }
    }

    private boolean processConfig(ScanResult scanResult) throws Exception {
        var targetClasses = new LinkedHashSet<String>();
        scanSources(targetClasses);

        if (!classpathFiles.isEmpty()) {
            targetClasses.addAll(scanResult.getClassesWithAnnotation(ANNOTATION_NAME).getNames());
        }

        if (targetClasses.isEmpty()) {
            logger.lifecycle("[Kora-Aot] No configuration classes found. Skipping AotConfigModule.");
            return false;
        }

        logger.lifecycle("[Kora-Aot] Found " + targetClasses.size() + " configuration classes to process.");

        var urls = new ArrayList<URL>();
        for (var file : classpathFiles) {
            urls.add(file.toURI().toURL());
        }

        try (var projectClassLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader())) {
            var format = configFormat;
            var file = configFile;

            if (format == null || format.isBlank()) {
                if (file != null && !file.isBlank()) {
                    var lower = file.toLowerCase();
                    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                        format = "YAML";
                    } else if (lower.endsWith(".conf") || lower.endsWith(".json")) {
                        format = "HOCON";
                    }
                } else {
                    var resource = configResource != null ? configResource : "application";
                    if (projectClassLoader.getResource(resource + ".yaml") != null ||
                        projectClassLoader.getResource(resource + ".yml") != null) {
                        format = "YAML";
                    } else if (projectClassLoader.getResource(resource + ".conf") != null ||
                        projectClassLoader.getResource(resource + ".json") != null) {
                        format = "HOCON";
                    }
                }
            }

            if (format == null || format.isBlank()) {
                format = "HOCON";
            }

            var configParser = "YAML".equalsIgnoreCase(format) ? new YamlAotParser() : new HoconAotParser();
            configParser.loadAndMerge(projectClassLoader, configResource, configFile);

            generateAotConfigModule(configParser, outputDir);
        }
        return true;
    }

    private boolean checkFlywayMigrations(ScanResult scanResult) {
        var sqlResources = scanResult.getResourcesMatchingPattern(Pattern.compile("db/migration/.*\\.sql"));
        return !sqlResources.isEmpty();
    }

    private void generateMainAotModule(File outputDir, boolean hasConfig, boolean hasFlyway) throws Exception {
        var packageName = "io.koraframework.config.aot.generated";
        var moduleName = "AotModule";

        var extendsList = new ArrayList<String>();
        if (hasConfig) extendsList.add("AotConfigModule");
        if (hasFlyway) extendsList.add("AotFlywayJdbcDatabaseModule");

        String extendsClause = extendsList.isEmpty() ? "" : "extends " + String.join(", ", extendsList);

        var template = readTemplate("AotModule.java");
        var finalCode = template.replace("// EXTENDS_INJECTION_POINT", extendsClause);

        var targetSubDir = outputDir.toPath().resolve(packageName.replace('.', '/'));
        Files.createDirectories(targetSubDir);
        Files.writeString(targetSubDir.resolve(moduleName + ".java"), finalCode);
    }

    private String readTemplate(String templateName) throws IOException {
        try (var is = getClass().getResourceAsStream("/templates/" + templateName)) {
            if (is == null) {
                throw new FileNotFoundException("Template " + templateName + " not found in plugin resources");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void generateAotConfigModule(AotConfigParser parser, File outputDir) throws Exception {
        var packageName = "io.koraframework.config.aot.generated";
        var moduleName = "AotConfigModule";

        var globalFlattenedValues = new LinkedHashMap<String, Object>();
        collectFlattenedProperties(parser, "", globalFlattenedValues);

        var fillDataBuilder = new StringBuilder();
        var chunksBuilder = new StringBuilder();

        if (!globalFlattenedValues.isEmpty()) {
            var entries = new ArrayList<>(globalFlattenedValues.entrySet());
            var currentChunkIndex = 0;
            var currentChunkBuilder = new StringBuilder();

            for (var i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var fullKey = entry.getKey();
                var baseVal = entry.getValue();

                String statement = switch (baseVal) {
                    case null -> "null";
                    case Boolean b -> b.toString();
                    case Long l -> l + "L";
                    case Number n -> n.toString();
                    case String s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                    case List<?> list -> {
                        var listBuilder = new StringBuilder("java.util.List.of(");
                        for (var j = 0; j < list.size(); j++) {
                            var item = list.get(j);
                            if (item instanceof String) {listBuilder.append("\"").append(item.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");} else if (item instanceof Long) {
                                listBuilder.append(item).append("L");
                            } else {listBuilder.append(item);}
                            if (j < list.size() - 1) listBuilder.append(", ");
                        }
                        yield listBuilder.append(")").toString();
                    }
                    default -> "\"" + baseVal.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                };

                var pathBuilder = new StringBuilder("r");
                int start = 0;
                int dotIdx;
                while ((dotIdx = fullKey.indexOf('.', start)) != -1) {
                    pathBuilder.append(".child(\"").append(fullKey.substring(start, dotIdx)).append("\")");
                    start = dotIdx + 1;
                }
                pathBuilder.append(".child(\"").append(fullKey.substring(start)).append("\")");

                currentChunkBuilder.append("            root.put(\"").append(fullKey).append("\", new AotLeaf(").append(pathBuilder).append(", ").append(statement).append("));\n");

                if ((i + 1) % MAX_FIELDS_PER_CHUNK == 0 || (i + 1) == entries.size()) {
                    var chunkMethodName = "initChunk" + currentChunkIndex;

                    chunksBuilder.append("        static void ").append(chunkMethodName).append("(Map<String, Object> root) {\n")
                        .append("            var r = ConfigValuePath.root();\n")
                        .append(currentChunkBuilder)
                        .append("        }\n\n");

                    fillDataBuilder.append("            ").append(chunkMethodName).append("(root);\n");

                    currentChunkBuilder = new StringBuilder();
                    currentChunkIndex++;
                }
            }
        }

        var template = readTemplate("AotConfigModule.java");
        var finalCode = template
            .replace("// TARGET_INJECTION_POINT", fillDataBuilder.toString().trim())
            .replace("// CHUNKS_INJECTION_POINT", chunksBuilder.toString().trim());

        var targetSubDir = outputDir.toPath().resolve(packageName.replace('.', '/'));
        Files.createDirectories(targetSubDir);
        Files.writeString(targetSubDir.resolve(moduleName + ".java"), finalCode);
    }

    private void generateAotFlywayModule(ScanResult scanResult, File outputDir) throws Exception {
        var packageName = "io.koraframework.config.aot.generated";
        var sqlResources = scanResult.getResourcesMatchingPattern(Pattern.compile("db/migration/.*\\.sql"));

        var targetSubDir = outputDir.toPath().resolve(packageName.replace('.', '/'));
        Files.createDirectories(targetSubDir);

        var flywayJdbcSubDir = outputDir.toPath().resolve("org/flywaydb/core/internal/jdbc");
        Files.createDirectories(flywayJdbcSubDir);
        var flywayParserSubDir = outputDir.toPath().resolve("org/flywaydb/core/internal/parser");
        Files.createDirectories(flywayParserSubDir);
        var flywayUtilSubDir = outputDir.toPath().resolve("org/flywaydb/core/internal/util");
        Files.createDirectories(flywayUtilSubDir);

        Files.writeString(targetSubDir.resolve("AotFlywayJdbcDatabaseModule.java"), readTemplate("AotFlywayJdbcDatabaseModule.java"));
        Files.writeString(targetSubDir.resolve("AotFlywayFactoryModule.java"), readTemplate("AotFlywayFactoryModule.java"));
        Files.writeString(targetSubDir.resolve("AotClassicConfiguration.java"), readTemplate("AotClassicConfiguration.java"));
        Files.writeString(flywayJdbcSubDir.resolve("JdbcConnectionFactory.java"), readTemplate("JdbcConnectionFactory.java"));
        Files.writeString(flywayParserSubDir.resolve("ParsingContext.java"), readTemplate("ParsingContext.java"));
        Files.writeString(flywayUtilSubDir.resolve("FlywayDbWebsiteLinks.java"), readTemplate("FlywayDbWebsiteLinks.java"));

        var chunksMethods = new StringBuilder();
        var listInit = new StringBuilder();

        final int MAX_STRING_CHUNK_LENGTH = 16384;
        final int MAX_METHOD_BYTE_SIZE = 60000;

        int totalMigrationsCount = sqlResources.size();
        int migrationIndex = 0;
        int currentMethodIndex = 0;
        int currentMethodEstimatedSize = 0;

        String sqlMigrationPrefix = "V";
        String repeatableSqlMigrationPrefix = "R";
        String sqlMigrationSeparator = "__";

        var chunkMethodsList = new ArrayList<String>();
        var currentChunkBuilder = new StringBuilder();

        for (var res : sqlResources) {
            String path = res.getPath();
            String filename = path.substring(path.lastIndexOf('/') + 1);

            var crc32 = new CRC32();
            var contentBuilder = new StringBuilder();

            try (var is = res.open();
                 var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        if (line.startsWith("\uFEFF")) {
                            line = line.substring(1);
                        }
                        firstLine = false;
                    } else {
                        contentBuilder.append('\n');
                    }
                    crc32.update(line.getBytes(StandardCharsets.UTF_8));
                    contentBuilder.append(line);
                }
            }

            int checksum = (int) crc32.getValue();
            String content = contentBuilder.toString();

            String cleanName = filename.endsWith(".sql") ? filename.substring(0, filename.length() - 4) : filename;
            String version = "";
            String description = cleanName;

            if (cleanName.startsWith(sqlMigrationPrefix)) {
                int sepIdx = cleanName.indexOf(sqlMigrationSeparator);
                if (sepIdx != -1) {
                    version = cleanName.substring(sqlMigrationPrefix.length(), sepIdx);
                    description = cleanName.substring(sepIdx + sqlMigrationSeparator.length()).replace("_", " ");
                }
            } else if (cleanName.startsWith(repeatableSqlMigrationPrefix)) {
                version = null;
                description = cleanName.substring(repeatableSqlMigrationPrefix.length()).replace("_", " ");
            }

            String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");

            int length = escaped.length();
            var chunkMethodNames = new ArrayList<String>();
            int chunkIndex = 0;

            for (int i = 0; i < length; i += MAX_STRING_CHUNK_LENGTH) {
                String chunk = escaped.substring(i, Math.min(length, i + MAX_STRING_CHUNK_LENGTH));
                String chunkMethodName = "getMigration_" + migrationIndex + "_chunk_" + chunkIndex;
                chunkMethodNames.add(chunkMethodName);

                chunksMethods.append("    private static String ").append(chunkMethodName).append("() {\n")
                    .append("        return \"").append(chunk).append("\";\n")
                    .append("    }\n\n");

                chunkIndex++;
            }

            String mainMethodName = "getMigrationContent_" + migrationIndex;
            chunksMethods.append("    private static String ").append(mainMethodName).append("() {\n")
                .append("        var sb = new StringBuilder(").append(length).append(");\n");
            for (String chunkMethod : chunkMethodNames) {
                chunksMethods.append("        sb.append(").append(chunkMethod).append("());\n");
            }
            chunksMethods.append("        return sb.toString();\n")
                .append("    }\n\n");

            var statementBuilder = new StringBuilder();
            statementBuilder.append("        list.add(new MigrationData(\"")
                .append(path).append("\", \"")
                .append(filename).append("\", ")
                .append(mainMethodName).append("(), ")
                .append(version == null ? "null" : "\"" + version + "\"").append(", \"")
                .append(description).append("\", ")
                .append(checksum).append(", \"")
                .append(path).append("\"));\n");

            String statement = statementBuilder.toString();
            int statementSize = statement.getBytes(StandardCharsets.UTF_8).length;

            if (currentMethodEstimatedSize + statementSize > MAX_METHOD_BYTE_SIZE) {
                String methodName = "addMigrations_chunk_" + currentMethodIndex;
                chunkMethodsList.add(methodName);

                chunksMethods.append("    private static void ").append(methodName).append("(List<MigrationData> list) {\n")
                    .append(currentChunkBuilder)
                    .append("    }\n\n");

                currentChunkBuilder.setLength(0);
                currentMethodEstimatedSize = 0;
                currentMethodIndex++;
            }

            currentChunkBuilder.append(statement);
            currentMethodEstimatedSize += statementSize;
            migrationIndex++;
        }

        if (currentChunkBuilder.length() > 0) {
            String methodName = "addMigrations_chunk_" + currentMethodIndex;
            chunkMethodsList.add(methodName);

            chunksMethods.append("    private static void ").append(methodName).append("(List<MigrationData> list) {\n")
                .append(currentChunkBuilder)
                .append("    }\n\n");
        }

        for (String methodName : chunkMethodsList) {
            listInit.append("        ").append(methodName).append("(list);\n");
        }

        var migrationsInit = new StringBuilder();
        migrationsInit.append("var list = new ArrayList<MigrationData>(").append(totalMigrationsCount).append(");\n");
        for (String methodName : chunkMethodsList) {
            migrationsInit.append("        ").append(methodName).append("(list);\n");
        }

        var migrationsTemplate = readTemplate("AotFlywayMigrations.java");
        var finalMigrationsCode = migrationsTemplate
            .replace("// CHUNKS_METHODS_POINT", chunksMethods.toString().trim())
            .replace("// MIGRATIONS_INIT_POINT", migrationsInit.toString().trim());

        Files.writeString(targetSubDir.resolve("AotFlywayMigrations.java"), finalMigrationsCode);
    }

    private void scanSources(Set<String> targetClasses) {
        var packagePattern = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9._]+);");
        var interfacePattern = Pattern.compile("public\\s+interface\\s+([a-zA-Z0-9_]+)");

        for (var srcDir : javaSourceDirs) {
            if (!srcDir.exists()) continue;
            try (var stream = Files.walk(srcDir.toPath())) {
                stream.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                    try {
                        var content = Files.readString(path);
                        if (content.contains("@ConfigSource")) {
                            var pkgMatcher = packagePattern.matcher(content);
                            var packageName = pkgMatcher.find() ? pkgMatcher.group(1) : "";
                            var intMatcher = interfacePattern.matcher(content);
                            if (intMatcher.find()) {
                                var interfaceName = intMatcher.group(1);
                                var fullClassName = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;
                                targetClasses.add(fullClassName);
                            }
                        }
                    } catch (IOException _) {}
                });
            } catch (IOException _) {}
        }
    }

    private void collectFlattenedProperties(AotConfigParser parser, String currentPath, Map<String, Object> result) {
        var baseVal = parser.getConfigValue(currentPath);
        switch (baseVal) {
            case null -> {}
            case Map<?, ?> map -> {
                for (var entry : map.entrySet()) {
                    var key = String.valueOf(entry.getKey());
                    var nextPath = currentPath.isBlank() ? key : currentPath + "." + key;
                    collectFlattenedProperties(parser, nextPath, result);
                }
            }
            case List<?> list -> {
                var hasComplexStructures = false;
                for (var item : list) {
                    if (item instanceof Map || item instanceof List) {
                        hasComplexStructures = true;
                        break;
                    }
                }

                if (hasComplexStructures) {
                    for (var i = 0; i < list.size(); i++) {
                        collectFlattenedProperties(parser, currentPath + "." + i, result);
                    }
                } else {
                    result.put(currentPath, list);
                }
            }
            case HoconAotParser.FallbackHolder holder -> {
                if (holder.fallbackValue != null) result.put(currentPath, holder.fallbackValue);
            }
            default -> result.put(currentPath, baseVal);
        }
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            var files = file.listFiles();
            if (files != null) {
                for (var f : files) {delete(f);}
            }
        }
        file.delete();
    }

    public interface AppLogger {
        void lifecycle(String message);
    }
}
