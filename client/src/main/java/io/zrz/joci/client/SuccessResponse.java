package io.zrz.joci.client;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Response;

public class SuccessResponse implements UploadResponse {

  private String digest;
  private ObjectNode body;

  public SuccessResponse(Response response) {
    this.digest = response.header("Docker-Content-Digest");
    try {
      this.body = (ObjectNode) new ObjectMapper().readTree(response.body().bytes());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String digest() {
    return this.digest;
  }

  public ObjectNode body() {
    return this.body;
  }

  public String target() {
    return body().get("target").textValue();
  }

  public String status() {
    return body().get("status").textValue();
  }

  public long changingSize() {
    return this.changingLayer().get("size").longValue();
  }

  public long stableSize() {
    return this.stableLayer().get("size").longValue();
  }

  private ObjectNode stableLayer() {
    return (ObjectNode) body().get("stableLayer");
  }

  private ObjectNode changingLayer() {
    return (ObjectNode) body().get("changingLayer");
  }

  public Optional<String> previousTarget() {
    JsonNode prev = body().get("previous");
    if (prev != null) {
      return Optional.ofNullable(prev.asText());
    }
    return Optional.empty();
  }

  public String toString() {
    return "HTTP " + this.status() + ": " + body();
  }

}
