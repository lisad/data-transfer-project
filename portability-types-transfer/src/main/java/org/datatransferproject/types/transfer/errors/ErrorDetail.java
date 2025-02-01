/*
 * Copyright 2019 The Data Transfer Project Authors.
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
package org.datatransferproject.types.transfer.errors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

/**
 * Details about errors that happened during the transfer.
 *
 * <p>These details are stored underneath the PortabilityJob and saved to be displayed to the user.
 * *
 */
@AutoValue
@JsonDeserialize(builder = ErrorDetail.Builder.class)
public abstract class ErrorDetail {
  private static final String DATA_KEY = "Data";

  public static ErrorDetail.Builder builder() {
    return new org.datatransferproject.types.transfer.errors.AutoValue_ErrorDetail.Builder()
        .setCanSkip(false);
  }

  @JsonProperty("id")
  public abstract String id();

  @JsonProperty("title")
  public abstract String title();

  @JsonProperty("exception")
  public abstract String exception();

  @JsonProperty("canSkip")
  public abstract boolean canSkip();

  @AutoValue.Builder
  public abstract static class Builder {
    @JsonCreator
    private static ErrorDetail.Builder create() {
      return ErrorDetail.builder();
    }

    public abstract ErrorDetail build();

    @JsonProperty("id")
    public abstract Builder setId(String id);

    @JsonProperty("title")
    public abstract Builder setTitle(String title);

    @JsonProperty("exception")
    public abstract Builder setException(String exception);

    @JsonProperty("canSkip")
    public abstract Builder setCanSkip(boolean canSkip);
  }
}
