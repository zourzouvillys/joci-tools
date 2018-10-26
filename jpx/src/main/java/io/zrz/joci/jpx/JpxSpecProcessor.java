package io.zrz.joci.jpx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.HashCode;

import io.zrz.joci.client.ImageUpload;

public class JpxSpecProcessor {

  private List<JpxDepNode> nodes;

  public JpxSpecProcessor(List<JpxDepNode> nodes) {
    this.nodes = nodes;
  }

  /**
   * 
   * @param args
   * @throws IOException
   * @throws JsonProcessingException
   */

  public static void main(String[] args) throws JsonProcessingException, IOException {

    ObjectMapper mapper = new ObjectMapper();
    JsonNode tree = mapper.readTree(new File("/Users/theo/git/fga-sandbox/flow/build/dependencies.json"));
    JpxSpecProcessor spec = fromJson(tree);
    spec.nodes.forEach(System.err::println);
    System.err.println(spec.nodes.size());

    // now do the upload itself.
    ImageUpload uploader = new ImageUpload("http://localhost:6666/v2/");
    uploader.uploadFiles(spec.files());
    System.err.println("uploaded " + spec.nodes.size() + " blobs");

    //

    ObjectNode upload = spec.toManifestSpec();
    String res = uploader.uploadManifest("fga/flow", "latest", "application/vnd.jpx.app.manifest.v1+json", upload.toString());
    System.err.println(res);

    // create the layer.
    JpxLayerBuilder b = new JpxLayerBuilder();
    b.open(Paths.get("/tmp/layer1"));
    spec.nodes.stream().sorted(Comparator.comparing(e -> e.hash())).forEach(b::addBlob);
    HashCode hash = b.close();
    System.err.println(hash.toString());

  }

  private ObjectNode toManifestSpec() {

    ObjectNode mf = JsonNodeFactory.instance.objectNode();

    this.nodes.forEach(node -> {

      mf.withArray("classpath").add(node.toManifestEntry());

    });

    return mf;
  }

  private static JpxSpecProcessor fromJson(JsonNode tree) {

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

}
