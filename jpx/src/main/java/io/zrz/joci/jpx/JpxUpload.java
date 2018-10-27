package io.zrz.joci.jpx;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.MoreFiles;

import io.zrz.joci.client.ImageUpload;
import io.zrz.joci.client.MissingBlobResponse;
import io.zrz.joci.client.SuccessResponse;
import io.zrz.joci.client.UploadResponse;

public class JpxUpload {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpxUpload.class);

  private JpxSpecProcessor spec;
  private ImageUpload uploader;
  private List<ObjectNode> uploaded = new LinkedList<>();

  public JpxUpload(String endpoint) {
    this.spec = new JpxSpecProcessor();
    this.uploader = new ImageUpload(endpoint);
  }

  public void loadManifest(File singleFile) {
    this.spec.loadManifest(singleFile);
  }

  public JpxUpload addToClasspath(ArrayNode tree) {
    this.spec.addAll(tree);
    return this;
  }

  public JpxUpload addToClasspath(Path path) {
    ObjectNode main = JsonNodeFactory.instance.objectNode();
    main.put("name", MoreFiles.getNameWithoutExtension(path));
    main.put("file", path.toString());
    spec.add(JpxDepNode.fromJson(main));
    return this;
  }

  public SuccessResponse create(String repository, String tag) {

    // submit the manifest.
    UploadResponse res = submit(repository, tag);

    // if the response is that we are missing blobs, let's provide them.
    if (res instanceof MissingBlobResponse) {

      List<ObjectNode> missing = ((MissingBlobResponse) res)
          .missingBlobs();

      log.info("repo missing {} blobs, uploading", missing.size());

      this.uploadBlobs(missing);

      res = submit(repository, tag);

    }

    if (res instanceof SuccessResponse) {
      log.info("image with id {}", ((SuccessResponse) res).digest());
      return (SuccessResponse) res;
    }

    throw new JpxUploadException(res);

  }

  public ObjectNode toJsonManifest() {
    return spec.toManifestSpec();
  }

  public ObjectNode toNode() {
    return spec.toNode();
  }

  private UploadResponse submit(String repository, String tag) {
    UploadResponse res = uploader.uploadManifest(
        repository,
        tag,
        "application/vnd.jpx.app.manifest.v1+json",
        spec
            .toManifestSpec()
            .toString());
    log.info("JPX upload response: {}", res);
    return res;
  }

  public void uploadBlobs(List<ObjectNode> missing) {

    Stream<Path> list = missing.stream()
        .map(n -> n.get("integrity").asText())
        .map(hash -> this.spec.findBlobByHash(hash))
        .peek(blob -> log.info("uploading blob: {}", blob))
        .map(dep -> dep.file());
    this.uploaded.addAll(missing);
    uploader.uploadFiles(list);

  }

  public JpxUpload option(String string) {
    this.spec.javaOption(string);
    return this;
  }

  public JpxUpload env(String key, String value) {
    this.spec.env(key, value);
    return this;
  }

  public JpxUpload exposeTcp(int port) {
    spec.exposePort(port + "/tcp");
    return this;
  }

  public JpxUpload expose(String expose) {
    spec.exposePort(expose);
    return this;
  }

  public JpxUpload mainClass(String className) {
    spec.mainClass(className);
    return this;
  }

  public JpxUpload property(String key, boolean value) {
    spec.javaOption("-D" + key + "=" + value);
    return this;
  }

  public JpxUpload enable(JpxJvmOption opt) {
    spec.javaOption(opt.arg());
    return this;
  }

  public JpxUpload openToAllUnnamed(String module, Collection<String> packageNames) {
    for (String packageName : packageNames) {
      this.open(module, packageName, "ALL-UNNNAMED");
    }
    return this;
  }

  private JpxUpload open(String module, String packageName, String accessorModule) {
    this.option("--add-opens=" + module + "/" + packageName + "=" + accessorModule);
    return this;
  }

  public JpxUpload env(String env) {
    this.spec.env(env);
    return this;
  }

  public UploadStats uploadStats() {
    return new UploadStats(this.uploaded);
  }

}
