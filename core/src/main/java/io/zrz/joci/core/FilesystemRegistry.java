package io.zrz.joci.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Range;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSink;
import com.google.common.io.MoreFiles;

import io.zrz.joci.spi.RegistryProvider;
import io.zrz.joci.spi.RegistryUploadSession;

public class FilesystemRegistry implements RegistryProvider {

  private static final Logger log = LoggerFactory.getLogger(FilesystemRegistry.class);

  private final Path base;

  public FilesystemRegistry(final Path path) {
    this.base = path;
  }

  private class Session implements RegistryUploadSession {

    private final String uploadId;

    public Session(final String uploadId) {
      this.uploadId = uploadId;
    }

    @Override
    public String uploadId() {
      return this.uploadId;
    }

    @Override
    public void patch(final Range<Long> range, final InputStream content) {
      log.debug("added range {}", range);
    }

  }

  @Override
  public RegistryUploadSession startUpload() {
    final String uploadId = UUID.randomUUID().toString();
    return new Session(uploadId);
  }

  @Override
  public RegistryUploadSession resumeUpload(final String uploadId) {
    return new Session(uploadId);
  }

  @Override
  public Path resolve(final String string) {
    return this.base.resolve(string);
  }

  @Override
  public boolean containsBlob(final Digest digest) {
    return Files.exists(this.base.resolve(digest.toString()));
  }

  @Override
  public BlobInfo stat(final Digest digest) {

    final Path path = this.resolve(digest.toString());

    if (!Files.exists(path)) {
      log.warn("missing blob: {} at {}", digest, path);
      return null;
    }

    return new BlobInfo() {

      @Override
      public long size() {
        try {
          return Files.size(path);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public InputStream openStream() {
        try {
          return new FileInputStream(path.toFile());
        }
        catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public Digest digest() {
        return digest;
      }

      public String toString() {
        return "FSBlob(" + digest + ", " + path + ", " + size() + ")";
      }

    };

  }

  @Override
  public Path resolve(final String registry, final String version) {
    return this.base.resolve(registry + "/manifests/" + version);
  }

  @Override
  public BlobInfo completeUpload(final String uploadId, final Digest digest) throws IOException {

    final Path file = this.resolve(uploadId);

    final HashCode hash = MoreFiles.asByteSource(file).hash(Hashing.sha256());

    if (!hash.toString().equals(digest.hash())) {
      throw new IllegalArgumentException("invalid hash");
    }

    final Path target = this.resolve(digest.toString());

    if (Files.exists(target)) {
      Files.delete(file);
    }
    else {
      Files.move(file, this.resolve(digest.toString()));
    }

    return this.stat(digest);

  }

  @Override
  public BlobInfo putBlob(byte[] data) {

    final HashCode hash = Hashing.sha256().hashBytes(data);

    Path target = resolve(new Digest(hash).toString());

    if (Files.exists(target)) {
      return this.stat(new Digest(hash));
    }

    try {
      MoreFiles.asByteSink(target, StandardOpenOption.CREATE_NEW)
          .write(data);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return this.stat(new Digest(hash));

  }

}
