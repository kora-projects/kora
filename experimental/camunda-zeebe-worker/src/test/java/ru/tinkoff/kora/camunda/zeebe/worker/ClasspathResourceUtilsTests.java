package ru.tinkoff.kora.camunda.zeebe.worker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClasspathResourceUtilsTests {

    @Test
    void fileByName() {
        List<ZeebeResourceDeployment.Resource> resources = ZeebeResourceDeployment.ClasspathResourceUtils.findResources("some-file.txt");
        assertEquals(1, resources.size());
        for (ZeebeResourceDeployment.Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegex() {
        List<ZeebeResourceDeployment.Resource> resources = ZeebeResourceDeployment.ClasspathResourceUtils.findResources(".*\\.txt");
        assertEquals(1, resources.size());
        for (ZeebeResourceDeployment.Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void filesFromDirectory() {
        List<ZeebeResourceDeployment.Resource> resources = ZeebeResourceDeployment.ClasspathResourceUtils.findResources("bpm");
        assertEquals(4, resources.size());
        for (ZeebeResourceDeployment.Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByNameFromDirectory() {
        List<ZeebeResourceDeployment.Resource> resources = ZeebeResourceDeployment.ClasspathResourceUtils.findResources("bpm/ProcessEmpty.bpmn");
        assertEquals(1, resources.size());
        for (ZeebeResourceDeployment.Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }

    @Test
    void fileByRegexFromDirectory() {
        List<ZeebeResourceDeployment.Resource> resources = ZeebeResourceDeployment.ClasspathResourceUtils.findResources("bpm/.*\\.bpmn");
        assertEquals(2, resources.size());
        for (ZeebeResourceDeployment.Resource resource : resources) {
            assertNotNull(resource.name());
            assertNotNull(resource.path());
            assertNotNull(resource.asInputStream());
        }
    }
}
