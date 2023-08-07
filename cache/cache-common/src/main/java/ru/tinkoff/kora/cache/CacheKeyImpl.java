package ru.tinkoff.kora.cache;

class CacheKeyImpl {

    private CacheKeyImpl() {}

    record CacheKeyImpl2<K1, K2>(K1 key1, K2 key2) implements CacheKey.Key2<K1, K2> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl3<K1, K2, K3>(K1 key1, K2 key2, K3 key3) implements CacheKey.Key3<K1, K2, K3> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl4<K1, K2, K3, K4>(K1 key1, K2 key2, K3 key3, K4 key4) implements CacheKey.Key4<K1, K2, K3, K4> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl5<K1, K2, K3, K4, K5>(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5) implements CacheKey.Key5<K1, K2, K3, K4, K5> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl6<K1, K2, K3, K4, K5, K6>(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6) implements CacheKey.Key6<K1, K2, K3, K4, K5, K6> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl7<K1, K2, K3, K4, K5, K6, K7>(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7) implements CacheKey.Key7<K1, K2, K3, K4, K5, K6, K7> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl8<K1, K2, K3, K4, K5, K6, K7, K8>(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7, K8 key8) implements CacheKey.Key8<K1, K2, K3, K4, K5, K6, K7, K8> {

        @Override
        public String toString() {
            return joined();
        }
    }

    record CacheKeyImpl9<K1, K2, K3, K4, K5, K6, K7, K8, K9>(K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, K6 key6, K7 key7, K8 key8,
                                                             K9 key9) implements CacheKey.Key9<K1, K2, K3, K4, K5, K6, K7, K8, K9> {

        @Override
        public String toString() {
            return joined();
        }
    }
}
