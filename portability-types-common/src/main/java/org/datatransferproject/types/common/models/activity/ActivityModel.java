package org.datatransferproject.types.common.models.activity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ActivityModel {
  private final String service;
  private final String action;
  private final Instant timestamp;

  @JsonCreator
  public ActivityModel(
      @JsonProperty("service") String service,
      @JsonProperty("action") String action,
      @JsonProperty("timestamp") Instant timestamp) {
    this.service = service;
    this.action = action;
    this.timestamp = timestamp;
  }

  public String getService() {
    return service;
  }

  public String getAction() {
    return action;
  }

  public Instant gettimestamp() { return timestamp; }

  @Override
  public int hashCode() {
    return Objects.hash(getService(), getAction(), gettimestamp());
  }

}