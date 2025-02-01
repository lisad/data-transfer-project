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

package org.datatransferproject.datatransfer.backblaze.photos;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.datatransfer.backblaze.common.BackblazeDataTransferClient;
import org.datatransferproject.datatransfer.backblaze.common.BackblazeDataTransferClientFactory;
import org.datatransferproject.spi.cloud.connection.ConnectionProvider;
import org.datatransferproject.spi.cloud.storage.TemporaryPerJobDataStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.idempotentexecutor.ItemImportResult;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.common.models.photos.PhotosContainerResource;
import org.datatransferproject.types.transfer.auth.TokenSecretAuthData;

public class BackblazePhotosImporter
    implements Importer<TokenSecretAuthData, PhotosContainerResource> {

  private final TemporaryPerJobDataStore jobStore;
  private final ConnectionProvider connectionProvider;
  private final Monitor monitor;
  private final BackblazeDataTransferClientFactory b2ClientFactory;

  private final String baseFolderName;

  public BackblazePhotosImporter(
      Monitor monitor,
      TemporaryPerJobDataStore jobStore,
      ConnectionProvider connectionProvider,
      BackblazeDataTransferClientFactory b2ClientFactory,
      String baseFolderName) {
    this.monitor = monitor;
    this.jobStore = jobStore;
    this.connectionProvider = connectionProvider;
    this.b2ClientFactory = b2ClientFactory;
    this.baseFolderName = baseFolderName;
  }

  @Override
  public ImportResult importItem(
      UUID jobId,
      IdempotentImportExecutor idempotentExecutor,
      TokenSecretAuthData authData,
      PhotosContainerResource data)
      throws Exception {
    if (data == null) {
      // Nothing to do
      return ImportResult.OK;
    }

    BackblazeDataTransferClient b2Client = b2ClientFactory.getOrCreateB2Client(jobId, authData);

    if (data.getAlbums() != null && data.getAlbums().size() > 0) {
      for (PhotoAlbum album : data.getAlbums()) {
        idempotentExecutor.executeAndSwallowIOExceptions(
            album.getId(),
            String.format("Caching album name for album '%s'", album.getId()),
            () -> album.getName());
      }
    }

    final LongAdder totalImportedFilesSizes = new LongAdder();
    if (data.getPhotos() != null && data.getPhotos().size() > 0) {
      for (PhotoModel photo : data.getPhotos()) {
        idempotentExecutor.importAndSwallowIOExceptions(
            photo,
            p -> {
              ItemImportResult<String> fileImportResult =
                  importSinglePhoto(idempotentExecutor, b2Client, jobId, p);
              if (fileImportResult.hasBytes()) {
                totalImportedFilesSizes.add(fileImportResult.getBytes());
              }
              return fileImportResult;
            });
      }
    }

    return ImportResult.OK.copyWithBytes(totalImportedFilesSizes.longValue());
  }

  private ItemImportResult<String> importSinglePhoto(
      IdempotentImportExecutor idempotentExecutor,
      BackblazeDataTransferClient b2Client,
      UUID jobId,
      PhotoModel photo)
      throws IOException {
    String albumName = idempotentExecutor.getCachedValue(photo.getAlbumId());

    File file;
    try (InputStream is = connectionProvider.getInputStreamForItem(jobId, photo).getStream()) {
      file = jobStore.getTempFileFromInputStream(is, photo.getDataId(), ".jpg");
    }
    String response =
        b2Client.uploadFile(
            String.format("%s/%s/%s", baseFolderName, albumName, photo.getTitle()),
            file,
            photo.getUploadedTime()
        );
    long size = file.length();

    try {
      if (photo.isInTempStore()) {
        jobStore.removeData(jobId, photo.getFetchableUrl());
      }
    } catch (Exception e) {
      // Swallow the exception caused by Remove data so that existing flows continue
      monitor.info(
          () ->
              format(
                  "Exception swallowed while removing data for jobId %s, localPath %s",
                  jobId, photo.getFetchableUrl()),
          e);
    }

    return ItemImportResult.success(response, size);
  }
}
