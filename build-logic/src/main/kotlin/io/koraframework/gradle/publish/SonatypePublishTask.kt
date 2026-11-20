package io.koraframework.gradle.publish

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URLEncoder
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeoutException

@UntrackedTask(because = "Publishing tasks interact with external web services and should not be cached")
abstract class SonatypePublishTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val archive: RegularFileProperty

    @get:Input
    abstract val version: Property<String>

    @get:Internal
    abstract val username: Property<String>

    @get:Internal
    abstract val password: Property<String>

    init {
        group = "publishing"
        description = "Uploads deployment bundle to Sonatype Central Portal"
    }

    @TaskAction
    @Throws(IOException::class, TimeoutException::class, InterruptedException::class)
    fun upload() {
        val client = OkHttpClient.Builder()
            .connectTimeout(Duration.ofMinutes(2))
            .readTimeout(Duration.ofMinutes(2))
            .build()

        val om = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules()
            .build()

        val authorization = "Bearer " + Base64.getEncoder().encodeToString("${username.get()}:${password.get()}".toByteArray())

        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "bundle", "bundle.zip",
                archive.get().asFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val uploadName = URLEncoder.encode("kora-release-" + version.get(), Charsets.UTF_8)
        val request = Request.Builder()
            .post(form)
            .url("https://central.sonatype.com/api/v1/publisher/upload?name=$uploadName&publishingType=AUTOMATIC")
            .addHeader("Authorization", authorization)
            .build()

        val uploadStart = System.currentTimeMillis()
        logger.info("Uploading archive to Sonatype...")

        val deploymentId: String
        client.newCall(request).execute().use { rs ->
            if (rs.code != 201) {
                throw RuntimeException("Unexpected response code while uploading archive to Sonatype: ${rs.code}\n${rs.body.string()}")
            }
            deploymentId = rs.body.string().trim()
        }
        logger.info("Archive uploaded to Sonatype in {}s", (System.currentTimeMillis() - uploadStart) / 1000)

        val start = System.currentTimeMillis()
        val deadline = start + Duration.ofHours(1).toMillis()
        var status = ""

        logger.info("Waiting for deployment to transfer to final status (ID: {})...", deploymentId)
        while (System.currentTimeMillis() <= deadline) {
            Thread.sleep(10000)

            val rq = Request.Builder()
                .post(ByteArray(0).toRequestBody())
                .url("https://central.sonatype.com/api/v1/publisher/status?id=$deploymentId")
                .addHeader("Authorization", authorization)
                .build()

            client.newCall(rq).execute().use { rs ->
                if (rs.code != 200) {
                    throw RuntimeException("Unexpected response code while retrieving Sonatype deployment status: ${rs.code}\n${rs.body.string()}")
                }

                data class DeploymentStatus(val deploymentState: String)

                val rsString = rs.body.string()
                val deploymentStatus = om.readValue(rsString, DeploymentStatus::class.java)
                status = deploymentStatus.deploymentState

                if ("PUBLISHED" == status) {
                    logger.info("Deployment successfully PUBLISHED!")
                    return
                }
                if ("FAILED" == status) {
                    throw RuntimeException("Deployment failed on Sonatype side: \n$rsString")
                }
                logger.info("Current Sonatype deployment status: {}", status)
            }
        }
        throw TimeoutException("Timeout reached while waiting for deployment final status. Last status: $status")
    }
}
