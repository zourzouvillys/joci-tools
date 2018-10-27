package io.zrz.joci.registry.jpx;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.google.common.hash.Hashing;

import io.zrz.joci.core.Digest;
import io.zrz.joci.spi.RegistryProvider.BlobInfo;

public class MockBlobInfo implements BlobInfo {

  private byte[] data;

  MockBlobInfo(String data) {
    this.data = data.getBytes();
  }

  @Override
  public long size() {
    return data.length;
  }

  @Override
  public InputStream openStream() {
    return new ByteArrayInputStream(data);
  }

  @Override
  public Digest digest() {
    return new Digest(Hashing.sha256().hashBytes(data));
  }

  public static MockBlobInfo blobInfo(String data) {
    return new MockBlobInfo(data);
  }

}
