package io.koraframework.camunda.engine.bpmn.configurator;

import org.junit.jupiter.api.Test;
import io.koraframework.camunda.engine.bpmn.configurator.DeploymentProcessEngineConfigurator.ClasspathResourceUtils;
import io.koraframework.camunda.engine.bpmn.configurator.DeploymentProcessEngineConfigurator.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClasspathResourceUtilsTests {

    @Test
    void fileByName() {
        List<Resource> resources = ClasspathResourceUtils.findResources("camunda-license.txt");
        assertEquals(1, resources.size());
        for (Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegex() {
        List<Resource> resources = ClasspathResourceUtils.findResources(".*\\.txt");
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

    @Test
    void filesFromNestedDirectories() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm-nested");
        assertEquals(List.of("Nested.dmn", "Top.bpmn"), resources.stream().map(Resource::name).sorted().toList());
        for (Resource resource : resources) {
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegexFromNestedDirectories() {
        List<Resource> resources = ClasspathResourceUtils.findResources("bpm-nested/.*\\.dmn");
        assertEquals(1, resources.size());
        assertEquals("Nested.dmn", resources.get(0).name());
        assertNotNull(resources.get(0).asInputStream());
    }
}
