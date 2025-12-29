package ru.tinkoff.kora.cache;

import ru.tinkoff.kora.common.Mapping;

/**
 * Represent mapping interface that is used to map method arguments to cache key
 */
@FunctionalInterface
public interface CacheKeyMapper<T, A1> extends Mapping.MappingFunction {

    T map( A1 arg);

    @FunctionalInterface
    interface CacheKeyMapper2<T, A1, A2> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2);
    }

    @FunctionalInterface
    interface CacheKeyMapper3<T, A1, A2, A3> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3);
    }

    @FunctionalInterface
    interface CacheKeyMapper4<T, A1, A2, A3, A4> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4);
    }

    @FunctionalInterface
    interface CacheKeyMapper5<T, A1, A2, A3, A4, A5> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4,  A5 arg5);
    }

    @FunctionalInterface
    interface CacheKeyMapper6<T, A1, A2, A3, A4, A5, A6> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4,  A5 arg5,  A6 arg6);
    }

    @FunctionalInterface
    interface CacheKeyMapper7<T, A1, A2, A3, A4, A5, A6, A7> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4,  A5 arg5,  A6 arg6,  A7 arg7);
    }

    @FunctionalInterface
    interface CacheKeyMapper8<T, A1, A2, A3, A4, A5, A6, A7, A8> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4,  A5 arg5,  A6 arg6,  A7 arg7,  A8 arg8);
    }

    @FunctionalInterface
    interface CacheKeyMapper9<T, A1, A2, A3, A4, A5, A6, A7, A8, A9> extends Mapping.MappingFunction {

        T map( A1 arg1,  A2 arg2,  A3 arg3,  A4 arg4,  A5 arg5,  A6 arg6,  A7 arg7,  A8 arg8,  A9 arg9);
    }
}
