package io.zrz.joci.registry.jpx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import io.zrz.joci.jpx.JpxLayerBuilder;
import io.zrz.joci.spi.RegistryProvider;
import io.zrz.joci.spi.RegistryProvider.BlobInfo;
import io.zrz.joci.spi.RegistryUploadSession;

/**
 * creates a layer locally from a JPX manifest.
 * 
 * @author theo
 *
 */

public class JpxBuilder {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpxBuilder.class);
  private RegistryProvider registry;
  private ObjectNode manifest;
  private Layer stable;
  private Layer changing;
  private Path previousTagTarget;
  private JpxCache cache;
  private List<String> accumulatedClasspath = new LinkedList<>();

  public JpxBuilder(RegistryProvider registry, Path manifest) {
    this.registry = registry;
    this.cache = new JpxCache(Paths.get("/mnt/blobs/_cache/"));
    try {
      this.manifest = (ObjectNode) new ObjectMapper().readTree(manifest.toFile());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Digest put(String registry, String tag) {

    try {

      log.debug("creating JPX for {}:{}", registry, tag);

      ObjectNode mf = this.manifest;

      this.stable = createStableLayer(mf);
      this.changing = createChangingLayer(mf, stable);

      final BlobInfo configBlob;

      {

        ObjectNode spec = JsonNodeFactory.instance.objectNode();

        spec.put("architecture", "amd64");
        ObjectNode cfg = spec.putObject("config");

        cfg.put("Hostname", "");
        cfg.put("Domainname", "");
        cfg.put("User", "");
        cfg.put("AttachStdin", false);
        cfg.put("AttachStdout", false);
        cfg.put("AttachStderr", false);

        ObjectNode exposedPorts = cfg.putObject("ExposedPorts");

        JsonNode ports = mf.get("ports");

        if (ports != null) {

          if (ports.isObject()) {
            Iterator<String> it = ports.fieldNames();
            while (it.hasNext()) {
              String fieldName = it.next();
              exposedPorts.set(fieldName, ports.get(fieldName));
            }
          }
          else if (ports.isArray()) {
            for (JsonNode val : ports) {
              exposedPorts.putObject(val.asText());
            }
          }
          else {
            throw new IllegalArgumentException("'ports' must be object or array");
          }

        }

        cfg.put("Tty", false);
        cfg.put("OpenStdin", false);
        cfg.put("StdinOnce", false);

        ArrayNode env = cfg.putArray("Env");

        env.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.add("LANG=C.UTF-8");
        env.add("JAVA_HOME=/docker-java-home");
        env.add("JAVA_VERSION=11.0.1");
        env.add("LANG=C.UTF-8");

        if (mf.has("mainClass")) {
          env.add("MAIN_CLASS=" + mf.get("mainClass").textValue());
        }

        env.add("JAVA_OPTS=" + javaOptions(mf));

        for (JsonNode envIn : mf.get("env")) {
          env.add(envIn);
        }

        cfg.putNull("Cmd");

        cfg.put("ArgsEscaped", true);

        cfg.putNull("Volumes");

        cfg.put("WorkingDir", "/");

        cfg.putArray("Entrypoint")
            .add("/joci/entrypoint");

        cfg.putArray("OnBuild");

        cfg.putObject("Labels")
            .put("maintainer", "root");

        spec.put("os", "linux");

        ArrayNode diffs = spec.putObject("rootfs")
            .put("type", "layers")
            .putArray("diff_ids");

        // java runtime ...
        diffs.add("sha256:e93af51b6155a8dd97f6dbbc700e94635dc9a0bd245e140c7c373d8305f8ff35");
        diffs.add("sha256:fb3486253dd2f38167fba17942b51a2341b8f0de4334c74965e04bc13f684fe0");
        diffs.add("sha256:11c651819169bfcb22239d1c0021c73870dca874570d56ca8f17071fdb58c961");
        diffs.add("sha256:04924db0e17d8830859f1b1ce3de15933809185fcdb8f75c70ea3e36eb270f02");
        diffs.add("sha256:949fb6408aace446c61014ff2d4bb0467703c2f149d57a5fd060b30bd48d09ed");

        diffs.add(new Digest(stable.uncompressedHash).toString());
        diffs.add(new Digest(changing.uncompressedHash).toString());

        String json = spec.toString();

        configBlob = this.registry.putBlob(json);

      }

      {

        ObjectNode spec = JsonNodeFactory.instance.objectNode();

        spec.putObject("config")
            .put("mediaType", "application/vnd.docker.container.image.v1+json")
            .put("digest", configBlob.digest().toString())
            .put("size", configBlob.size());

        ArrayNode layers = spec.putArray("layers");

        add(layers, "cca8b8cf2f157d13678401181406e5baf05ab424a04fb8190f9e3816c5db1e29");
        add(layers, "13cf705e89452ccf724ae251641ad26bc6e8162141b3a88b688c959d8872f905");
        add(layers, "66f6961c9eb5dddcc3a449439adc5c4a03e716794daec2a41900f7fda4fb492a");
        add(layers, "805a6a4d0333bf4fa32e15672a04b71ef96caa25e163e085b4bd72831d2792b0");
        add(layers, "340a18e6e5438fb1757d6b5f31c95377976254ddb669be89513895b05fb04647");

        layers.addObject()
            .put("mediaType", "application/vnd.docker.image.rootfs.diff.tar.gzip")
            .put("digest", new Digest(stable.compressedHash).toString())
            .put("size", stable.size());

        layers.addObject()
            .put("mediaType", "application/vnd.docker.image.rootfs.diff.tar.gzip")
            .put("digest", new Digest(changing.compressedHash).toString())
            .put("size", changing.size());

        log.debug("stable hash {}", stable.compressedHash);
        log.debug("changing hash {}", changing.compressedHash);

        // we created the JAR layer. now need to create the actual manifest and write it out.

        spec.put("mediaType", "application/vnd.docker.distribution.manifest.v2+json");
        spec.put("schemaVersion", 2);

        Digest mannifestHash = new Digest(Hashing.sha256().hashBytes(spec.toString().getBytes(UTF_8)));

        log.debug("manifest hash is {}", mannifestHash);

        // the new target.
        Path manifestPath = this.registry.resolve(registry, mannifestHash.toString());

        Files.createDirectories(manifestPath.getParent());

        if (!Files.exists(manifestPath)) {

          log.info("creating manifest hash at {}", manifestPath);

          // write the new manifest out. it's a 'sha256:...'
          MoreFiles
              .asByteSink(manifestPath, StandardOpenOption.CREATE_NEW)
              .write(spec.toString().getBytes(UTF_8));

        }
        else {

          // we already had it! continue onward, maybe update the tag.

          log.info("manifest {} already exists", manifestPath);

        }

        final java.nio.file.Path file = this.registry.resolve(registry, tag);

        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {

          // this tag is already present.

          if (Files.isSymbolicLink(file)) {

            this.previousTagTarget = Files.readSymbolicLink(file);
            log.info("tag {} is moving from {} to {}", file, this.previousTagTarget, manifestPath);
            Files.deleteIfExists(file);

          }
          else {

            // we do not allow overriding a concrete hashed manifest.
            throw new IllegalArgumentException("unable to replace content hash manifest");

          }

        }
        else {

          log.info("new tag {}", file);

        }

        log.debug("linkingn from {} to {}", file, manifestPath);

        Files.createSymbolicLink(file, manifestPath);

        return mannifestHash;

      }

    }
    catch (IOException ex) {

      throw new RuntimeException(ex);

    }
  }

  /**
   * the previous manifest target, if there was one.
   * 
   * @return
   */
  public Optional<Digest> previousTagTarget() {
    if (this.previousTagTarget == null) {
      return Optional.empty();
    }
    // will always be sha256 format (or it's an error).
    return Optional.of(new Digest(this.previousTagTarget.getFileName().toString()));
  }

  private Layer createStableLayer(ObjectNode mf) throws IOException {

    List<Pair<BlobInfo, String>> blobs = new LinkedList<>();

    for (JsonNode node : mf.get("classpath")) {

      if (isChanging(node)) {
        continue;
      }

      String jar = node.get("integrity").asText();

      if (jar == null || jar.isEmpty() || !jar.startsWith("sha256:")) {
        throw new IllegalArgumentException("invalid classpath entry");
      }

      if (!this.registry.containsBlob(new Digest(jar))) {
        throw new IllegalArgumentException("missing blob: " + jar);
      }

      BlobInfo blob = this.registry.stat(new Digest(jar));

      blobs.add(Pair.of(blob, node.get("name").textValue()));

    }

    //
    Layer cache = this.cache.inCache(blobs);

    if (cache != null) {
      log.debug("got cache hit for stable: {}", blobs);
      // add the classpath to the acumulation, so it is written in the child layer.
      this.accumulatedClasspath.addAll(blobs.stream()
          .map(e -> "/joci/" + e.getValue())
          .collect(Collectors.toList()));
      return cache;
    }

    log.debug("no cache hit for stable layer.");

    JpxLayerBuilder n = new JpxLayerBuilder();

    RegistryUploadSession session = this.registry.startUpload();
    Path layer = this.registry.resolve(session.uploadId());
    n.open(layer);

    for (Pair<BlobInfo, String> e : blobs) {

      try (InputStream strm = e.getKey().openStream()) {

        BlobInfo blob = e.getKey();

        // add it.
        n.addBlob(
            e.getValue(),
            blob.size(),
            blob.openStream());

        strm.close();

      }

    }

    this.accumulatedClasspath.addAll(n.classPath());

    // add it.

    n.close();

    HashCode compressedHash = n.compressedHash();
    HashCode uncompressedHash = n.uncompressedHash();

    BlobInfo bi = this.registry.completeUpload(session.uploadId(), new Digest(compressedHash));

    return this.cache.add(new Layer(bi, blobs, compressedHash, uncompressedHash));

  }

  private Layer createChangingLayer(ObjectNode mf, Layer stable) throws IOException {

    List<Pair<BlobInfo, String>> blobs = new LinkedList<>();

    for (JsonNode node : mf.get("classpath")) {

      if (!isChanging(node)) {
        continue;
      }

      String jar = node.get("integrity").asText();

      if (jar == null || jar.isEmpty() || !jar.startsWith("sha256:")) {
        throw new IllegalArgumentException("invalid classpath entry");
      }

      if (!this.registry.containsBlob(new Digest(jar))) {
        throw new IllegalArgumentException("missing blob: " + jar);
      }

      BlobInfo blob = this.registry.stat(new Digest(jar));

      blobs.add(Pair.of(blob, node.get("name").textValue()));

    }

    //

    blobs.forEach(blob -> log.debug(" -> {}", blob));

    Layer cache = this.cache.inCache(blobs, Optional.of(stable));

    if (cache != null) {
      log.debug("found changing layer cache for {}", blobs);
      return cache;
    }

    log.debug("no cache hit for changing layer.");

    ///

    JpxLayerBuilder n = new JpxLayerBuilder();

    RegistryUploadSession session = this.registry.startUpload();

    Path layer = this.registry.resolve(session.uploadId());

    n.open(layer);

    for (Pair<BlobInfo, String> e : blobs) {

      try (InputStream strm = e.getKey().openStream()) {

        BlobInfo blob = e.getKey();

        // add it.
        n.addBlob(
            e.getValue(),
            blob.size(),
            blob.openStream());

        strm.close();

      }

    }

    n.addClasspath(this.accumulatedClasspath);

    // add it.
    n.addScript("entrypoint", mf);

    n.close();

    HashCode compressedHash = n.compressedHash();
    HashCode uncompressedHash = n.uncompressedHash();

    BlobInfo bi = this.registry.completeUpload(session.uploadId(), new Digest(compressedHash));

    log.debug("created {} / {}", bi, compressedHash.toString());

    return this.cache.add(new Layer(bi, blobs, compressedHash, uncompressedHash, stable));

  }

  private boolean isChanging(JsonNode node) {

    if (node.has("changing")) {
      return node.get("changing").asBoolean();
    }

    JsonNode moduleVersion = node.get("moduleVersion");

    if (moduleVersion == null || !moduleVersion.isObject()) {
      return true;
    }

    JsonNode id = moduleVersion.get("id");

    if (id == null || !id.isObject()) {
      return true;
    }

    String version = id.get("version").textValue();

    if (version == null || version.length() == 0 || version.toLowerCase().endsWith("-SNAPSHOT")) {
      return true;
    }

    return false;

  }

  public static class Layer {

    private HashCode compressedHash;
    private HashCode uncompressedHash;
    private Layer parent;
    private HashCode blobHash;
    private long blobSize;

    public Layer(
        long size,
        HashCode blobsHash,
        HashCode compressedHash,
        HashCode uncompressedHash,
        Optional<Layer> parent) {
      this.blobSize = size;
      this.blobHash = blobsHash;
      this.compressedHash = compressedHash;
      this.uncompressedHash = uncompressedHash;
      this.parent = parent.orElse(null);
    }

    public Layer(
        BlobInfo bi,
        List<Pair<BlobInfo, String>> blobs,
        HashCode compressedHash,
        HashCode uncompressedHash,
        Layer parent) {
      this(bi.size(), JpxCache.hash(blobs), compressedHash, uncompressedHash, Optional.ofNullable(parent));
    }

    public Layer(BlobInfo bi, List<Pair<BlobInfo, String>> blobs, HashCode compressedHash, HashCode uncompressedHash) {
      this(bi, blobs, compressedHash, uncompressedHash, null);
    }

    /**
     * a hash of all the classpath & the underlying blobs representing them.
     */

    public HashCode blobHash() {
      return this.blobHash;
    }

    public Optional<Layer> parent() {
      return Optional.ofNullable(this.parent);
    }

    public long size() {
      return this.blobSize;
    }

    /**
     * a hash of the on-disk compressed blob.
     */

    public HashCode compressedHash() {
      return this.compressedHash;
    }

    /**
     * the hash of the tarball for the blob, before it was compressed.
     */

    public HashCode uncompressedHash() {
      return this.uncompressedHash;
    }

  }

  private String javaOptions(JsonNode mf) {

    List<String> entries = new LinkedList<>();

    for (JsonNode val : mf.get("javaOptions")) {

      if (val.isTextual()) {
        String text = val.textValue();
        if (text == null) {
          continue;
        }
        entries.add(text);
      }
      else {
        throw new IllegalArgumentException("invalid javaOptions node");
      }

    }

    return entries.stream().sequential().collect(Collectors.joining(" "));

  }

  private void add(ArrayNode layers, String hash) {
    BlobInfo blob = this.registry.stat(new Digest("sha256", hash));
    layers.addObject()
        .put("mediaType", "application/vnd.docker.image.rootfs.diff.tar.gzip")
        .put("digest", blob.digest().toString())
        .put("size", blob.size());
  }

  public List<ObjectNode> missingBlobs() {

    List<ObjectNode> missing = new LinkedList<>();

    for (JsonNode node : this.manifest.get("classpath")) {

      String jar = node.get("integrity").asText();

      if (jar == null || jar.isEmpty() || !jar.startsWith("sha256:")) {
        throw new IllegalArgumentException("invalid classpath entry");
      }

      if (!this.registry.containsBlob(new Digest(jar))) {
        missing.add((ObjectNode) node);
      }

    }

    return missing;
  }

  public Layer changingLayer() {
    return this.changing;
  }

  public Layer stableLayer() {
    return this.stable;
  }

  // virtual size of the
  public long virtualSize() {
    return this.stable.size() + this.changing.size();
  }

}
