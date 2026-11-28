package io.koraframework.config.aot.generated;

import java.util.List;
import java.util.ArrayList;

public final class AotFlywayMigrations {

    // CHUNKS_METHODS_POINT

    public static List<MigrationData> getMigrations() {
        // MIGRATIONS_INIT_POINT
        return list;
    }

    public record MigrationData(
        String path,
        String filename,
        String content,
        String version,
        String description,
        Integer checksum,
        String physicalLocation
    ) {}
}
