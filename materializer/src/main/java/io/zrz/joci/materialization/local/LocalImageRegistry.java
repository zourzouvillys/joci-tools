package io.zrz.joci.materialization.local;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.zrz.joci.core.ContainerManifest;
import io.zrz.joci.core.Digest;
import io.zrz.joci.core.ImageTools;
import io.zrz.joci.core.LayerProvider;
import io.zrz.joci.core.LayerRegistryProvider;

public class LocalImageRegistry implements LayerRegistryProvider {

  private Path base;

  /**
   *
   * @param path
   *          root directory containing the images.
   *
   */
  public LocalImageRegistry(final Path path) {
    this.base = path;
  }

  /**
   *
   * @param repository
   * @param version
   * @return
   */

  protected Path manifestPath(final String repository, final String version) {
    return base.resolve(Paths.get(repository, "manifest." + version));
  }

  /**
   *
   */

  @Override
  public LayerProvider registry(final String registry) {

    return new LayerProvider() {

      @Override
      public Path resolve(final Digest digest) {
        return base.resolve(digest.toString());
      }

      @Override
      public ContainerManifest manifest(final String version) {
        return ImageTools.openContainerManifest(manifestPath(registry, version));
      }

    };

  }

}
