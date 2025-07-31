package ru.tinkoff.kora.gradle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeoutException;

public abstract class SonatypePublishTask extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getArchive();

    @Input
    public abstract Property<String> getUsername();

    @Input
    public abstract Property<String> getPassword();

    public SonatypePublishTask() {
        setGroup("publishing");
    }

    @TaskAction
    public void upload() throws IOException, TimeoutException, InterruptedException {
        var client = new OkHttpClient();
        var om = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();
        var authorization = "Bearer " + Base64.getEncoder().encodeToString((getUsername().get() + ":" + getPassword().get()).getBytes());
        var form = new MultipartBody.Builder()
            .addFormDataPart("bundle", "bundle.zip", RequestBody.create(getArchive().getAsFile().get(), MediaType.get("application/octet-stream")))
            .build();
        var uploadName = URLEncoder.encode("kora-release-" + getProject().getVersion(), StandardCharsets.UTF_8);
        var request = new Request.Builder()
            .post(form)
            .url("https://central.sonatype.com/api/v1/publisher/upload?name=" + uploadName + "&publishingType=AUTOMATIC")
            .addHeader("Authorization", authorization)
            .build();
        var uploadStart = System.currentTimeMillis();
        getLogger().info("Uploading archive to sonatype");
        final String deploymentId;
        try (var rs = client.newCall(request).execute()) {
            if (rs.code() != 201) {
                throw new RuntimeException("Unexpected response code while uploading archive to sonatype: " + rs.code() + "\n" + rs.body().string());
            }
            deploymentId = rs.body().string().trim();
        }
        getLogger().info("Archive uploaded to sonatype in {}s", (System.currentTimeMillis() - uploadStart) / 1000);
        var start = System.currentTimeMillis();
        var deadline = start + Duration.ofHours(1).toMillis();
        String status = "";
        getLogger().info("Waiting for deployment to transfer to final status");
        while (System.currentTimeMillis() <= deadline) {
            Thread.sleep(10000);
            var rq = new Request.Builder()
                .post(RequestBody.create(new byte[0]))
                .url("https://central.sonatype.com/api/v1/publisher/status?id=" + deploymentId)
                .addHeader("Authorization", authorization)
                .build();
            try (var rs = client.newCall(rq).execute()) {
                if (rs.code() != 200) {
                    throw new RuntimeException("Unexpected response code while retrieving sonatype deployment status: " + rs.code() + "\n" + rs.body().string());
                }
                record DeploymentStatus(String deploymentState) {}
                var rsString = rs.body().string();
                var deploymentStatus = om.readValue(rsString, DeploymentStatus.class);
                status = deploymentStatus.deploymentState();
                if (status.equals("PUBLISHED")) {
                    return;
                }
                if (status.equals("FAILED")) {
                    throw new RuntimeException("Deployment failed: \n" + rsString);
                }
                getLogger().info("Waiting for deployment to transfer to final status, current status: {}", status);
            }
        }
        throw new TimeoutException("Timeout reached while waiting for deployment to transfer to final status, last status: " + status);
    }
}
