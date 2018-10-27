package io.zrz.joci.client;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Response;

public class MissingBlobResponse implements UploadResponse {

  private ObjectNode response;
  private int status;

  public MissingBlobResponse(Response response) {
    this.status = response.code();
    try {
      this.response = (ObjectNode) new ObjectMapper().readTree(response.body().bytes());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<ObjectNode> missingBlobs() {
    ArrayNode items = this.response.withArray("missing");
    List<ObjectNode> missing = new LinkedList<>();
    for (JsonNode m : items) {
      missing.add((ObjectNode) m);
    }
    return missing;
  }

  public String toString() {
    return "HTTP " + this.status + " " + this.response.toString();

  }

}
