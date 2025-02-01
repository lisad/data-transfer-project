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

package org.datatransferproject.datatransfer.backblaze.videos;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.backblaze.common.BackblazeConstants;
import org.datatransferproject.datatransfer.backblaze.common.BackblazeDataTransferClient;
import org.datatransferproject.datatransfer.backblaze.common.BackblazeDataTransferClientFactory;
import org.datatransferproject.spi.cloud.connection.ConnectionProvider;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore.InputStreamWrapper;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.idempotentexecutor.ImportFunction;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.types.common.models.videos.VideoModel;
import org.datatransferproject.types.common.models.videos.VideosContainerResource;
import org.datatransferproject.types.transfer.auth.TokenSecretAuthData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BackblazeVideosImporterTest {

  Monitor monitor;
  TemporaryPerJobDataStore dataStore;
  ConnectionProvider streamProvider;
  BackblazeDataTransferClientFactory clientFactory;
  IdempotentImportExecutor executor;
  TokenSecretAuthData authData;
  BackblazeDataTransferClient client;
  BackblazeDataTransferClientFactory b2ClientFactory;
  BackblazeVideosImporter videosImporter;
  @TempDir public Path folder;

  @BeforeAll
  public void setUp() {
    monitor = mock(Monitor.class);
    dataStore = mock(TemporaryPerJobDataStore.class);
    streamProvider = mock(ConnectionProvider.class);
    clientFactory = mock(BackblazeDataTransferClientFactory.class);
    executor = mock(IdempotentImportExecutor.class);
    authData = mock(TokenSecretAuthData.class);
    client = mock(BackblazeDataTransferClient.class);
    videosImporter = new BackblazeVideosImporter(monitor, dataStore, streamProvider, clientFactory,
            BackblazeConstants.VIDEOS_BASE_FOLDER_NAME);
  }

  @Test
  public void testNullData() throws Exception {
    ImportResult result = videosImporter.importItem(UUID.randomUUID(), executor, authData, null);
    assertEquals(ImportResult.OK, result);
  }

  @Test
  public void testNullVideos() throws Exception {
    VideosContainerResource data = mock(VideosContainerResource.class);
    when(data.getVideos()).thenReturn(null);

    ImportResult result = videosImporter.importItem(UUID.randomUUID(), executor, authData, data);
    assertEquals(ImportResult.ResultType.OK, result.getType());
  }

  @Test
  public void testEmptyVideos() throws Exception {
    VideosContainerResource data = mock(VideosContainerResource.class);
    when(data.getVideos()).thenReturn(new ArrayList<>());

    ImportResult result = videosImporter.importItem(UUID.randomUUID(), executor, authData, data);
    assertEquals(ImportResult.ResultType.OK, result.getType());
  }

  @Test
  public void testImportVideo() throws Exception {
    String dataId = "dataId";
    String title = "title";
    String videoUrl = "videoUrl";
    String description = "description";
    String encodingFormat = "video/mp4";
    String albumName = "albumName";
    String albumId = "albumId";
    String response = "response";
    UUID jobId = UUID.randomUUID();

    VideoModel videoObject =
        new VideoModel(title, videoUrl, description, encodingFormat, dataId, albumId, false, null);
    ArrayList<VideoModel> videos = new ArrayList<>();
    videos.add(videoObject);
    VideosContainerResource data = mock(VideosContainerResource.class);
    when(data.getVideos()).thenReturn(videos);

    when(executor.getCachedValue(albumId)).thenReturn(albumName);

    when(streamProvider.getInputStreamForItem(jobId, videoObject))
        .thenReturn(new InputStreamWrapper(IOUtils.toInputStream("video content", "UTF-8")));

    when(dataStore.getTempFileFromInputStream(any(), any(), any())).thenReturn(folder.toFile());
    when(client.uploadFile(eq("Video Transfer/dataId.mp4"), any(), any())).thenReturn(response);
    when(clientFactory.getOrCreateB2Client(jobId, authData)).thenReturn(client);

    videosImporter.importItem(jobId, executor, authData, data);

    ArgumentCaptor<ImportFunction<VideoModel, String>> importCapture =
        ArgumentCaptor.forClass(ImportFunction.class);
    verify(executor, times(1))
        .importAndSwallowIOExceptions(eq(videoObject), importCapture.capture());

    String actual = importCapture.getValue().apply(videoObject).getData();
    assertEquals(response, actual);
  }
}
