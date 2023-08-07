package ru.tinkoff.kora.cache;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * Represent CacheKey interface that is used by the implementation that represents method arguments as key for Cache
 *
 * @see CacheKey#joined() generates string where all values are separated with '-' according to contract for CacheKey
 */
public interface CacheKey {

    /**
     * @return {@link Boolean#TRUE} if all sub keys are nullable
     */
    boolean isEmpty();

    @Nonnull
    String joined();

    /**
     * @return cache key values arguments that are subjected for Cache key
     */
    @Nonnull
    List<Object> values();

    @Nonnull
    static <K1, K2> Key2<K1, K2> of(K1 key1, K2 key2) {
        return new CacheKeyImpl.CacheKeyImpl2<>(key1, key2);
    }

    @Nonnull
    static <K1, K2, K3> Key3<K1, K2, K3> of(K1 key1, K2 key2, K3 key3) {
        return new CacheKeyImpl.CacheKeyImpl3<>(key1, key2, key3);
    }

    @Nonnull
    static <K1, K2, K3, K4> Key4<K1, K2, K3, K4> of(K1 key1, K2 key2, K3 key3, K4 key4) {
        return new CacheKeyImpl.CacheKeyImpl4<>(key1, key2, key3, key4);
    }

    @Nonnull
    static <K1, K2, K3, K4, K5> Key5<K1, K2, K3, K4, K5> of(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5) {
        return new CacheKeyImpl.CacheKeyImpl5<>(key1, key2, key3, key4, key5);
    }

    @Nonnull
    static <K1, K2, K3, K4, K5, K6> Key6<K1, K2, K3, K4, K5, K6> of(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6) {
        return new CacheKeyImpl.CacheKeyImpl6<>(key1, key2, key3, key4, key5, key6);
    }

    @Nonnull
    static <K1, K2, K3, K4, K5, K6, K7> Key7<K1, K2, K3, K4, K5, K6, K7> of(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7) {
        return new CacheKeyImpl.CacheKeyImpl7<>(key1, key2, key3, key4, key5, key6, key7);
    }

    @Nonnull
    static <K1, K2, K3, K4, K5, K6, K7, K8> Key8<K1, K2, K3, K4, K5, K6, K7, K8> of(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7, K8 key8) {
        return new CacheKeyImpl.CacheKeyImpl8<>(key1, key2, key3, key4, key5, key6, key7, key8);
    }

    @Nonnull
    static <K1, K2, K3, K4, K5, K6, K7, K8, K9> Key9<K1, K2, K3, K4, K5, K6, K7, K8, K9> of(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7, K8 key8, K9 key9) {
        return new CacheKeyImpl.CacheKeyImpl9<>(key1, key2, key3, key4, key5, key6, key7, key8, key9);
    }

    interface Key2<K1, K2> extends CacheKey {

        K1 key1();

        K2 key2();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2();
        }
    }

    interface Key3<K1, K2, K3> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3();
        }
    }

    interface Key4<K1, K2, K3, K4> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4();
        }
    }

    interface Key5<K1, K2, K3, K4, K5> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        K5 key5();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null && key5() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4(), key5());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4() + "|" + key5();
        }
    }

    interface Key6<K1, K2, K3, K4, K5, K6> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        K5 key5();

        K6 key6();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null && key5() == null && key6() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4(), key5(), key6());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4() + "|" + key5() + "|" + key6();
        }
    }

    interface Key7<K1, K2, K3, K4, K5, K6, K7> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        K5 key5();

        K6 key6();

        K7 key7();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null && key5() == null && key6() == null && key7() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4(), key5(), key6(), key7());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4() + "|" + key5() + "|" + key6() + "|" + key7();
        }
    }

    interface Key8<K1, K2, K3, K4, K5, K6, K7, K8> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        K5 key5();

        K6 key6();

        K7 key7();

        K8 key8();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null && key5() == null && key6() == null && key7() == null && key8() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4(), key5(), key6(), key7(), key8());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4() + "|" + key5() + "|" + key6() + "|" + key7() + "|" + key8();
        }
    }

    interface Key9<K1, K2, K3, K4, K5, K6, K7, K8, K9> extends CacheKey {

        K1 key1();

        K2 key2();

        K3 key3();

        K4 key4();

        K5 key5();

        K6 key6();

        K7 key7();

        K8 key8();

        K9 key9();

        @Override
        default boolean isEmpty() {
            return key1() == null && key2() == null && key3() == null && key4() == null && key5() == null && key6() == null && key7() == null && key8() == null && key9() == null;
        }

        @Nonnull
        @Override
        default List<Object> values() {
            return Arrays.asList(key1(), key2(), key3(), key4(), key5(), key6(), key7(), key8(), key9());
        }

        @Nonnull
        @Override
        default String joined() {
            return key1() + "|" + key2() + "|" + key3() + "|" + key4() + "|" + key5() + "|" + key6() + "|" + key7() + "|" + key8() + "|" + key9();
        }
    }
}
