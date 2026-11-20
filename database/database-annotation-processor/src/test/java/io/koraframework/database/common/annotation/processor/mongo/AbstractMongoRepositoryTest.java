package io.koraframework.database.common.annotation.processor.mongo;

import io.koraframework.database.common.annotation.processor.AbstractRepositoryTest;
import org.intellij.lang.annotations.Language;

import java.util.List;

public abstract class AbstractMongoRepositoryTest extends AbstractRepositoryTest {

    protected MockMongoExecutor executor = new MockMongoExecutor();

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.database.mongo.*;
            import io.koraframework.database.mongo.annotation.*;
            import io.koraframework.common.annotation.Mapping;
            import org.bson.types.ObjectId;
            import org.bson.BsonDocument;
            import org.bson.Document;
            import org.jspecify.annotations.Nullable;
            import java.util.List;
            import java.util.Optional;
            import java.util.Set;
            """;
    }

    protected TestObject compileMongo(List<?> arguments, @Language("java") String... sources) {
        return this.compile(this.executor, arguments, sources);
    }
}
