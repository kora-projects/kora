package io.koraframework.resilient.fallback;


public interface FallbackManager {

    Fallback get(String name);
}
