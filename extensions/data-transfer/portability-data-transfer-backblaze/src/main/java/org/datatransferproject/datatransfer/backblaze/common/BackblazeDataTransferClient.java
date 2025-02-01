/*
 * Copyright 2018 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.datatransfer.backblaze.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.backblaze.exception.BackblazeCredentialsException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

public class BackblazeDataTransferClient {
  private static final String DATA_TRANSFER_BUCKET_PREFIX_FORMAT_STRING = "%s-data-transfer";
  private static final int MAX_BUCKET_CREATION_ATTEMPTS = 10;
  private final List<String> BACKBLAZE_REGIONS =
      Arrays.asList("us-west-000", "us-west-001", "us-west-002", "eu-central-003");

  private final long sizeThresholdForMultipartUpload;
  private final long partSizeForMultiPartUpload;
  private final BackblazeS3ClientFactory backblazeS3ClientFactory;
  private final Monitor monitor;
  private final DateFormat dateFormatter;
  private S3Client s3Client;
  private String bucketName;

  public BackblazeDataTransferClient(
          Monitor monitor,
          BackblazeS3ClientFactory backblazeS3ClientFactory,
          long sizeThresholdForMultipartUpload,
          long partSizeForMultiPartUpload) {
    this.monitor = monitor;
    this.backblazeS3ClientFactory = backblazeS3ClientFactory;
    // Avoid infinite loops
    if (partSizeForMultiPartUpload <= 0)
      throw new IllegalArgumentException("Part size for multipart upload must be positive.");
    this.sizeThresholdForMultipartUpload = sizeThresholdForMultipartUpload;
    this.partSizeForMultiPartUpload = partSizeForMultiPartUpload;

    dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
    dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  public void init(String keyId, String applicationKey, String exportService)
      throws BackblazeCredentialsException, IOException {
    // Fetch all the available buckets and use that to find which region the user is in
    ListBucketsResponse listBucketsResponse = null;
    String userRegion = null;

    // The Key ID starts with the region identifier number, so reorder the regions such that
    // the first region is most likely the user's region
    String regionId = keyId.substring(0, 3);
    BACKBLAZE_REGIONS.sort(
        (String region1, String region2) -> {
          if (region1.endsWith(regionId)) {
            return -1;
          }
          return 0;
        });

    Throwable s3Exception = null;
    for (String region : BACKBLAZE_REGIONS) {
      try {
        s3Client = backblazeS3ClientFactory.createS3Client(keyId, applicationKey, region);

        listBucketsResponse = s3Client.listBuckets();
        userRegion = region;
        break;
      } catch (S3Exception e) {
        s3Exception = e;
        if (s3Client != null) {
          s3Client.close();
        }
        if (e.statusCode() == 403) {
          monitor.debug(() -> String.format("User is not in region %s", region));
        }
      }
    }

    if (listBucketsResponse == null || userRegion == null) {
      throw new BackblazeCredentialsException(
          "User's credentials or permissions are not valid for any regions available", s3Exception);
    }

    bucketName = getOrCreateBucket(s3Client, listBucketsResponse, userRegion, exportService);
  }

  public String uploadFile(String fileKey, File file, Date createdTime) throws IOException {
    if (s3Client == null || bucketName == null) {
      throw new IllegalStateException("BackblazeDataTransferClient has not been initialised");
    }

    try {
      long contentLength = file.length();
      monitor.debug(
          () -> String.format("Uploading '%s' with file size %d bytes", fileKey, contentLength));

      if (contentLength >= sizeThresholdForMultipartUpload) {
        monitor.debug(
            () ->
                String.format(
                    "File size is larger than %d bytes, so using multipart upload",
                        sizeThresholdForMultipartUpload));
        return uploadFileUsingMultipartUpload(fileKey, file, contentLength);
      }

      String createdTimeIso = dateFormatter.format(createdTime);

      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder().bucket(bucketName).key(fileKey)
                  .metadata(ImmutableMap.of("created_time", createdTimeIso))
                  .build();

      PutObjectResponse putObjectResponse =
          s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

      return putObjectResponse.versionId();
    } catch (AwsServiceException | SdkClientException e) {
      throw new IOException(String.format("Error while uploading file, fileKey: %s", fileKey), e);
    }
  }

  private String uploadFileUsingMultipartUpload(String fileKey, File file, long contentLength)
      throws IOException, AwsServiceException, SdkClientException {
    List<CompletedPart> completedParts = new ArrayList<>();

    CreateMultipartUploadRequest createMultipartUploadRequest =
        CreateMultipartUploadRequest.builder().bucket(bucketName).key(fileKey).build();
    CreateMultipartUploadResponse createMultipartUploadResponse =
        s3Client.createMultipartUpload(createMultipartUploadRequest);

    long filePosition = 0;
    try (InputStream fileInputStream = new FileInputStream(file)) {
      for (int i = 1; filePosition < contentLength; i++) {
        // Because the last part could be smaller than others, adjust the part size as needed
        long partSize = Math.min(partSizeForMultiPartUpload, (contentLength - filePosition));

        UploadPartRequest uploadRequest =
            UploadPartRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .uploadId(createMultipartUploadResponse.uploadId())
                .partNumber(i)
                .build();
        RequestBody requestBody = RequestBody.fromInputStream(fileInputStream, partSize);

        UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadRequest, requestBody);
        completedParts.add(
            CompletedPart.builder().partNumber(i).eTag(uploadPartResponse.eTag()).build());

        filePosition += partSize;
      }
    }

    CompleteMultipartUploadRequest completeMultipartUploadRequest =
        CompleteMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(fileKey)
            .uploadId(createMultipartUploadResponse.uploadId())
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build();

    CompleteMultipartUploadResponse completeMultipartUploadResponse =
        s3Client.completeMultipartUpload(completeMultipartUploadRequest);

    return completeMultipartUploadResponse.versionId();
  }

  private String getOrCreateBucket(
      S3Client s3Client,
      ListBucketsResponse listBucketsResponse,
      String region,
      String exportService)
      throws IOException {

    String fullPrefix =
        String.format(
            DATA_TRANSFER_BUCKET_PREFIX_FORMAT_STRING,
            exportService.toLowerCase());
    try {
      for (Bucket bucket : listBucketsResponse.buckets()) {
        if (bucket.name().startsWith(fullPrefix)) {
          return bucket.name();
        }
      }

      for (int i = 0; i < MAX_BUCKET_CREATION_ATTEMPTS; i++) {
        String bucketName =
            String.format("%s-%s", fullPrefix, RandomStringUtils.randomNumeric(8).toLowerCase());
        try {
          CreateBucketConfiguration createBucketConfiguration =
              CreateBucketConfiguration.builder().locationConstraint(region).build();
          CreateBucketRequest createBucketRequest =
              CreateBucketRequest.builder()
                  .bucket(bucketName)
                  .createBucketConfiguration(createBucketConfiguration)
                  .build();
          s3Client.createBucket(createBucketRequest);
          return bucketName;
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
          monitor.info(() -> "Bucket name already exists");
        }
      }
      throw new IOException(
          String.format(
              "Failed to create a uniquely named bucket after %d attempts",
              MAX_BUCKET_CREATION_ATTEMPTS));

    } catch (AwsServiceException | SdkClientException e) {
      throw new IOException("Error while creating bucket", e);
    }
  }
}
