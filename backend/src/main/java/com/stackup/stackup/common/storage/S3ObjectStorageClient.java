package com.stackup.stackup.common.storage;

import com.stackup.stackup.common.config.properties.S3Properties;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3ObjectStorageClient implements ObjectStorageClient {

    private final S3Properties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ObjectStorageClient(S3Properties properties) {
        this.properties = properties;
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
        );
        this.s3Client = S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build())
            .build();
        this.s3Presigner = S3Presigner.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build())
            .build();
    }

    @Override
    public StoredObject put(String key, InputStream content, long size, String contentType) {
        requireKey(key);
        Objects.requireNonNull(content, "content must not be null");
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentLength(size);
            if (hasText(contentType)) {
                requestBuilder.contentType(contentType);
            }
            s3Client.putObject(requestBuilder.build(), RequestBody.fromInputStream(content, size));
            return new StoredObject(properties.bucket(), key, size, contentType);
        } catch (SdkException e) {
            throw new StorageException(StorageErrorType.UPLOAD_FAILED, "Failed to upload object to S3", e);
        }
    }

    @Override
    public InputStream get(String key) {
        requireKey(key);
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build()
            );
            return response;
        } catch (SdkException e) {
            throw new StorageException(StorageErrorType.DOWNLOAD_FAILED, "Failed to download object from S3", e);
        }
    }

    @Override
    public void delete(String key) {
        requireKey(key);
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build()
            );
        } catch (SdkException e) {
            throw new StorageException(StorageErrorType.DELETE_FAILED, "Failed to delete object from S3", e);
        }
    }

    @Override
    public URI createPresignedGetUrl(String key, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(ttl, "ttl must not be null");
        try {
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                    .build()
            );
            return presignedRequest.url().toURI();
        } catch (SdkException | URISyntaxException e) {
            throw new StorageException(
                StorageErrorType.PRESIGNED_URL_FAILED,
                "Failed to create presigned URL for S3 object",
                e
            );
        }
    }

    @PreDestroy
    public void close() {
        s3Client.close();
        s3Presigner.close();
    }

    private static void requireKey(String key) {
        if (!hasText(key)) {
            throw new StorageException(StorageErrorType.INVALID_OBJECT_KEY, "Object key must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
