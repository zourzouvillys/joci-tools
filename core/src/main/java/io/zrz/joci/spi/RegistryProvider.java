package io.zrz.joci.spi;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.hash.HashCode;

import io.zrz.joci.core.Digest;

public interface RegistryProvider {

  RegistryUploadSession startUpload();

  RegistryUploadSession resumeUpload(String uploadId);

  Path resolve(String string);

  /**
   * resolves the file for a manifests of the given tag in the specified registry.
   *
   * @param registry
   * @param version
   * @return
   */

  Path resolve(String registry, String version);

  boolean containsBlob(Digest digest);

  BlobInfo stat(Digest digest);

  public interface BlobInfo {

    long size() throws IOException;

    InputStream openStream() throws IOException;

    Digest digest();

  }

  BlobInfo completeUpload(String uploadId, Digest digest) throws IOException;

  /**
   * simple blob put (for small config/manifest objects).
   * 
   * @param data
   * @return
   */

  BlobInfo putBlob(byte[] data);

  default BlobInfo putBlob(String json) {
    return putBlob(json.getBytes(UTF_8));
  }


}
