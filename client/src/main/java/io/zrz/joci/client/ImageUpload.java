package io.zrz.joci.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageUpload {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageUpload.class);

  private String registry;
  private OkHttpClient client;
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  public static final MediaType BINARY = MediaType.parse("application/octet-stream");

  public ImageUpload(String registry) {
    this.registry = Objects.requireNonNull(registry);
    this.client =
      new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(0, 1, TimeUnit.MICROSECONDS))
        .build();
  }

  public Upload createContext(Path src) {
    return createContext(MoreFiles.asByteSource(src));
  }

  public Upload createContext(ByteSource src) {
    try {
      return new Upload(src);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean exists(Digest digest) {
    try {

      Request request =
        new Request.Builder()
          .url(HttpUrl.parse(this.registry)
            .newBuilder()
            .addPathSegment("_blobs")
            .addPathSegment(digest.algorithm() + ":" + digest.hash())
            .build())
          .addHeader("Connection", "close")
          .head()
          .build();

      Response response = client.newCall(request).execute();

      if (!"registry/2.0".equals(response.header("Docker-Distribution-Api-Version", null))) {
        throw new IllegalArgumentException("invalid server API version: " + response.header("Docker-Distribution-Api-Version"));
      }

      switch (response.code()) {
        case 404:
          return false;
        case 200:
          return true;
      }

      throw new IllegalArgumentException("invalid server response: " + response.code());

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }

  }

  public class Upload {

    private String uploadUrl;
    private String uploadId;
    private ByteSource src;
    private Digest digest;
    private Boolean exists = null;

    public Upload(ByteSource src) throws IOException {
      this.src = src;
      this.digest = new Digest(src);
    }

    private Upload startUpload() {

      try {

        Request request =
          new Request.Builder()
            .url(registry + "/jars/blobs/uploads/")
            .post(RequestBody.create(JSON, ""))
            .addHeader("Connection", "close")
            .build();

        log.info("POST to {}", request.url());

        Response response = client.newCall(request).execute();

        if ((response.code() / 100) != 2) {
          throw new IllegalArgumentException("invalid server response");
        }

        if (!"registry/2.0".equals(response.header("Docker-Distribution-Api-Version", null))) {
          throw new IllegalArgumentException("invalid server API version");
        }

        String uploadPath = response.header("location");

        if (uploadPath == null) {
          throw new IllegalArgumentException("invalid upload path");
        }

        String uploadId = response.header("docker-upload-uuid", null);

        if (uploadId == null) {
          throw new IllegalArgumentException("upload ID missing");
        }

        this.uploadId = uploadId;
        this.uploadUrl = uploadPath;

        return this;

      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }

    }

    /**
     * returns false if it was not needed to be uploaded, else true if it was uploaded.
     * 
     * checks to ensure it doesn't already exist, if not already checked.
     * 
     * @return
     */

    public boolean send() {

      if (this.exists()) {
        return false;
      }

      if (this.uploadId == null) {
        this.startUpload();
      }

      try {

        HttpUrl url = HttpUrl.parse(uploadUrl).newBuilder().addQueryParameter("digest", digest.toString()).build();

        log.debug("uploading {} ({} bytes) to {}", url, src.size(), digest.toString());

        Request request =
          new Request.Builder()
            .url(url)
            .put(RequestBody.create(BINARY, src.read()))
            .addHeader("Connection", "close")
            .build();

        Response response = client.newCall(request).execute();

        if (!"registry/2.0".equals(response.header("Docker-Distribution-Api-Version", null))) {
          throw new IllegalArgumentException("invalid server API version: " + response.header("Docker-Distribution-Api-Version"));
        }
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }

      return true;

    }

    public boolean exists() {
      if (this.exists == null) {
        this.exists = ImageUpload.this.exists(this.digest);
      }
      return this.exists;
    }

  }

  public void uploadFiles(Stream<Path> paths) {
    paths
      .map(MoreFiles::asByteSource)
      .map(src -> createContext(src))
      .forEach(ctx -> ctx.send());
  }

  public UploadResponse uploadManifest(String registry, String tag, String mediaType, String manifest) {

    try {

      HttpUrl url =
        HttpUrl.parse(this.registry)
          .newBuilder()
          .addPathSegments(registry)
          .addPathSegment("manifests")
          .addPathSegment(tag)
          .build();

      log.debug("uploading {}", url);

      Request request =
        new Request.Builder()
          .url(url)
          .put(RequestBody.create(MediaType.parse(mediaType), manifest))
          .addHeader("Connection", "close")
          .build();

      Response response = client.newCall(request).execute();

      if (!"registry/2.0".equals(response.header("Docker-Distribution-Api-Version", null))) {
        throw new IllegalArgumentException("invalid server API version: " + response.header("Docker-Distribution-Api-Version"));
      }

      if (response.code() == 424) {
        // missing dependencies
        return new MissingBlobResponse(response);
      }

      return new SuccessResponse(response);

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

}
