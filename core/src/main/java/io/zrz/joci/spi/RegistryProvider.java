package io.zrz.joci.spi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import io.zrz.joci.core.Digest;

public interface RegistryProvider {

  RegistryUploadSession startUpload();

  RegistryUploadSession resumeUpload(String uploadId);

  Path resolve(String string);

  Path resolve(String registry, String version);

  boolean containsBlob(Digest digest);

  BlobInfo stat(Digest digest);

  public interface BlobInfo {

    long size() throws IOException;

    InputStream openStream() throws IOException;

  }

  BlobInfo completeUpload(String uploadId, Digest digest) throws IOException;

}
