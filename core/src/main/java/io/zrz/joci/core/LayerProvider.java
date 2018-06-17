package io.zrz.joci.core;

import java.nio.file.Path;

public interface LayerProvider {

  ContainerManifest manifest(String version);

  Path resolve(Digest digest);

}
