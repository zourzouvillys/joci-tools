package io.zrz.joci.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;

/**
 * provides an image
 *
 * @author theo
 *
 */

public class FileLayers implements ImageBundle, LayerProvider {

  private ImmutableList<Chunk> chunks;

  public FileLayers(final List<Chunk> chunks) {
    this.chunks = ImmutableList.copyOf(chunks);
  }

  @Value.Immutable
  @Value.Style(typeImmutable = "Chunk")
  public static abstract class AbstractChunk {

    public abstract String id();

    public abstract Path layerFile();

    public abstract Config config();

  }

  @Value.Immutable
  @Value.Style(typeImmutable = "FileManifest")
  public abstract static class AbstractFileManifest {

    @JsonProperty("Config")
    public abstract String config();

    @JsonProperty("RepoTags")
    public abstract String repoTags();

    @JsonProperty("Layers")
    public abstract List<String> layers();

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Value.Immutable
  @Value.Style(typeImmutable = "Layer")
  public abstract static class AbstractLayer {

    @JsonProperty
    public abstract String id();

    @JsonProperty
    public abstract String parent();

    @JsonProperty
    public abstract String created();

    @JsonProperty
    public abstract ObjectNode container_config();

    @JsonProperty
    public abstract String container();

    public static Layer parse(final Path json) {
      try {
        return MAPPER.readValue(json.toFile(), Layer.class);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static FileLayers open(final Path folder) throws IOException {

    final Path manifest = folder.resolve("manifest.json");

    FileManifest[] mf;
    try {
      mf = MAPPER.readValue(manifest.toFile(), FileManifest[].class);
    }
    catch (IOException e1) {
      throw new RuntimeException(e1);

    }

    final Map<String, Layer> layers = mf[0].layers()
        .stream()
        .map(path -> manifest.getParent().resolve(path))
        .map(path -> Layer.parse(path.getParent().resolve("json")))
        .collect(Collectors.toMap(e -> e.id(), e -> e));

    final Set<String> parents = layers.values().stream()
        .filter(x -> x.parent() != null)
        .map(x -> x.parent())
        .collect(Collectors.toSet());

    final SetView<String> root = Sets.difference(layers.keySet(), parents);

    final String topLayer = root.iterator().next();

    final List<Chunk> chunks = buildLayers(topLayer, layers, manifest.getParent());

    return new FileLayers(chunks);

  }

  private static List<Chunk> buildLayers(final String layerId, final Map<String, Layer> layers, final Path base) throws IOException {

    final Path layer = base.resolve(layerId).resolve("layer.tar");

    final Config.Builder layerConfig = Config.builder()
        .digest(new Digest("sha256", MoreFiles.asByteSource(layer).hash(Hashing.sha256()).toString()))
        .size(Long.valueOf(Files.size(layer)))
        .mediaType("application/vnd.docker.image.rootfs.diff.tar");

    final Layer config = layers.get(layerId);

    System.err.println(layerConfig);

    final Chunk self = Chunk.builder()
        .id(layerId)
        .config(layerConfig.build())
        .layerFile(layer)
        .build();

    if (config.parent() == null) {
      final LinkedList<Chunk> res = new LinkedList<>();
      res.add(self);
      return res;
    }

    final List<Chunk> res = buildLayers(config.parent(), layers, base);
    res.add(self);
    return res;

  }

  @Override
  public List<Config> layers() {
    return this.chunks.stream().map(e -> e.config()).collect(Collectors.toList());
  }

  @Override
  public ContainerManifest manifest(String version) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Path resolve(Digest digest) {
    return this.chunks.stream().filter(e -> e.config().digest().equals(digest)).findFirst()
        .map(x -> x.layerFile()).orElseThrow(IllegalArgumentException::new);
  }

}
