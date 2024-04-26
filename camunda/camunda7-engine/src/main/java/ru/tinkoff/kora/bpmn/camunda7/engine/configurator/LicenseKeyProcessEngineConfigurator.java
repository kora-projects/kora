package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.Camunda7Version;
import ru.tinkoff.kora.bpmn.camunda7.engine.Camunda7EngineConfig;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class LicenseKeyProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(LicenseKeyProcessEngineConfigurator.class);

    private static final String HEADER_FOOTER_REGEX = "(?i)[-\\s]*(BEGIN|END)\\s*(OPTIMIZE|CAMUNDA|CAMUNDA\\s*BPM)\\s*LICENSE\\s*KEY[-\\s]*";
    private final Camunda7Version camundaVersion;

    private final String licensePath;

    public LicenseKeyProcessEngineConfigurator(Camunda7EngineConfig camundaEngineConfig, Camunda7Version camundaVersion) {
        this.camundaVersion = camundaVersion;
        this.licensePath = camundaEngineConfig.licensePath();
    }

    @Override
    public void setup(ProcessEngine engine) {
        ManagementService managementService = engine.getManagementService();
        if (!camundaVersion.isEnterprise()) {
            logger.debug("License is not required for Camunda Community Edition, it will be ignored.");
            return;
        }

        // Check if there is already a license key in the database
        if (managementService.getLicenseKey() != null) {
            logger.info("A license key is already registered and will be used. Please use the Camunda Cockpit to update it.");
            return;
        }

        String licenseKey = readLicenseKeyFromUrl(licensePath);
        if (licenseKey != null) {
            managementService.setLicenseKey(licenseKey);
            logger.info("Registered new license key");
        } else {
            logger.warn("Could not locate the referenced license key. The license can be registered in the Camunda Cockpit.");
        }
    }

    private String readLicenseKeyFromUrl(String licensePath) {
        try {
            URL licenseUrl = LicenseKeyProcessEngineConfigurator.class.getClassLoader().getResource(licensePath);
            if (licenseUrl == null) {
                return null;
            }

            Scanner scanner = new Scanner(licenseUrl.openStream(), StandardCharsets.UTF_8).useDelimiter("\\A");
            if (scanner.hasNext()) {
                return scanner.next()
                    .replaceAll(HEADER_FOOTER_REGEX, "")
                    .replaceAll("\\n", "")
                    .trim();
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("Ignoring license file {}. Details: {}", licensePath, e.getMessage());
            return null;
        }
    }
}
