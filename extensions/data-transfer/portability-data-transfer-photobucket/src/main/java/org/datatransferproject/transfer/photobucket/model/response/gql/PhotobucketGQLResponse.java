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

package org.datatransferproject.transfer.photobucket.model.response.gql;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.ArrayList;

public class PhotobucketGQLResponse {
  @JsonProperty("data")
  public Data data;

  @JsonProperty("errors")
  public ArrayList<Error> errors;

  public String getCreatedAlbumId() throws IOException {
    if (data != null && data.createAlbum != null && data.createAlbum.id != null) {
      return data.createAlbum.id;
    } else if (!errors.isEmpty() && errors.get(0) != null) {
      Error error = errors.get(0);
      throw new IOException(error.name + ": " + error.message);
    } else {
      throw new IOException("Neither data nor error were provided, unable to create album");
    }
  }

  public String getRootAlbumId() throws IOException {
    if (data != null && data.getProfile != null && data.getProfile.defaultAlbum != null) {
      return data.getProfile.defaultAlbum;
    } else if (!errors.isEmpty() && errors.get(0) != null) {
      Error error = errors.get(0);
      throw new IOException(error.name + ": " + error.message);
    } else {
      throw new IOException("Neither data nor error were provided, unable to get root album id");
    }
  }
}

class Data {
  @JsonProperty("createAlbum")
  public CreateAlbum createAlbum;

  @JsonProperty("getProfile")
  public GetProfile getProfile;
}

class CreateAlbum {
  @JsonProperty("id")
  public String id;
}

class GetProfile {
  @JsonProperty("defaultAlbum")
  public String defaultAlbum;
}

class Error {
  @JsonProperty("message")
  public String message;

  @JsonProperty("name")
  public String name;
}
