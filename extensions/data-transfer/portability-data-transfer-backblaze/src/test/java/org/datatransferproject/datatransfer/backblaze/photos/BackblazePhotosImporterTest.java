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

package org.datatransferproject.datatransfer.backblaze.photos;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.common.models.photos.PhotosContainerResource;
import org.datatransferproject.types.transfer.auth.TokenSecretAuthData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BackblazePhotosImporterTest {
  Monitor monitor;
  TemporaryPerJobDataStore dataStore;
  ConnectionProvider streamProvider;
  BackblazeDataTransferClientFactory clientFactory;
  IdempotentImportExecutor executor;
  TokenSecretAuthData authData;
  BackblazeDataTransferClient client;
  BackblazePhotosImporter photosImporter;

  @BeforeAll
  public void setUp() {
    monitor = mock(Monitor.class);
    dataStore = mock(TemporaryPerJobDataStore.class);
    streamProvider = mock(ConnectionProvider.class);
    clientFactory = mock(BackblazeDataTransferClientFactory.class);
    executor = mock(IdempotentImportExecutor.class);
    authData = mock(TokenSecretAuthData.class);
    client = mock(BackblazeDataTransferClient.class);
    photosImporter = new BackblazePhotosImporter(monitor, dataStore, streamProvider, clientFactory,
            BackblazeConstants.PHOTOS_BASE_FOLDER_NAME);
  }

  @TempDir public Path folder;

  @Test
  public void testNullData() throws Exception {
    ImportResult result = photosImporter.importItem(UUID.randomUUID(), executor, authData, null);
    assertEquals(ImportResult.OK, result);
  }

  @Test
  public void testNullPhotosAndAlbums() throws Exception {
    System.out.println(photosImporter);
    PhotosContainerResource data = mock(PhotosContainerResource.class);
    when(data.getAlbums()).thenReturn(null);
    when(data.getPhotos()).thenReturn(null);

    ImportResult result = photosImporter.importItem(UUID.randomUUID(), executor, authData, data);
    assertEquals(ImportResult.ResultType.OK, result.getType());
  }

  @Test
  public void testEmptyPhotosAndAlbums() throws Exception {
    PhotosContainerResource data = mock(PhotosContainerResource.class);
    when(data.getAlbums()).thenReturn(new ArrayList<>());
    when(data.getPhotos()).thenReturn(new ArrayList<>());

    ImportResult result = photosImporter.importItem(UUID.randomUUID(), executor, authData, data);
    assertEquals(ImportResult.ResultType.OK, result.getType());
  }

  @Test
  public void testImportPhoto() throws Exception {
    String dataId = "dataId";
    String title = "title";
    String photoUrl = "photoUrl";
    String albumName = "albumName";
    String albumId = "albumId";
    String response = "response";
    UUID jobId = UUID.randomUUID();
    PhotoModel photoModel = new PhotoModel(title, photoUrl, "", "", dataId, albumId, false);
    PhotosContainerResource data =
        new PhotosContainerResource(Collections.emptyList(), Collections.singletonList(photoModel));

    when(executor.getCachedValue(albumId)).thenReturn(albumName);

    when(streamProvider.getInputStreamForItem(jobId, photoModel))
        .thenReturn(new InputStreamWrapper(IOUtils.toInputStream("photo content", "UTF-8")));

    when(client.uploadFile(eq("Photo Transfer/albumName/dataId.jpg"), any(), any())).thenReturn(response);
    when(clientFactory.getOrCreateB2Client(jobId, authData)).thenReturn(client);

    File file = folder.toFile();
    when(dataStore.getTempFileFromInputStream(any(), any(), any())).thenReturn(file);

    photosImporter.importItem(jobId, executor, authData, data);

    ArgumentCaptor<ImportFunction<PhotoModel, String>> importCapture =
        ArgumentCaptor.forClass(ImportFunction.class);
    verify(executor, times(1))
        .importAndSwallowIOExceptions(eq(photoModel), importCapture.capture());

    String actual = importCapture.getValue().apply(photoModel).getData();
    assertEquals(response, actual);
  }

  @Test
  public void testImportAlbum() throws Exception {
    String albumId = "albumId";
    PhotoAlbum album = new PhotoAlbum(albumId, "", "");
    ArrayList<PhotoAlbum> albums = new ArrayList<>();
    albums.add(album);
    PhotosContainerResource data = mock(PhotosContainerResource.class);
    when(data.getAlbums()).thenReturn(albums);

    photosImporter.importItem(UUID.randomUUID(), executor, authData, data);

    verify(executor, times(1))
        .executeAndSwallowIOExceptions(
            eq(albumId), eq("Caching album name for album 'albumId'"), any());
  }
}
