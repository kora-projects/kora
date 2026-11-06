package io.koraframework.test.extension.junit5.testdata;

public interface GenericRepository<T> {

    T find();

    abstract class AbstractGenericRepository<T> implements GenericRepository<T> {
    }

    final class StringRepository extends AbstractGenericRepository<String> {
        @Override
        public String find() {
            return "string";
        }
    }

    class IntegerRepositoryBase implements GenericRepository<Integer> {
        @Override
        public Integer find() {
            return 1;
        }
    }

    final class IntegerRepository extends IntegerRepositoryBase {
    }
}
