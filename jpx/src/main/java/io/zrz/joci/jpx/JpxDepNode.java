package io.zrz.joci.jpx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;

public class JpxDepNode {

  private ObjectNode node;

  /**
   * 
   */

  public JpxDepNode(ObjectNode node) {
    this.node = node;
  }

  /**
   * 
   */

  public String hash() {
    return node.get("integrity").asText();
  }

  /**
   * 
   */

  public String toString() {
    return this.node.toString();
  }

  /**
   * new dependency nodes from JSON. calculates integrity if not already specified and local file is declared.
   * 
   * @param node
   * @return
   */

  public static JpxDepNode fromJson(ObjectNode node) {
    if (node.has("file") && !node.has("integrity")) {
      try {
        HashCode hash = Files.asByteSource(new File(node.get("file").asText())).hash(Hashing.sha256());
        node.put("integrity", "sha256:" + hash.toString());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return new JpxDepNode(node);
  }

  public Path file() {
    return Paths.get(node.get("file").asText());
  }

  public JsonNode toManifestEntry() {

    ObjectNode cp = node.deepCopy();
    cp.remove("file");
    return cp;

  }

}
