package io.zrz.joci.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;

import io.zrz.joci.core.Digest;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageUpload {

  private String registry;
  private OkHttpClient client;
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  public static final MediaType BINARY = MediaType.parse("application/octet-stream");

  public ImageUpload(String registry) {
    this.registry = registry;
    this.client = new OkHttpClient();
  }

  public Upload upload(Path src) {
    return upload(MoreFiles.asByteSource(src));
  }

  public Upload upload(ByteSource src) {
    try {

      Request request = new Request.Builder()
          .url(registry + "/jars/blobs/uploads/")
          .post(RequestBody.create(JSON, ""))
          .build();

      Response response = client.newCall(request).execute();

      if (response.code() / 100 != 2) {
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

      return new Upload(uploadPath, uploadId, src);

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }

  }

  public boolean exists(Digest digest) {
    try {

      String path = registry + "/_blobs/" + digest.algorithm() + ":" + digest.hash();

      Request request = new Request.Builder()
          .url(path)
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

    public Upload(String uploadUrl, String uploadId, ByteSource src) throws IOException {
      this.uploadUrl = uploadUrl;
      this.uploadId = uploadId;
      this.src = src;
      this.digest = new Digest(src);
    }

    public void send() {
      try {
        HttpUrl url = HttpUrl.parse(uploadUrl).newBuilder().addQueryParameter("digest", digest.toString()).build();

        System.err.println("uploading " + url + " " + src.size() + " ---> " + digest.toString());

        Request request = new Request.Builder()
            .url(url)
            .put(RequestBody.create(BINARY, src.read()))
            .build();

        Response response = client.newCall(request).execute();

        if (!"registry/2.0".equals(response.header("Docker-Distribution-Api-Version", null))) {
          throw new IllegalArgumentException("invalid server API version: " + response.header("Docker-Distribution-Api-Version"));
        }
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }

    }

    public boolean exists() {
      return ImageUpload.this.exists(this.digest);
    }

  }

  public void uploadFiles(Stream<Path> paths) {
    paths
        .map(MoreFiles::asByteSource)
        .map(src -> upload(src))
        .filter(ctx -> !ctx.exists())
        .forEach(ctx -> ctx.send());
  }

}
