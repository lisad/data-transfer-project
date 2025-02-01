package org.datatransferproject.transfer.photobucket.model.response.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserStatsResponse {
  @JsonProperty("username")
  public String username;

  @JsonProperty("availableSpace")
  public long availableSpace;

  @JsonProperty("availableImages")
  public long availableImages;
}
