/*
 * Copyright 2021 The Data Transfer Project Authors.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.backblaze.exception.BackblazeCredentialsException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

@ExtendWith(MockitoExtension.class)
public class BackblazeDataTransferClientTest {

  @Mock
  private Monitor monitor;
  @Mock
  private BackblazeS3ClientFactory backblazeS3ClientFactory;
  @Mock
  private S3Client s3Client;
  private static File testFile;
  private static Date testUploadedDate = new GregorianCalendar(2023, Calendar.FEBRUARY,23, 15, 0).getTime();
  private static final String KEY_ID = "keyId";
  private static final String APP_KEY = "appKey";
  private static final String EXPORT_SERVICE = "exp-serv";
  private static final String FILE_KEY = "fileKey";
  private static final String VALID_BUCKET_NAME = EXPORT_SERVICE + "-data-transfer-bucket";

  @BeforeAll
  public static void setUpClass() {
    testFile = new File("src/test/resources/test.txt");
  }

  @BeforeEach
  public void setUp() {
    lenient().when(backblazeS3ClientFactory.createS3Client(anyString(), anyString(), anyString()))
        .thenReturn(s3Client);
  }

  private void createValidBucketList() {
    Bucket bucket = Bucket.builder().name(VALID_BUCKET_NAME).build();
    when(s3Client.listBuckets()).thenReturn(ListBucketsResponse.builder().buckets(bucket).build());
  }

  private void createEmptyBucketList() {
    when(s3Client.listBuckets()).thenReturn(ListBucketsResponse.builder().build());
  }

  private BackblazeDataTransferClient createDefaultClient() {
    return new BackblazeDataTransferClient(monitor, backblazeS3ClientFactory, 1000, 500);
  }

  @Test
  public void testWrongPartSize() {
    assertThrows(IllegalArgumentException.class, () -> {
      new BackblazeDataTransferClient(monitor, backblazeS3ClientFactory, 10, 0);
    });
  }

  @Test
  public void testInitBucketNameMatches() throws BackblazeCredentialsException, IOException {
    createValidBucketList();
    BackblazeDataTransferClient client = createDefaultClient();
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    verify(s3Client, times(0)).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  public void testInitBucketCreated() throws BackblazeCredentialsException, IOException {
    Bucket bucket = Bucket.builder().name("invalid-name").build();
    when(s3Client.listBuckets()).thenReturn(ListBucketsResponse.builder().buckets(bucket).build());
    BackblazeDataTransferClient client = createDefaultClient();
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    verify(s3Client, times(1)).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  public void testInitBucketNameExists() throws BackblazeCredentialsException, IOException {
    createEmptyBucketList();
    when(s3Client.createBucket(any(CreateBucketRequest.class)))
        .thenThrow(BucketAlreadyExistsException.builder().build());
    BackblazeDataTransferClient client = createDefaultClient();

    assertThrows(IOException.class, () -> {
      client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    });
    verify(monitor, atLeast(1)).info(any());
  }

  @Test
  public void testInitErrorCreatingBucket() throws BackblazeCredentialsException, IOException {
    createEmptyBucketList();
    when(s3Client.createBucket(any(CreateBucketRequest.class)))
        .thenThrow(AwsServiceException.builder().build());
    BackblazeDataTransferClient client = createDefaultClient();
    assertThrows(IOException.class, () -> {
      client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    });
  }

  @Test
  public void testInitListBucketException() throws BackblazeCredentialsException, IOException {
    when(s3Client.listBuckets()).thenThrow(S3Exception.builder().statusCode(403).build());
    BackblazeDataTransferClient client = createDefaultClient();
    assertThrows(BackblazeCredentialsException.class, () -> {
      client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    });
    verify(s3Client, atLeast(1)).close();
    verify(monitor, atLeast(1)).debug(any());
  }

  @Test
  public void testUploadFileNonInitialized() throws IOException {
    BackblazeDataTransferClient client = createDefaultClient();
    assertThrows(IllegalStateException.class, () -> {
      client.uploadFile(FILE_KEY, testFile, testUploadedDate);
    });
  }

  @Test
  public void testUploadFileSingle() throws BackblazeCredentialsException, IOException {
    final String expectedVersionId = "123";
    createValidBucketList();
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().versionId(expectedVersionId).build());
    BackblazeDataTransferClient client = createDefaultClient();
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    String actualVersionId = client.uploadFile(FILE_KEY, testFile,
            new GregorianCalendar(2023, Calendar.FEBRUARY,23, 15, 0).getTime());
    verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    assertEquals(expectedVersionId, actualVersionId);
  }

  @Test
  public void testUploadFileSingleException() throws BackblazeCredentialsException, IOException {
    createValidBucketList();
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(AwsServiceException.builder().build());
    BackblazeDataTransferClient client = createDefaultClient();
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    assertThrows(IOException.class, () -> {
      client.uploadFile(FILE_KEY, testFile, testUploadedDate);
    });
  }

  @Test
  public void testUploadFileMultipart() throws BackblazeCredentialsException, IOException {
    final String expectedVersionId = "123";
    createValidBucketList();
    when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("xyz").build());
    when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
        .thenReturn(UploadPartResponse.builder().build());
    when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
        .thenReturn(CompleteMultipartUploadResponse.builder().versionId(expectedVersionId).build());
    final long partSize = 10;
    final long fileSize = testFile.length();
    final long expectedParts = fileSize / partSize + (fileSize % partSize == 0 ? 0 : 1);
    BackblazeDataTransferClient client =
        new BackblazeDataTransferClient(monitor, backblazeS3ClientFactory, fileSize / 2, partSize);
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    String actualVersionId = client.uploadFile(FILE_KEY, testFile, testUploadedDate);
    verify(s3Client, times((int) expectedParts))
        .uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
    assertEquals(expectedVersionId, actualVersionId);
  }

  @Test
  public void testUploadFileMultipartException() throws BackblazeCredentialsException, IOException {
    createValidBucketList();
    when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("xyz").build());
    when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
        .thenThrow(AwsServiceException.builder().build());
    final long fileSize = testFile.length();
    BackblazeDataTransferClient client =
        new BackblazeDataTransferClient(monitor, backblazeS3ClientFactory, fileSize / 2,
            fileSize / 8);
    client.init(KEY_ID, APP_KEY, EXPORT_SERVICE);
    assertThrows(IOException.class, () -> {
      client.uploadFile(FILE_KEY, testFile, testUploadedDate);
    });
  }
}
