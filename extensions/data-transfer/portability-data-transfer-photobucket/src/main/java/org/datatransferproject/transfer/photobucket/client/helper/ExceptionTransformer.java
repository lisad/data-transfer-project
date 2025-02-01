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

package org.datatransferproject.transfer.photobucket.client.helper;

import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.types.CopyExceptionWithFailureReason;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.transfer.photobucket.model.error.*;

public class ExceptionTransformer {
  private final Monitor monitor;

  public ExceptionTransformer(Monitor monitor) {
    this.monitor = monitor;
  }

  // Exception mapper, to transform PB exceptions to open source exceptions
  public CopyExceptionWithFailureReason transformException(Exception e) {
    monitor.severe(e::getMessage);

    if (e instanceof OverlimitException) {
      return new DestinationMemoryFullException(
          "Photobucket storage is full. Please check your plan limits.", e);
    } else if (e instanceof AlbumImportException) {
      return new PhotobucketException("Unable to transfer album data. Please try again later.", e);
    } else if (e instanceof MediaFileIsTooLargeException) {
      return new PhotobucketException("Unable tp transfer media file - file is too large.", e);
    } else if (e instanceof ResponseParsingException || e instanceof GraphQLException) {
      return new PhotobucketException(
          "Unable to process Photobucket response. Please try again later.", e);
    } else if (e instanceof WrongStatusCodeException
        || e instanceof WrongStatusCodeRetriableException) {
      return new PhotobucketException(
          "Wrong status code provided by Photobucket. Please try again later.", e);
    } else return new PhotobucketException(e.getMessage(), e);
  }
}
