package io.koraframework.openfeature;

import dev.openfeature.sdk.FlagValueType;

import java.util.Map;

@FunctionalInterface
public interface OpenfeatureFlagRegistrar {

    /** Immutable flag key/type metadata generated for one source, available through Kora's All. */
    Map<String, FlagValueType> flags();
}
