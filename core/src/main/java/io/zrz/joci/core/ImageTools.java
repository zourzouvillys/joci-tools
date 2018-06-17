package io.zrz.joci.core;

import java.nio.file.Path;

public class ImageTools {

  public static ContainerManifest openContainerManifest(final Path path) {
    return ContainerManifest.readFrom(path);
  }

}
