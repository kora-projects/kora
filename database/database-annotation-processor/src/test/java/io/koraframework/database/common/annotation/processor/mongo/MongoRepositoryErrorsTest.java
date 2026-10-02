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
    public void testDuplicateTemplateKeyIsRejected() {
        assertThat(errorOf(templateRepository("{'login': 'a', 'login': 'b'}")))
            .contains("Mongo query template is invalid", "duplicate key 'login'");
    }

    @Test
    public void testNestedDuplicateTemplateKeyIsRejected() {
        assertThat(errorOf(templateRepository("{'age': {'$gt': 1, '$gt': 2}}")))
            .contains("Mongo query template is invalid", "duplicate key '$gt'");
    }

    @Test
    public void testNaNInTemplateIsRejected() {
        assertThat(errorOf(templateRepository("{'score': NaN}")))
            .contains("Mongo query template is invalid", "NaN");
    }

    @Test
    public void testInfinityInTemplateIsRejected() {
        assertThat(errorOf(templateRepository("{'score': -Infinity}")))
            .contains("Mongo query template is invalid", "Infinity");
    }

    @Test
    public void testUnsupportedExtendedJsonTypeIsRejected() {
        assertThat(errorOf(templateRepository("{'data': {'$binary': {'base64': 'AQ==', 'subType': '00'}}}")))
            .contains("Mongo query template is invalid", "BINARY")
            .doesNotContain("Kora internal error");
    }

    @Test
    public void testParameterNamedLikeGeneratedLocalIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{'login': :_query}")
                long count(String _query);
            }
            """)).contains("Mongo repository parameter name is invalid", "_query");
    }

    @Test
    public void testParameterStartingWithDollarIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "{}")
                long count(String $login);
            }
            """)).contains("Mongo repository parameter name is invalid", "$login");
    }

    private static String templateRepository(String filter) {
        return """
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoCount(filter = "%s")
                long count();
            }
            """.formatted(filter);
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
    public void testNonNumericLimitIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", limit = "ten")
                List<TestEntity> findAll();
            }
            """)).contains("neither an integer nor a ':name' reference");
    }

    @Test
    public void testLimitReferencingNonIntParameterIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", limit = ":size")
                List<TestEntity> findAll(String size);
            }
            """)).contains("has no int parameter with that name");
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
            """)).contains("Supported return types are void, ObjectId and the entity type");
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
    public void testUnsupportedInsertReturnTypeIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                String insert(TestEntity entity);
            }
            """)).contains("Supported return types are void, ObjectId and the entity type");
    }

    @Test
    public void testEntityResultWithoutAnIdFieldIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                TestEntity insert(TestEntity entity);
            }
            """)).contains("has no field mapped to '_id'");
    }

    @Test
    public void testIdResultWithNonObjectIdFieldIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoInsert
                ObjectId insert(TestEntity entity);
            }
            """, """
            public record TestEntity(@Id String id, String login) {}
            """)).contains("so the inserted identifier is not an ObjectId");
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

    @Test
    public void testProjectionMissingARequiredFieldIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 1}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """)).contains("Mongo projection does not cover the result type", "age");
    }

    @Test
    public void testProjectionExcludingARequiredFieldIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"age\\": 0}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """)).contains(
            "Mongo projection does not cover the result type",
            "age",
            "An exclusion projection returns every field except the listed ones",
            "Remove 'age' from the projection");
    }

    @Test
    public void testInclusionProjectionOfOnlyIdIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"_id\\": 1}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """)).contains("Mongo projection does not cover the result type", "login");
    }

    @Test
    public void testMixedProjectionIsRejected() {
        assertThat(errorOf("""
            @Repository
            @MongoCollection("users")
            public interface TestRepository extends MongoRepository {

                @MongoFind(filter = "{}", projection = "{\\"login\\": 1, \\"age\\": 0}")
                List<TestSummary> summaries();
            }
            """, """
            @EntityMongo
            public record TestSummary(String login, int age) {}
            """)).contains("mixes included and excluded fields");
    }

    private String errorOf(@Language("java") String repository) {
        return errorOf(repository, """
            public record TestEntity(String login) {}
            """);
    }

    private String errorOf(@Language("java") String repository, @Language("java") String entity) {
        compile(List.of(new RepositoryAnnotationProcessor()), repository, entity);

        assertThat(compileResult.isFailed()).isTrue();
        return compileResult.errors().stream()
            .map(e -> e.getMessage(null))
            .reduce("", (a, b) -> a + "\n" + b);
    }
}
