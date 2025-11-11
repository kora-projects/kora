package ru.tinkoff.kora.aws.s3;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.exception.S3ClientException;
import ru.tinkoff.kora.aws.s3.model.*;
import ru.tinkoff.kora.aws.s3.model.rq.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public interface S3Client {
    /**
     * The HEAD operation retrieves metadata from an object without returning the object itself. This operation is useful if you're interested only in an object's metadata.
     *
     * @param credentials
     * @param bucket      The bucket name containing the object.
     * @param key         Key of the object to get.
     * @return object metadata or null if required is false and object is not found
     * @throws ru.tinkoff.kora.aws.s3.exception.S3ClientResponseException on 404 if required is true
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html">HeadObject</a>
     */
    @Nullable
    HeadObjectResult headObject(AwsCredentials credentials, String bucket, String key, @Nullable HeadObjectArgs args, boolean required) throws S3ClientException;

    /**
     * @see #headObject
     */
    default HeadObjectResult headObject(AwsCredentials credentials, String bucket, String key) throws S3ClientException {
        return Objects.requireNonNull(this.headObject(credentials, bucket, key, null, true));
    }

    /**
     * @see #headObject
     */
    @Nullable
    default HeadObjectResult headObjectOptional(AwsCredentials credentials, String bucket, String key) throws S3ClientException {
        return this.headObject(credentials, bucket, key, null, false);
    }

    /**
     * Retrieves an object from Amazon S3.
     *
     * @param credentials
     * @param bucket      The bucket name containing the object.
     * @param key         Key of the object to get.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html">GetObject</a>
     */
    @Nullable
    GetObjectResult getObject(AwsCredentials credentials, String bucket, String key, @Nullable GetObjectArgs args, boolean required);

    /**
     * @see #getObject
     */
    default GetObjectResult getObject(AwsCredentials credentials, String bucket, String key) {
        return this.getObject(credentials, bucket, key, null, true);
    }

    /**
     * @see #getObject
     */
    default GetObjectResult getObjectOptional(AwsCredentials credentials, String bucket, String key) {
        return this.getObject(credentials, bucket, key, null, false);
    }


    /**
     * Removes an object from a bucket.
     *
     * @param credentials
     * @param bucket      The bucket name of the bucket containing the object.
     * @param key         Key name of the object to delete.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html">DeleteObject</a>
     */
    void deleteObject(AwsCredentials credentials, String bucket, String key, @Nullable DeleteObjectArgs args) throws S3ClientException;

    /**
     * @see #deleteObject
     */
    default void deleteObject(AwsCredentials credentials, String bucket, String key) throws S3ClientException {
        this.deleteObject(credentials, bucket, key, null);
    }

    /**
     * This operation enables you to delete multiple objects from a bucket using a single HTTP request.<br>
     * If you know the object keys that you want to delete, then this operation provides a suitable alternative to sending individual delete requests, reducing per-request overhead.
     * The request can contain a list of up to 1,000 keys that you want to delete.
     * In the XML, you provide the object key names, and optionally, version IDs if you want to delete a specific version of the object from a versioning-enabled bucket.
     * For each key, Amazon S3 performs a delete operation and returns the result of that delete, success or failure, in the response.
     * If the object specified in the request isn't found, Amazon S3 confirms the deletion by returning the result as deleted.
     *
     * @param credentials
     * @param bucket      The bucket name of the bucket containing the object.
     * @param keys        The objects to delete.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObjects.html">DeleteObjects</a>
     */
    void deleteObjects(AwsCredentials credentials, String bucket, List<String> keys) throws S3ClientException;


    /**
     * Adds an object to a bucket.
     *
     * @param bucket The bucket name to which the PUT action was initiated.
     * @param key    Object key for which the PUT action was initiated.
     * @param data   The data.
     * @param off    The start offset in the data.
     * @param len    The number of bytes to write.
     * @return the ETag of the uploaded object
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html">PutObject</a>
     */
    String putObject(AwsCredentials credentials, String bucket, String key, @Nullable PutObjectArgs args, byte[] data, int off, int len) throws S3ClientException;

    /**
     * @see #putObject(AwsCredentials, String, String, PutObjectArgs, byte[], int, int)
     */
    default String putObject(AwsCredentials credentials, String bucket, String key, byte[] data, int off, int len) throws S3ClientException {
        return this.putObject(credentials, bucket, key, null, data, off, len);
    }

    /**
     * Adds an object to a bucket.
     *
     * @param bucket        The bucket name to which the PUT action was initiated.
     * @param key           Object key for which the PUT action was initiated.
     * @param contentWriter Callback for writing data to object.
     * @param len           The number of bytes to write.
     * @return the ETag of the uploaded object
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html">PutObject</a>
     */
    String putObject(AwsCredentials credentials, String bucket, String key, @Nullable PutObjectArgs args, ContentWriter contentWriter, long len) throws S3ClientException;

    /**
     * @see #putObject(AwsCredentials, String, String, PutObjectArgs, ContentWriter, long)
     */
    default String putObject(AwsCredentials credentials, String bucket, String key, ContentWriter contentWriter, long len) throws S3ClientException {
        return this.putObject(credentials, bucket, key, null, contentWriter, len);
    }

    /**
     * This operation lists in-progress multipart uploads in a bucket.
     * An in-progress multipart upload is a multipart upload that has been initiated by the CreateMultipartUpload request, but has not yet been completed or aborted.<br>
     * The ListMultipartUploads operation returns a maximum of 1,000 multipart uploads in the response. The limit of 1,000 multipart uploads is also the default value.
     * You can further limit the number of uploads in a response by specifying the max-uploads request parameter.
     * If there are more than 1,000 multipart uploads that satisfy your ListMultipartUploads request, the response returns an IsTruncated element with the value of true, a NextKeyMarker element, and a NextUploadIdMarker element.
     * To list the remaining multipart uploads, you need to make subsequent ListMultipartUploads requests. In these requests, include two query parameters: key-marker and upload-id-marker.
     * Set the value of key-marker to the NextKeyMarker value from the previous response. Similarly, set the value of upload-id-marker to the NextUploadIdMarker value from the previous response.
     *
     * @param bucket The name of the bucket to which the multipart upload was initiated.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListMultipartUploads.html">ListMultipartUploads</a>
     */
    ListMultipartUploadsResult listMultipartUploads(AwsCredentials credentials, String bucket, @Nullable ListMultipartUploadsArgs args) throws S3ClientException;

    /**
     * Returns some or all (up to 1,000) of the objects in a bucket with each request.
     * You can use the request parameters as selection criteria to return a subset of the objects in a bucket.
     * For more information about listing objects, see Listing object keys programmatically in the Amazon S3 User Guide.
     * To get a list of your buckets, see ListBuckets.
     */
    ListBucketResult listObjectsV2(AwsCredentials credentials, String bucket, @Nullable ListObjectsArgs args);

    /**
     * @see #listObjectsV2(AwsCredentials, String, ListObjectsArgs)
     */
    Iterator<ListBucketResult.ListBucketItem> listObjectsV2Iterator(AwsCredentials credentials, String bucket, @Nullable ListObjectsArgs args);

    /**
     * This action initiates a multipart upload and returns an upload ID.
     * This upload ID is used to associate all of the parts in the specific multipart upload. You specify this upload ID in each of your subsequent upload part requests (see UploadPart).
     * You also include this upload ID in the final request to either complete or abort the multipart upload request.
     * For more information about multipart uploads, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html">Multipart Upload Overview</a> in the Amazon S3 User Guide.
     *
     * @param credentials
     * @param bucket      The name of the bucket where the multipart upload is initiated and where the object is uploaded.
     * @param key         Object key for which the multipart upload is to be initiated.
     * @return ID for the initiated multipart upload.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateMultipartUpload.html">createMultipartUpload</a>
     */
    String createMultipartUpload(AwsCredentials credentials, String bucket, String key, @Nullable CreateMultipartUploadArgs args) throws S3ClientException;

    /**
     * @see #createMultipartUpload
     */
    default String createMultipartUpload(AwsCredentials credentials, String bucket, String key) throws S3ClientException {
        return this.createMultipartUpload(credentials, bucket, key, null);
    }


    /**
     * Uploads a part in a multipart upload.
     *
     * @param bucket     The name of the bucket to which the multipart upload was initiated.
     * @param key        Object key for which the multipart upload was initiated.
     * @param partNumber Part number of part being uploaded. This is a positive integer between 1 and 10,000.
     * @param uploadId   Upload ID identifying the multipart upload whose part is being uploaded.
     * @param data       The data.
     * @param off        The start offset in the data.
     * @param len        The number of bytes to write.
     * @return Entity tag for the uploaded object.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html">UploadPart</a>
     */
    UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, @Nullable UploadPartArgs args, byte[] data, int off, int len) throws S3ClientException;

    /**
     * @see #uploadPart(AwsCredentials, String, String, String, int, UploadPartArgs, byte[], int, int)
     */
    default UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, byte[] data, int off, int len) throws S3ClientException {
        return this.uploadPart(credentials, bucket, key, uploadId, partNumber, null, data, off, len);
    }

    /**
     * Uploads a part in a multipart upload.
     *
     * @param bucket        The name of the bucket to which the multipart upload was initiated.
     * @param key           Object key for which the multipart upload was initiated.
     * @param partNumber    Part number of part being uploaded. This is a positive integer between 1 and 10,000.
     * @param uploadId      Upload ID identifying the multipart upload whose part is being uploaded.
     * @param contentWriter Callback for writing data to object.
     * @param len           The number of bytes to write.
     * @return Entity tag for the uploaded object.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html">UploadPart</a>
     */
    UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, @Nullable UploadPartArgs args, ContentWriter contentWriter, long len) throws S3ClientException;

    /**
     * @see #uploadPart(AwsCredentials, String, String, String, int, UploadPartArgs, ContentWriter, long)
     */
    default UploadedPart uploadPart(AwsCredentials credentials, String bucket, String key, String uploadId, int partNumber, ContentWriter contentWriter, long len) throws S3ClientException {
        return this.uploadPart(credentials, bucket, key, uploadId, partNumber, null, contentWriter, len);
    }

    /**
     * Lists the parts that have been uploaded for a specific multipart upload.
     * The ListParts request returns a maximum of 1,000 uploaded parts. The limit of 1,000 parts is also the default value.
     * You can restrict the number of parts in a response by specifying the max-parts request parameter.
     * If your multipart upload consists of more than 1,000 parts, the response returns an IsTruncated field with the value of true, and a NextPartNumberMarker element.
     * To list remaining uploaded parts, in subsequent ListParts requests, include the part-number-marker query string parameter and set its value to the NextPartNumberMarker field value from the previous response.
     *
     * @param bucket   The name of the bucket to which the parts are being uploaded.
     * @param key      Object key for which the multipart upload was initiated.
     * @param uploadId Upload ID identifying the multipart upload whose parts are being listed.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListParts.html">ListParts</a>
     */
    ListPartsResult listParts(AwsCredentials credentials, String bucket, String key, String uploadId, @Nullable ListPartsArgs args) throws S3ClientException;

    default ListPartsResult listParts(AwsCredentials credentials, String bucket, String key, String uploadId, @Nullable Integer maxParts, @Nullable Integer partNumberMarker) throws S3ClientException {
        return this.listParts(credentials, bucket, key, uploadId, new ListPartsArgs()
            .setMaxParts(maxParts)
            .setPartNumberMarker(partNumberMarker)
        );
    }

    /**
     * This operation aborts a multipart upload. After a multipart upload is aborted, no additional parts can be uploaded using that upload ID.
     * The storage consumed by any previously uploaded parts will be freed.
     * However, if any part uploads are currently in progress, those part uploads might or might not succeed.
     * As a result, it might be necessary to abort a given multipart upload multiple times in order to completely free all storage consumed by all parts.
     * To verify that all parts have been removed and prevent getting charged for the part storage, you should call the {@link #listParts} API operation and ensure that the parts list is empty.
     *
     * @param bucket   The bucket name to which the upload was taking place.
     * @param key      Key of the object for which the multipart upload was initiated.
     * @param uploadId Upload ID that identifies the multipart upload.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html">AbortMultipartUpload</a>
     */
    void abortMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId, @Nullable AbortMultipartUploadArgs args) throws S3ClientException;

    /**
     * @see #abortMultipartUpload(AwsCredentials, String, String, String, AbortMultipartUploadArgs)
     */
    default void abortMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId) throws S3ClientException {
        this.abortMultipartUpload(credentials, bucket, key, uploadId, null);
    }

    /**
     * Completes a multipart upload by assembling previously uploaded parts.
     *
     * @param bucket   The bucket name to which the upload was taking place.
     * @param key      Key of the object for which the multipart upload was initiated.
     * @param uploadId Upload ID that identifies the multipart upload.
     * @param parts    uploaded parts metadata
     * @return the ETag of the uploaded object
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_CompleteMultipartUpload.html">CompleteMultipartUpload</a>
     */
    String completeMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId, List<UploadedPart> parts, @Nullable CompleteMultipartUploadArgs args) throws S3ClientException;

    /**
     * @see #completeMultipartUpload
     */
    default String completeMultipartUpload(AwsCredentials credentials, String bucket, String key, String uploadId, List<UploadedPart> parts) throws S3ClientException {
        return this.completeMultipartUpload(credentials, bucket, key, uploadId, parts, null);
    }

    interface ContentWriter extends Closeable {
        void write(OutputStream os) throws IOException;

        @Override
        default void close() throws IOException {}
    }

}
