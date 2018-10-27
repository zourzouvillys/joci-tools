package io.zrz.joci.jpx;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class UploadStats {

  private List<ObjectNode> uploaded;

  public UploadStats(List<ObjectNode> uploaded) {
    this.uploaded = uploaded;
  }

  public int count() {
    return this.uploaded.size();
  }

  public String toString() {
    return count() + " files";
  }

}
