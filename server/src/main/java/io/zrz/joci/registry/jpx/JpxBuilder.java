package io.zrz.joci.registry.jpx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Range;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSink;
import com.google.common.io.CharStreams;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import io.zrz.joci.jpx.JpxLayerBuilder;
import io.zrz.joci.registry.DockerRegistry;
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

  public JpxBuilder(RegistryProvider registry) {
    this.registry = registry;
  }

  public void put(String registry, Path manifest, String tag) {

    try {

      final Digest hash = new Digest(MoreFiles.asByteSource(manifest).hash(Hashing.sha256()));

      log.debug("hash is {}", hash.toString());

      JsonNode mf = new ObjectMapper().readTree(manifest.toFile());

      JpxLayerBuilder n = new JpxLayerBuilder();

      RegistryUploadSession session = this.registry.startUpload();

      Path layer = this.registry.resolve(session.uploadId());

      System.err.println(layer);

      n.open(layer);

      for (JsonNode node : mf.get("classpath")) {

        String jar = node.get("integrity").asText();

        if (jar == null || jar.isEmpty() || !jar.startsWith("sha256:")) {
          throw new IllegalArgumentException("invalid classpath entry");
        }

        if (!this.registry.containsBlob(new Digest(jar))) {
          throw new IllegalArgumentException("missing blob: " + jar);
        }

        BlobInfo blob = this.registry.stat(new Digest(jar));

        try (InputStream strm = blob.openStream()) {

          // add it.
          n.addBlob(
              node.get("name").textValue() + ".jar",
              blob.size(),
              blob.openStream());

          strm.close();

        }

      }

      HashCode hashCode = n.close();

      System.err.println(hashCode.toString());

      BlobInfo bi = this.registry.completeUpload(session.uploadId(), new Digest(hashCode));

      System.err.println(bi.size());

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
        exposedPorts.putObject("80/tcp");

        cfg.put("Tty", false);
        cfg.put("OpenStdin", false);
        cfg.put("StdinOnce", false);

        ArrayNode env = cfg.putArray("Env");

        env.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.add("LANG=C.UTF-8");
        env.add("JAVA_HOME=/docker-java-home");
        env.add("JAVA_VERSION=11.0.1");
        env.add("LANG=C.UTF-8");
        env.add("LISTEN_PORT=80");

        cfg.putNull("Cmd");

        cfg.put("ArgsEscaped", true);

        cfg.put("Image", new Digest(hashCode).toString());

        cfg.putNull("Volumes");

        cfg.put("WorkingDir", "/");

        cfg.putArray("Entrypoint")
            .add("/bin/sh");

        cfg.putArray("OnBuild");

        cfg.putObject("Labels")
            .put("maintainer", "root");

        spec.put("os", "linux");

        spec.putObject("rootfs")
            .put("type", "layers")
            .putArray("diff_ids")
            .add(new Digest(hashCode).toString());

        String json = spec.toString();

        configBlob = this.registry.putBlob(json);

      }

      {
        ObjectNode spec = JsonNodeFactory.instance.objectNode();

        spec.putObject("config")
            .put("mediaType", "application/vnd.docker.container.image.v1+json")
            .put("digest", configBlob.digest().toString())
            .put("size", 12);

        spec.putArray("layers")
            .addObject()
            .put("mediaType", "application/vnd.docker.image.rootfs.diff.tar.gzip")
            .put("digest", new Digest(hashCode).toString())
            .put("size", bi.size());

        // we created the JAR layer. now need to create the actual manifest and write it out.

        spec.put("mediaType", "application/vnd.docker.distribution.manifest.v2+json");
        spec.put("schemaVersion", 2);

        Digest mannifestHash = new Digest(Hashing.sha256().hashBytes(spec.toString().getBytes(UTF_8)));

        Path manifestPath = this.registry.resolve(registry, mannifestHash.hash());

        Files.createDirectories(manifestPath.getParent());
        
        MoreFiles.asByteSink(manifestPath, StandardOpenOption.CREATE_NEW)
            .write(spec.toString().getBytes(UTF_8));

        final java.nio.file.Path file = this.registry.resolve(registry, tag);
        Files.deleteIfExists(file);
        Files.createSymbolicLink(file, manifestPath);

      }

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
