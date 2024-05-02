package ru.tinkoff.kora.camunda.engine.configurator;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.camunda.engine.CamundaVersion;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;

public final class LicenseKeyProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(LicenseKeyProcessEngineConfigurator.class);

    private static final String HEADER_FOOTER_REGEX = "(?i)[-\\s]*(BEGIN|END)\\s*(OPTIMIZE|CAMUNDA|CAMUNDA\\s*BPM)\\s*LICENSE\\s*KEY[-\\s]*";
    private final CamundaVersion camundaVersion;

    private final CamundaEngineConfig config;

    public LicenseKeyProcessEngineConfigurator(CamundaEngineConfig camundaEngineConfig, CamundaVersion camundaVersion) {
        this.camundaVersion = camundaVersion;
        this.config = camundaEngineConfig;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (config.licensePath() != null) {
            logger.debug("Camunda Configurator licence key registering...");
            final long started = System.nanoTime();

            ManagementService managementService = engine.getManagementService();
            if (!camundaVersion.isEnterprise()) {
                logger.debug("Camunda Configurator license key is not required for Camunda Community Edition, ignoring...");
                return;
            }

            if (managementService.getLicenseKey() != null) {
                logger.debug("Camunda Configurator license Key is already registered...");
                return;
            }

            String licenseKey = readLicenseKeyFromUrl(config.licensePath());
            if (licenseKey != null) {
                managementService.setLicenseKey(licenseKey);
                logger.info("Camunda Configurator licence key registered in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            } else {
                logger.warn("Camunda Configurator can't find license key, register license in the Camunda Cockpit manually");
            }
        }
    }

    @Nullable
    private static String readLicenseKeyFromUrl(String licensePath) {
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
            logger.warn("Failed reading license key file {} due to {}", licensePath, e.getMessage());
            return null;
        }
    }
}
