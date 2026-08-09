package io.koraframework.database.common.annotation.processor.mongo;

import io.koraframework.database.annotation.processor.RepositoryAnnotationProcessor;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A generator is only as good as the message it produces when the code is wrong, so every rejection path is pinned
 * down by a test.
 */
public class MongoRepositoryErrorsTest extends AbstractMongoRepositoryTest {

    @Test
    public void testMalformedFilterIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"login\\": }")
                List<TestEntity> findAll();
            }
            """)).contains("Mongo query template is invalid", "filter");
    }

    @Test
    public void testUnknownPlaceholderIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{\\"login\\": :missing}")
                List<TestEntity> findAll();
            }
            """)).contains("Template references ':missing'");
    }

    @Test
    public void testUnusedParameterIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                List<TestEntity> findAll(String login);
            }
            """)).contains("unused parameters", "login");
    }

    @Test
    public void testUnresolvedCollectionIsRejected() {
        assertThat(errorOf("""
            @Repository
            public interface TestRepository extends MongoRepository {

                @MongoDelete(filter = "{}", many = true)
                UpdateCount deleteAll();
            }
            """)).contains("Mongo collection can not be resolved");
    }

    @Test
    public void testMethodWithoutOperationAnnotationIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                List<TestEntity> findAll();
            }
            """)).contains("has no Mongo operation annotation");
    }

    @Test
    public void testTwoOperationAnnotationsAreRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                @MongoCount(filter = "{}")
                long countAll();
            }
            """)).contains("more than one Mongo operation annotation");
    }

    @Test
    public void testUnsupportedCountReturnTypeIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{}")
                String countAll();
            }
            """)).contains("unsupported return type", "void, UpdateCount, long and int");
    }

    @Test
    public void testVoidFindIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}")
                void findAll();
            }
            """)).contains("A read operation must return the documents it reads");
    }

    @Test
    public void testInsertWithResultIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                long insert(TestEntity entity);
            }
            """)).contains("@MongoInsert does not produce a result");
    }

    @Test
    public void testInsertWithoutEntityParameterIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                void insert();
            }
            """)).contains("needs exactly one parameter holding the document to write");
    }

    @Test
    public void testPipelineStageThatIsNotADocumentIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoAggregate("[1, 2]")
                List<TestEntity> stats();
            }
            """)).contains("Every aggregation stage must be a document");
    }

    private String errorOf(@Language("java") String repository) {
        compile(List.of(new RepositoryAnnotationProcessor()), repository, """
            public record TestEntity(String login) {}
            """);

        assertThat(compileResult.isFailed()).isTrue();
        return compileResult.errors().stream()
            .map(e -> e.getMessage(null))
            .reduce("", (a, b) -> a + "\n" + b);
    }
}
