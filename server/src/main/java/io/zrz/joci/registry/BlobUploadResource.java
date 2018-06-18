package io.zrz.joci.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.StandardOpenOption;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Range;
import com.google.common.io.ByteSink;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import io.zrz.joci.spi.RegistryProvider;
import io.zrz.joci.spi.RegistryProvider.BlobInfo;

public class BlobUploadResource {

  private static final Logger log = LoggerFactory.getLogger(BlobUploadResource.class);

  @Context
  UriInfo uri;

  private RegistryProvider registry;

  private String uploadId;

  /**
   *
   * @param registry
   */

  public BlobUploadResource(final RegistryProvider registry, final String uploadId) {
    this.registry = registry;
    this.uploadId = uploadId;
  }

  private URI uploadEndpoint(final String uploadId) {
    return UriBuilder
        .fromResource(DockerRegistry.class)
        .path("uploads")
        .path(uploadId)
        .build();
  }

  /**
   *
   * @param req
   * @param registry
   * @param uploadId
   * @param content
   * @return
   * @throws IOException
   */

  @PATCH
  public Response patch(@HeaderParam("Range") final RangeHeader range, final InputStream content) throws IOException {

    log.info("PATCH:  reg=" + registry + ", uploadId=" + uploadId);

    registry.resumeUpload(uploadId).patch(range == null ? Range.all() : range.getRange(), content);

    final java.nio.file.Path file = this.registry.resolve(uploadId);

    final ByteSink stream = MoreFiles.asByteSink(file, StandardOpenOption.CREATE);

    final long len = stream.writeFrom(content);

    System.err.println("wrote: " + len + " to " + file);

    return Response.status(202)
        .header("Docker-Upload-Uuid", uploadId)
        .header("Docker-Distribution-Api-Version", DockerRegistry.API_VERSION)
        .location(uploadEndpoint(uploadId))
        .header("Range", "0-" + (len - 1))
        .build();
  }

  /**
   * the upload is complete.
   *
   * @param digest
   * @return
   */

  @PUT
  public Response put(@QueryParam("digest") final String hash, final InputStream content) {

    final Digest digest = new Digest(hash);

    log.info("PUT: reg=" + registry + ", id=" + uploadId + ", digest=" + digest);
    try {

      if (content != null) {

        final java.nio.file.Path file = this.registry.resolve(uploadId);

        final ByteSink stream = MoreFiles.asByteSink(file, StandardOpenOption.CREATE);

        final long len = stream.writeFrom(content);

        System.err.println("wrote: " + len + " bytes to " + file);

      }

      final BlobInfo info = registry.completeUpload(uploadId, digest);

      return Response.status(202)
          .header("Docker-Distribution-Api-Version", DockerRegistry.API_VERSION)
          .header("Docker-Content-Digest", digest.toString())
          .location(UriBuilder
              .fromResource(DockerRegistry.class)
              .path("_blobs")
              .path(digest.toString())
              .build())
          .build();

    }
    catch (final Throwable ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }

  }

}
