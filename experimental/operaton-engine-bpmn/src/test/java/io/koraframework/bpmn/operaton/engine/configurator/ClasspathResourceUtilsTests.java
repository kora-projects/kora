package io.koraframework.bpmn.operaton.engine.configurator;

import io.koraframework.bpmn.operaton.engine.configurator.DeploymentProcessEngineConfigurator.ClasspathResourceUtils;
import io.koraframework.bpmn.operaton.engine.configurator.DeploymentProcessEngineConfigurator.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClasspathResourceUtilsTests {

    @Test
    void fileByRegex() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm/.*.form");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void filesFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm");
        assertEquals(4, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByNameFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm/ProcessEmpty.bpmn");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegexFromDirectory() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm/.*\\.bpmn");
        assertEquals(2, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }
}
