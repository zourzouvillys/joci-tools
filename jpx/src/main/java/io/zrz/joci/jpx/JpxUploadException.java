package io.zrz.joci.jpx;

import io.zrz.joci.client.UploadResponse;

public class JpxUploadException extends RuntimeException {

  private UploadResponse res;

  public JpxUploadException(UploadResponse res) {
    this.res = res;
  }

}
