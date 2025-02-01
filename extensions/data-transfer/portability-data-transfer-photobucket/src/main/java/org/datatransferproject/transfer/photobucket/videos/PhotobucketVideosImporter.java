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

package org.datatransferproject.transfer.photobucket.videos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import okhttp3.OkHttpClient;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.transfer.photobucket.client.PhotobucketClient;
import org.datatransferproject.transfer.photobucket.client.PhotobucketCredentialsFactory;
import org.datatransferproject.types.common.models.videos.VideoAlbum;
import org.datatransferproject.types.common.models.videos.VideoModel;
import org.datatransferproject.types.common.models.videos.VideosContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.util.UUID;

import static org.datatransferproject.transfer.photobucket.data.PhotobucketConstants.*;

public class PhotobucketVideosImporter
    implements Importer<TokensAndUrlAuthData, VideosContainerResource> {

  private final Monitor monitor;
  private final OkHttpClient httpClient;
  private final JobStore jobStore;
  private final PhotobucketCredentialsFactory credentialsFactory;
  private final ObjectMapper objectMapper;

  @VisibleForTesting
  public PhotobucketVideosImporter(
      PhotobucketCredentialsFactory credentialsFactory,
      Monitor monitor,
      OkHttpClient httpClient,
      JobStore jobStore,
      ObjectMapper objectMapper) {
    monitor.debug(() -> "Starting PhotobucketVideosImporter initialization");
    this.monitor = monitor;
    this.httpClient = httpClient;
    this.jobStore = jobStore;
    this.credentialsFactory = credentialsFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  public ImportResult importItem(
      UUID jobId,
      IdempotentImportExecutor idempotentExecutor,
      TokensAndUrlAuthData authData,
      VideosContainerResource data)
      throws Exception {
    Preconditions.checkArgument(
        data.getAlbums() != null || data.getVideos() != null,
        String.format("Error: There is no data to import for jobId=[%s]", jobId));
    if (authData == null) {
      throw new IllegalArgumentException("Wrong token instance");
    }

    Credential credential = credentialsFactory.createCredential(authData);
    String requester =
        jobStore.findJob(jobId) != null ? jobStore.findJob(jobId).exportService() : "DTP";
    PhotobucketClient photobucketClient =
        new PhotobucketClient(
            jobId, monitor, credential, httpClient, jobStore, objectMapper, requester);

    // create empty album in root where all data structure is going to be saved
    monitor.debug(() -> String.format("Creating top level video album for jobId=[%s]", jobId));

    String topLevelTitle = requester + MAIN_VIDEO_ALBUM_TITLE_SUFFIX;
    idempotentExecutor.executeAndSwallowIOExceptions(
        jobId.toString(),
        topLevelTitle,
        () -> photobucketClient.createTopLevelAlbum(topLevelTitle));

    // import albums
    monitor.debug(() -> String.format("Starting video albums import for jobId=[%s]", jobId));
    for (VideoAlbum album : data.getAlbums()) {
      idempotentExecutor.executeAndSwallowIOExceptions(
          album.getId(),
          album.getName(),
          () -> photobucketClient.createAlbum(album, ALBUM_TITLE_PREFIX));
    }

    // import photos
    monitor.debug(() -> String.format("Starting videos import for jobId=[%s]", jobId));
    for (VideoModel video : data.getVideos()) {
      idempotentExecutor.executeAndSwallowIOExceptions(
          video.getAlbumId() + "-" + video.getDataId(),
          video.getName(),
          () -> photobucketClient.uploadVideo(video).wasSuccessful());
    }
    monitor.debug(() -> String.format("Video import complete, for jobId=[%s]", jobId));

    return new ImportResult(ImportResult.ResultType.OK);
  }
}
