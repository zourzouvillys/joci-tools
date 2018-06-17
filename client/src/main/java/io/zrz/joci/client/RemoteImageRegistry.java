package io.zrz.joci.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.zrz.joci.core.ContainerManifest;
import io.zrz.joci.core.Digest;
import io.zrz.joci.core.LayerProvider;
import io.zrz.joci.core.LayerRegistryProvider;

public class RemoteImageRegistry implements LayerRegistryProvider {

  private Client client;
  private String repoUrl;
  private Path localBase;

  public RemoteImageRegistry(final String repoUrl, Path localBase) {
    this.repoUrl = repoUrl;
    this.localBase = localBase;
    this.client = ClientBuilder.newBuilder()
        .build();
  }

  @Override
  public LayerProvider registry(final String repository) {
    return new RepositoryProvider(repository);
  }

  private class RepositoryProvider implements LayerProvider {

    private String repository;

    public RepositoryProvider(final String repository) {
      this.repository = repository;
    }

    @Override
    public ContainerManifest manifest(final String version) {

      final WebTarget webTarget = client.target(repoUrl);

      final Invocation.Builder invocationBuilder = webTarget
          .path(repository)
          .path("manifests")
          .path(version)
          .request("application/vnd.docker.distribution.manifest.v2+json");

      try (final Response response = invocationBuilder.get(Response.class)) {

        return ContainerManifest.fromString(response.readEntity(String.class));

      }

    }

    /**
     *
     */

    @Override
    public Path resolve(final Digest digest) {

      final WebTarget webTarget = client.target(repoUrl);

      final Invocation.Builder invocationBuilder = webTarget
          .path(repository)
          .path("blobs")
          .path(digest.toString())
          .request();

      try (final Response response = invocationBuilder.get(Response.class)) {

        final byte[] bytes = response.readEntity(byte[].class);
        Path path = localBase.resolve(digest.toString());
        Files.write(path, bytes);
        return path;

      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

    }

  }

}
