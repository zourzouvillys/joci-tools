package io.zrz.joci.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;

import org.glassfish.grizzly.http.util.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import io.zrz.joci.registry.jpx.JpxBuilder;
import io.zrz.joci.spi.RegistryProvider;
import io.zrz.joci.spi.RegistryProvider.BlobInfo;
import io.zrz.joci.spi.RegistryUploadSession;

/**
 * API endpoint mapper
 *
 * @author theo
 *
 */

@Path("/v2")
@Singleton
public class DockerRegistry {

  private static final Logger log = LoggerFactory.getLogger(DockerRegistry.class);

  static final String API_VERSION = "registry/2.0";

  private final RegistryProvider registry;

  @Context
  UriInfo uri;

  public DockerRegistry(final RegistryProvider registry) {
    this.registry = registry;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response apiCheck() {
    return Response.status(200).entity("{}").header("Docker-Distribution-Api-Version", API_VERSION).build();
  }

  /**
   * start a new upload.
   *
   * @throws IOException
   */

  @POST
  @Path("/{registry:[^_].*}/blobs/uploads/")
  @Produces("*/*")
  public Response startUpload(@PathParam("registry") final String registry, final InputStream content)
      throws IOException {

    log.info("UPLOAD started registry={}", registry);

    // read the upload. should be 0 bytes.
    final long len = ByteStreams.exhaust(content);

    if (len > 0) {
      log.warn("upload started with data: {}", len);
    }

    final RegistryUploadSession upload = this.registry.startUpload();

    return Response.accepted().location(this.uploadEndpoint(upload.uploadId()))
        .header("Docker-Upload-Uuid", upload.uploadId()).header("Docker-Distribution-Api-Version", API_VERSION)
        .build();

  }

  private URI uploadEndpoint(final String uploadId) {
    return UriBuilder.fromResource(DockerRegistry.class).path("uploads").path(uploadId).build();
  }

  /**
   * handle for the upload
   *
   * @param uploadId
   * @return
   */

  @Path("/uploads/{id:[^/]+}")
  public BlobUploadResource upload(@PathParam("id") final String uploadId) {
    return new BlobUploadResource(this.registry, uploadId);
  }

  /**
   * uploads a new manifest.
   *
   * @param req
   * @param registry
   * @param version
   * @param manifest
   *
   * @return
   */

  @PUT
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Consumes("application/vnd.docker.distribution.manifest.v2+json")
  public Response putManifest(@Context final UriInfo req, @PathParam("registry") final String registry,
      @PathParam("version") final String version, final InputStream manifest) {

    log.info("PUT MANIFEST: registry={} version={}", registry, version);

    if (version.startsWith("sha256:")) {
      return Response.status(400)
          .build();
    }

    try {

      final java.nio.file.Path tempFile = Files.createTempFile("manifest", ".json");

      try {

        MoreFiles.asByteSink(tempFile, StandardOpenOption.TRUNCATE_EXISTING).writeFrom(manifest);

        final Digest hash = new Digest(MoreFiles.asByteSource(tempFile).hash(Hashing.sha256()));

        log.debug("hash is {}", hash.toString());

        final java.nio.file.Path file = this.registry.resolve(registry, version);

        if (Files.exists(file) && !Files.isSymbolicLink(file)) {
          log.warn("ignoring attempt to overwrite content digest");
          return Response.status(403)
              .header("Docker-Content-Digest", hash.toString())
              .build();
        }

        Files.createDirectories(file.getParent());

        // the target file based on the hash.
        final java.nio.file.Path real = file.getParent().resolve(hash.toString());

        // the real target already exists, so nothing to do.
        if (Files.exists(real)) {

          return Response.notModified().header("Docker-Distribution-Api-Version", API_VERSION)
              .header("Docker-Content-Digest", hash.toString()).build();

        }

        // real is repo/manifests/{hash}
        // tempFile is uploaded content
        // file is the repo/manifests/{tag}

        // move from tempfile to the hash
        Files.move(tempFile, real);

        // create link from the tagged path to the actual file.

        Files.deleteIfExists(file);

        Files.createSymbolicLink(file, real);

        log.debug(MoreFiles.asCharSource(real, StandardCharsets.UTF_8).read());

        //
        return Response
            .created(UriBuilder
                .fromResource(DockerRegistry.class)
                .path(registry)
                .path("manifests")
                .path(version).build())
            .header("Docker-Distribution-Api-Version", API_VERSION)
            .header("Docker-Content-Digest", hash.toString()).build();

      }
      finally {

        Files.deleteIfExists(tempFile);

      }

    }
    catch (final Throwable ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }

  }

  /**
   * accepts a JPX manifest, and creates a virtual JPX image.
   * 
   * @param req
   * @param registry
   * @param version
   * @param manifest
   * @return
   */

  @PUT
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Consumes("application/vnd.jpx.app.manifest.v1+json")
  @Produces("application/json;charset=UTF-8")
  public Response putJpxManifest(@Context final UriInfo req,
      @PathParam("registry") final String registry,
      @PathParam("version") final String version,
      final InputStream manifest) {

    log.info("PUT JPX MANIFEST: registry={} version={}", registry, version);

    if (version.startsWith("sha256:")) {
      return Response.status(400)
          .build();
    }

    try {

      final java.nio.file.Path tempFile = Files.createTempFile("manifest", ".json");

      try {

        MoreFiles.asByteSink(tempFile, StandardOpenOption.TRUNCATE_EXISTING).writeFrom(manifest);

        JpxBuilder b = new JpxBuilder(this.registry, tempFile);

        if (!b.missingBlobs().isEmpty()) {

          ObjectNode res = JsonNodeFactory.instance.objectNode();

          res.put("status", "missingBlobs");
          ArrayNode missing = res.putArray("missing");

          b.missingBlobs()
              .forEach(e -> {
                missing.add(e);
              });

          return Response.status(424)
              .header("Docker-Distribution-Api-Version", API_VERSION)
              .entity(res.toString())
              .build();

        }

        Digest hash = b.put(registry, version);

        ObjectNode res = JsonNodeFactory.instance.objectNode();

        if (b.previousTagTarget().isPresent()) {

          Digest prev = b.previousTagTarget().get();

          if (!prev.hash().equals(hash.hash())) {

            res.put("status", "replaced");
            res.put("previous", prev.toString());

          }
          else {

            res.put("status", "unchanged");

          }

        }
        else {

          res.put("status", "created");

        }

        res.put("target", hash.toString());

        res.put("virtualSize", b.virtualSize());

        res.putObject("stableLayer")
            .put("size", b.stableLayer().size())
            .put("digest", new Digest(b.stableLayer().compressedHash()).toString())
            .put("contentDigest", new Digest(b.stableLayer().uncompressedHash()).toString());

        res.putObject("changingLayer")
            .put("size", b.changingLayer().size())
            .put("digest", new Digest(b.changingLayer().compressedHash()).toString())
            .put("contentDigest", new Digest(b.changingLayer().uncompressedHash()).toString());

        //
        return Response
            .created(
                UriBuilder
                    .fromResource(DockerRegistry.class)
                    .path(registry)
                    .path("manifests")
                    .path(version)
                    .build())
            .header("Docker-Distribution-Api-Version", API_VERSION)
            .header("Docker-Content-Digest", hash.toString())
            .entity(res.toString())
            .build();

      }
      finally {

        Files.deleteIfExists(tempFile);

      }

    }
    catch (final Throwable ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }

  }

  @PUT
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Consumes("application/vnd.docker.distribution.manifest.v1+prettyjws")
  public Response putV1Manifest(@Context final UriInfo req, @PathParam("registry") final String registry,
      @PathParam("version") final String version, final InputStream manifest) {

    log.warn("PUT [V1] MANIFEST: registry={} version={}", registry, version);

    return Response.status(400).build();

  }

  @HEAD
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Produces("application/vnd.docker.distribution.manifest.v2+json")
  public Response statManifest(@Context final UriInfo req, @PathParam("registry") final String registry,
      @PathParam("version") final String version) throws IOException {

    final java.nio.file.Path file = this.registry.resolve(registry, version);

    log.info("HEAD MANIFEST: registry={} version={} at={}", registry, version, file);

    if (!Files.exists(file)) {
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("Content-Length", Long.valueOf(0)).build();
    }

    return Response.ok().header("Docker-Distribution-Api-Version", API_VERSION).header("Docker-Content-Digest",
        "sha256:" + MoreFiles.asByteSource(file).hash(Hashing.sha256()).toString()).build();

  }

  @GET
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Produces("application/vnd.docker.distribution.manifest.v2+json")
  public Response getManifest(@PathParam("registry") final String registry,
      @PathParam("version") final String version) throws IOException {

    final java.nio.file.Path file = this.registry.resolve(registry, version);

    log.info("GET MANIFEST: registry={} version={} at={}", registry, version, file);

    if (!Files.exists(file)) {
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("Content-Length", Long.valueOf(0)).build();
    }

    return Response.ok(file.toFile()).header("Docker-Distribution-Api-Version", API_VERSION)
        .header("Docker-Content-Digest",
            "sha256:" + MoreFiles.asByteSource(file).hash(Hashing.sha256()).toString())
        .build();

  }

  @GET
  @Path("/{registry:[^_].*}/manifests/{version:.+}")
  @Produces("text/plain")
  public Response getPlainManifest(@PathParam("registry") final String registry,
      @PathParam("version") final String version) throws IOException {

    final java.nio.file.Path file = this.registry.resolve(registry, version);

    log.info("GET MANIFEST: registry={} version={} at={}", registry, version, file);

    if (!Files.exists(file)) {
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("Content-Length", Long.valueOf(0)).build();
    }

    return Response.ok("sha256:" + MoreFiles.asByteSource(file).hash(Hashing.sha256()).toString())
        .header("Docker-Distribution-Api-Version", API_VERSION).header("Docker-Content-Digest",
            "sha256:" + MoreFiles.asByteSource(file).hash(Hashing.sha256()).toString())
        .build();

  }

  //

  /**
   * stat a blob to see if it exists.
   *
   * @param registry
   * @param algo
   * @param digest
   *
   * @return
   * @throws IOException
   */

  @HEAD
  @Path("/_blobs/sha256:{hash:[a-f0-9]+}")
  public Response stat(@PathParam("hash") final String hash) throws IOException {

    final Digest digest = new Digest("sha256", hash);

    System.err.println("HEAD: reg=" + this.registry + ", hash=" + digest);

    final BlobInfo blob = this.registry.stat(digest);

    if (blob == null) {
      return Response.status(404).header("Content-Type", "application/json; charset=utf-8")
          .header("Docker-Distribution-Api-Version", API_VERSION).header("Content-Length", Long.valueOf(0))
          .build();
    }

    return Response.ok(blob.openStream(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .type(MediaType.APPLICATION_OCTET_STREAM_TYPE).header("Docker-Distribution-Api-Version", API_VERSION)
        .header("Docker-Content-Digest", digest.toString()).header(HttpHeaders.ETAG, digest.toString())
        .header("Content-Length", Long.valueOf(blob.size())).build();

  }

  @GET
  @Path("/{registry:[^_].*}/blobs/sha256:{hash:[a-f0-9]+}")
  public Response getBlobRedirect(@PathParam("registry") final String registry, @PathParam("hash") final String hash)
      throws IOException, UriBuilderException, IOException {

    final Digest digest = new Digest("sha256", hash);

    final BlobInfo blob = this.registry.stat(digest);

    if (blob == null) {
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("X-Registry", registry).header("X-Digest", hash).header("Content-Length", Long.valueOf(0))
          .build();
    }

    return sendBlob(blob, digest);

    // return Response
    // .temporaryRedirect(
    // UriBuilder.fromResource(DockerRegistry.class).path("_blobs").path(digest.toString()).build())
    // .type(MediaType.APPLICATION_OCTET_STREAM_TYPE).header("Docker-Distribution-Api-Version", API_VERSION)
    // .header("Docker-Content-Digest", digest.toString()).header(HttpHeaders.ETAG, digest.toString())
    // .header("Content-Length", Long.valueOf(blob.size())).build();

  }

  @HEAD
  @Path("/{registry:[^_].*}/blobs/sha256:{hash:[a-f0-9]+}")
  public Response statBlobRedirect(@PathParam("registry") final String registry, @PathParam("hash") final String hash)
      throws IOException {

    final Digest digest = new Digest("sha256", hash);

    final BlobInfo blob = this.registry.stat(digest);

    if (blob == null) {
      log.info("couldn't find {} {}", registry, digest);
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("Content-Length", Long.valueOf(0)).build();
    }

    return Response
        .temporaryRedirect(
            UriBuilder.fromResource(DockerRegistry.class).path("_blobs").path(digest.toString()).build())
        .type(MediaType.APPLICATION_OCTET_STREAM_TYPE).header("Docker-Distribution-Api-Version", API_VERSION)
        .header("Docker-Content-Digest", digest.toString())
        .header(HttpHeaders.ETAG, new EntityTag(digest.toString()).getValue())
        .header("Content-Length", Long.valueOf(blob.size())).build();

  }

  /**
   * fetch info about the blob
   *
   * @param req
   * @param registry
   * @param algo
   * @param digest
   * @return
   * @throws IOException
   */

  @GET
  @Path("/_blobs/sha256:{hash:[a-f0-9]+}")
  public Response getBlob(@PathParam("hash") final String hash) throws IOException {

    final Digest digest = new Digest("sha256", hash);

    log.info("GET: registry={} digest={}", this.registry, digest);

    final BlobInfo blob = this.registry.stat(digest);

    if (blob == null) {
      return Response.status(404).header("Docker-Distribution-Api-Version", API_VERSION)
          .header("Content-Length", Long.valueOf(0)).build();
    }

    return Response.ok(blob.openStream(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .header("Docker-Distribution-Api-Version", API_VERSION)
        .header("Docker-Content-Digest", digest.toString()).header(HttpHeaders.ETAG, digest.toString())
        .header("Content-Length", Long.valueOf(blob.size())).build();

  }

  Response sendBlob(BlobInfo blob, Digest digest) throws IOException {

    return Response.ok(blob.openStream(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
        .header("Docker-Distribution-Api-Version", API_VERSION)
        .header(HttpHeaders.ETAG, new EntityTag(digest.toString()).getValue())
        .header("Docker-Content-Digest", digest.toString()).header(HttpHeaders.ETAG, digest.toString())
        .header("Content-Length", Long.valueOf(blob.size())).build();

  }

}
