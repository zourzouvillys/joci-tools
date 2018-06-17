package io.zrz.joci.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Value.Immutable
public abstract class ContainerManifest implements ImageBundle {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Value.Immutable
  @Value.Style(typeImmutable = "Config")
  public abstract static class AbstractConfig {

    @JsonProperty
    public abstract String mediaType();

    @JsonProperty
    public abstract Long size();

    @JsonProperty
    public abstract Digest digest();

  }

  @JsonProperty
  public abstract int schemaVersion();

  @JsonProperty
  public abstract String mediaType();

  @JsonProperty
  public abstract Config config();

  @Override
  @JsonProperty
  public abstract List<Config> layers();

  public static ContainerManifest readFrom(final Path path) {
    try {
      return MAPPER.readValue(path.toFile(), ContainerManifest.class);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ContainerManifest fromString(final String entity) {
    try {
      return MAPPER.readValue(entity, ContainerManifest.class);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
