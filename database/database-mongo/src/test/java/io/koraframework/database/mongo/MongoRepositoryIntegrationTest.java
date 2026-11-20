package io.koraframework.database.mongo;

import io.koraframework.test.mongo.MongoParams;
import io.koraframework.test.mongo.MongoTestContainer;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MongoTestContainer.class)
class MongoRepositoryIntegrationTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    public void testInsertAndFindById(MongoParams params) {
        withRepository(params, (db, repository) -> {
            var user = user("user", 30);
            repository.insert(user);

            assertThat(repository.findById(user.id())).contains(user);
        });
    }

    @Test
    public void testFindReturnsNullWhenAbsent(MongoParams params) {
        withRepository(params, (db, repository) -> assertThat(repository.findByLogin("missing")).isNull());
    }

    @Test
    public void testInsertManyAndCount(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 30), user("c", 40)));

            assertThat(repository.countAll()).isEqualTo(3);
        });
    }

    @Test
    public void testSortAndLimit(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 40), user("c", 30)));

            assertThat(repository.findTwoOldest())
                .extracting(TestUser::login)
                .containsExactly("b", "c");
        });
    }

    @Test
    public void testPagingFromParameters(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 40), user("c", 30)));

            assertThat(repository.findPage(2, 0))
                .extracting(TestUser::login)
                .containsExactly("b", "c");
            assertThat(repository.findPage(2, 2))
                .extracting(TestUser::login)
                .containsExactly("a");
        });
    }

    @Test
    public void testFilterByInAndComparison(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 40), user("c", 30)));

            assertThat(repository.findByLogins(List.of("a", "c")))
                .extracting(TestUser::login)
                .containsExactlyInAnyOrder("a", "c");
            assertThat(repository.findOlderThan(30))
                .extracting(TestUser::login)
                .containsExactlyInAnyOrder("b", "c");
        });
    }

    @Test
    public void testUpdateAndReplace(MongoParams params) {
        withRepository(params, (db, repository) -> {
            var user = user("user", 30);
            repository.insert(user);

            assertThat(repository.rename(user.id(), "renamed").value()).isEqualTo(1);
            assertThat(repository.findById(user.id()).orElseThrow().login()).isEqualTo("renamed");

            var replacement = new TestUser(user.id(), "replaced", 44, NOW, List.of("x"), "note");
            assertThat(repository.replace(user.id(), replacement).value()).isEqualTo(1);
            assertThat(repository.findById(user.id())).contains(replacement);
        });
    }

    @Test
    public void testUpdateMany(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 30)));

            assertThat(repository.ageEveryone().value()).isEqualTo(2);
            assertThat(repository.findOlderThan(21)).hasSize(2);
        });
    }

    @Test
    public void testBatchUpdateAndDelete(MongoParams params) {
        withRepository(params, (db, repository) -> {
            var first = user("a", 20);
            var second = user("b", 30);
            var third = user("c", 40);
            repository.insertAll(List.of(first, second, third));

            var renamed = List.of(
                new TestUser(first.id(), "a-renamed", first.age(), NOW, first.tags(), null),
                new TestUser(second.id(), "b-renamed", second.age(), NOW, second.tags(), null));
            assertThat(repository.renameAll(renamed).value()).isEqualTo(2);
            assertThat(repository.findById(first.id()).orElseThrow().login()).isEqualTo("a-renamed");
            assertThat(repository.findById(third.id()).orElseThrow().login()).isEqualTo("c");

            assertThat(repository.deleteBatch(List.of(first, second)).value()).isEqualTo(2);
            assertThat(repository.countAll()).isEqualTo(1);
        });
    }

    @Test
    public void testEmptyBatchIsANoOp(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insert(user("a", 20));

            assertThat(repository.renameAll(List.of()).value()).isZero();
            assertThat(repository.countAll()).isEqualTo(1);
        });
    }

    @Test
    public void testDelete(MongoParams params) {
        withRepository(params, (db, repository) -> {
            var user = user("user", 30);
            repository.insertAll(List.of(user, user("other", 40)));

            assertThat(repository.deleteById(user.id()).value()).isEqualTo(1);
            assertThat(repository.countAll()).isEqualTo(1);
            assertThat(repository.deleteAll().value()).isEqualTo(1);
            assertThat(repository.countAll()).isZero();
        });
    }

    @Test
    public void testAggregate(MongoParams params) {
        withRepository(params, (db, repository) -> {
            repository.insertAll(List.of(user("a", 20), user("b", 40), user("c", 30)));

            var result = repository.countOlderThan(30);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getInteger("total")).isEqualTo(2);
        });
    }

    @Test
    public void testRepositoryJoinsTransactionAndRollsBack(MongoParams params) {
        withRepository(params, (db, repository) -> {
            try {
                db.inTxWithoutResult(() -> {
                    repository.insert(user("user", 30));
                    throw new IllegalStateException("boom");
                });
            } catch (IllegalStateException e) {
                // expected, the transaction must be rolled back
            }

            assertThat(repository.countAll()).isZero();
        });
    }

    @Test
    public void testRepositoryCommitsTransaction(MongoParams params) {
        withRepository(params, (db, repository) -> {
            db.inTxWithoutResult(() -> {
                repository.insert(user("a", 30));
                repository.insert(user("b", 31));
            });

            assertThat(repository.countAll()).isEqualTo(2);
        });
    }

    private static TestUser user(String login, int age) {
        return new TestUser(new ObjectId(), login, age, NOW, List.of("tag-" + login), null);
    }

    private static void withRepository(MongoParams params, BiConsumer<MongoDataSource, TestUserRepository> consumer) {
        MongoTestUtils.withDb(params, db -> consumer.accept(db, createRepository(db)));
    }

    /**
     * Constructor arguments are resolved by type, so the test does not depend on the order the generator emits codecs in.
     */
    private static TestUserRepository createRepository(MongoExecutor executor) {
        try {
            var implementation = Class.forName(TestUserRepository.class.getPackageName() + ".$TestUserRepository_Impl");
            var constructor = implementation.getConstructors()[0];
            var arguments = Arrays.stream(constructor.getGenericParameterTypes())
                .map(type -> argumentFor(type, executor))
                .toArray();
            return (TestUserRepository) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object argumentFor(Type type, MongoExecutor executor) {
        if (type.equals(MongoExecutor.class)) {
            return executor;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType().equals(Codec.class)) {
            var argument = parameterized.getActualTypeArguments()[0];
            if (argument.equals(TestUser.class)) {
                return new $TestUser_MongoCodec();
            }
            if (argument.equals(Document.class)) {
                return new DocumentCodec();
            }
        }
        throw new IllegalStateException("Unexpected repository constructor parameter: " + type);
    }
}
