package ru.tinkoff.kora.database.common.annotation.processor.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.annotation.processor.model.TemplateModel;

import java.util.List;

public class TemplateModelTest {

    @Test
    void testTemplateParameterDetection() {
        var sql = "UPDATE {table_name} SET {entity.update_columns} WHERE {id_column} = :id RETURNING {columns}";
        var result = TemplateModel.detectTemplateParams(sql);
        List<TemplateModel.TemplateParam> expected = List.of(
            new TemplateModel.TemplateParam(null, "table_name", "{table_name}"),
            new TemplateModel.TemplateParam("entity", "update_columns", "{entity.update_columns}"),
            new TemplateModel.TemplateParam(null, "id_column", "{id_column}"),
            new TemplateModel.TemplateParam(null, "columns", "{columns}")
        );

        Assertions.assertEquals(expected, result);
    }

    @Test
    void testTemplateParameterDetectionMultiline() {
        var sql = """
            UPDATE {table_name}
            SET {entity.update_columns}
            WHERE {id_column} = :id
            RETURNING {columns}
            """;
        var result = TemplateModel.detectTemplateParams(sql);
        List<TemplateModel.TemplateParam> expected = List.of(
            new TemplateModel.TemplateParam(null, "table_name", "{table_name}"),
            new TemplateModel.TemplateParam("entity", "update_columns", "{entity.update_columns}"),
            new TemplateModel.TemplateParam(null, "id_column", "{id_column}"),
            new TemplateModel.TemplateParam(null, "columns", "{columns}")
        );

        Assertions.assertEquals(expected, result);
    }

}
