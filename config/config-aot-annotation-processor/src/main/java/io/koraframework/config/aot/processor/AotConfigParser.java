package io.koraframework.config.aot.processor;

public interface AotConfigParser {

    void loadAndMerge(ClassLoader classLoader, String customResource, String customFile);

    Object getConfigValue(String path);
}
