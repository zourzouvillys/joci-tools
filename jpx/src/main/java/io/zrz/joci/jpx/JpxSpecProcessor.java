package io.zrz.joci.jpx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JpxSpecProcessor {

  private List<JpxDepNode> nodes;
  private String mainClass;
  private ArrayNode jopts;
  private ArrayNode env;
  private ArrayNode ports;

  public JpxSpecProcessor() {
    this(new LinkedList<>());
  }

  public JpxSpecProcessor(List<JpxDepNode> nodes) {
    this.nodes = nodes;
    this.jopts = JsonNodeFactory.instance.arrayNode();
    this.env = JsonNodeFactory.instance.arrayNode();
    this.ports = JsonNodeFactory.instance.arrayNode();

  }

  public JpxSpecProcessor mainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public JpxSpecProcessor javaOption(String opt) {
    jopts.add(opt);
    return this;
  }

  public JpxSpecProcessor env(String entry) {
    this.env.add(entry);
    return this;
  }

  public JpxSpecProcessor env(String key, String value) {
    this.env.add(key + "=" + value);
    return this;
  }

  public JpxSpecProcessor exposePort(String string) {
    return this;
  }

  public void loadManifest(File singleFile) {
    try {

      ObjectNode tree = new ObjectMapper().readValue(singleFile, ObjectNode.class);

      for (JsonNode dep : tree.withArray("classpath")) {
        this.nodes.add(JpxDepNode.fromJson((ObjectNode) dep));
      }

      this.mainClass = tree.path("mainClass").textValue();

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ObjectNode toNode() {

    ObjectNode root = JsonNodeFactory.instance.objectNode();

    this.nodes.forEach(node -> {

      root.withArray("classpath").add(node.node());

    });

    if (mainClass != null) {
      root.put("mainClass", this.mainClass);
    }

    if (this.jopts.size() > 0) {
      root.set("javaOptions", this.jopts);
    }

    if (this.env.size() > 0) {
      ArrayNode jenv = root.putArray("env");
      jenv.addAll(this.env);
    }

    if (this.ports.size() > 0) {
      ArrayNode ports = root.putArray("ports");
      ports.addAll(this.ports);
    }

    return root;
  }

  public ObjectNode toManifestSpec() {

    ObjectNode root = JsonNodeFactory.instance.objectNode();

    this.nodes.forEach(node -> {

      root.withArray("classpath").add(node.toManifestEntry());

    });

    if (mainClass != null) {
      root.put("mainClass", this.mainClass);
    }

    if (this.jopts.size() > 0) {
      root.set("javaOptions", this.jopts);
    }

    if (this.env.size() > 0) {
      ArrayNode jenv = root.putArray("env");
      jenv.addAll(this.env);
    }

    if (this.ports.size() > 0) {
      ArrayNode ports = root.putArray("ports");
      ports.addAll(this.ports);
    }

    return root;

  }

  public static JpxSpecProcessor fromJson(JsonNode tree) {

    if (!tree.isArray()) {
      throw new IllegalArgumentException("expected array");
    }

    List<JpxDepNode> nodes = new LinkedList<>();

    for (JsonNode node : tree) {
      nodes.add(JpxDepNode.fromJson((ObjectNode) node));
    }

    return new JpxSpecProcessor(nodes);

  }

  public Stream<Path> files() {
    return this.nodes.stream().map(e -> e.file());
  }

  public JpxSpecProcessor add(JpxDepNode node) {
    if (!nodes.stream().anyMatch(e -> e.hash().contentEquals(node.hash()))) {
      this.nodes.add(node);
    }
    return this;
  }

  public JpxSpecProcessor addAll(ArrayNode tree) {
    for (JsonNode node : tree) {
      add(JpxDepNode.fromJson((ObjectNode) node));
    }
    return this;
  }

  public JpxDepNode findBlobByHash(String hash) {
    for (JpxDepNode node : this.nodes) {
      if (node.hash().equals(hash)) {
        return node;
      }
    }
    throw new IllegalArgumentException("can't find blob with hash " + hash);
  }

}
