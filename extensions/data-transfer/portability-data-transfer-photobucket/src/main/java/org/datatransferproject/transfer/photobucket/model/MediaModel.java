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

package org.datatransferproject.transfer.photobucket.model;

import java.util.Date;

public class MediaModel {
  private final String title;
  private final String fetchableUrl;
  private final String description;
  private final String mediaType;
  private final String albumId;
  private final boolean inTempStore;
  private final Date uploadedTime;
  public MediaModel(
      String title,
      String fetchableUrl,
      String description,
      String mediaType,
      String albumId,
      boolean inTempStore,
      Date uploadedTime) {
    this.title = title;
    this.fetchableUrl = fetchableUrl;
    this.description = description;
    this.mediaType = mediaType;
    this.albumId = albumId;
    this.inTempStore = inTempStore;
    this.uploadedTime = uploadedTime;
  }

  public String getTitle() {
    return title;
  }

  public String getFetchableUrl() {
    return fetchableUrl;
  }

  public String getDescription() {
    return description;
  }

  public String getAlbumId() {
    return albumId;
  }

  public Date getUploadedTime() {
    return uploadedTime;
  }

  public String getMediaType() {
    return mediaType;
  }

  public boolean isInTempStore() {
    return inTempStore;
  }
}
