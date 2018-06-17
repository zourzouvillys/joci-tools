package io.zrz.joci.spi;

import java.io.InputStream;

import com.google.common.collect.Range;

public interface RegistryUploadSession {

  String uploadId();

  void patch(Range<Long> range, InputStream content);

}
