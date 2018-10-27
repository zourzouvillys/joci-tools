package io.zrz.joci.registry.jpx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import io.zrz.joci.core.Digest;
import io.zrz.joci.registry.jpx.JpxBuilder.Layer;
import io.zrz.joci.spi.RegistryProvider.BlobInfo;

public class JpxCache {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpxCache.class);

  private Path base;

  public JpxCache(Path base) {
    if (!Files.exists(base)) {
      try {
        Files.createDirectories(base);
      }
      catch (IOException e) {
        // TODO Auto-generated catch block
        throw new RuntimeException(e);
      }
    }
    this.base = base;
  }

  private static Hasher cacheKey(List<Pair<BlobInfo, String>> keys, Optional<Layer> parentLayer) {
    return cacheKey(hash(keys), parentLayer);
  }

  public static final HashCode hash(List<Pair<BlobInfo, String>> blobs) {
    Hasher hasher = Hashing.sha256().newHasher();
    blobs
        .stream()
        .sequential()
        .forEach(e -> {
          hasher.putBytes(e.getKey().digest().rawBytes());
          hasher.putInt(0);
          hasher.putBytes(e.getValue().getBytes(UTF_8));
        });
    return hasher.hash();
  }

  private static Hasher cacheKey(HashCode keys, Optional<Layer> parentLayer) {
    Hasher hasher = Hashing.sha256().newHasher();
    hasher.putBytes(keys.asBytes());
    parentLayer.ifPresent(parent -> {
      hasher.putInt(0);
      hasher.putBytes(parent.compressedHash().asBytes());
    });
    return hasher;
  }

  /**
   * cache with a parent layer.
   * 
   * @param blobs
   * @param stable
   * @param config
   * @return
   */

  public Layer inCache(List<Pair<BlobInfo, String>> blobs, Optional<Layer> parentLayer) {
    Hasher hasher = cacheKey(blobs, parentLayer);
    String key = hasher.hash().toString();
    return get(key, blobs, parentLayer);
  }

  public Layer inCache(List<Pair<BlobInfo, String>> blobs, Layer parentLayer) {
    return inCache(blobs, Optional.ofNullable(parentLayer));
  }

  public Layer inCache(List<Pair<BlobInfo, String>> blobs) {
    return inCache(blobs, Optional.empty());
  }

  /**
   * looks up in cache. returns null if not found or an error occured.
   * 
   * @param key
   * @param blobs
   * @param parentLayer
   * @return
   */

  @SuppressWarnings("null")
  private Layer get(String key, List<Pair<BlobInfo, String>> blobs, Optional<Layer> parentLayer) {
    try {
      log.info("cache key is {}", key);
      Path path = base.resolve(key);
      if (Files.exists(path)) {
        return mapper.readValue(path.toFile(), CacheKey.class).asLayer(parentLayer);
      }
    }
    catch (Exception e) {
      log.warn("error looking up cache", e);
      return null;
    }
    return null;
  }

  /**
   * 
   * @author theo
   *
   */

  public static class CacheKey {

    public String uncompressedHash;
    public String compressedHash;
    public String blobsHash;
    public long blobsSize;

    public CacheKey() {
    }

    public CacheKey(Layer layer) {
      this.blobsSize = layer.size();
      this.blobsHash = layer.blobHash().toString();
      this.compressedHash = layer.compressedHash().toString();
      this.uncompressedHash = layer.uncompressedHash().toString();
    }

    public Layer asLayer(Optional<Layer> parentLayer) {
      return new Layer(
          this.blobsSize,
          HashCode.fromString(this.blobsHash),
          HashCode.fromString(this.compressedHash),
          HashCode.fromString(this.uncompressedHash),
          parentLayer);
    }

  }

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * add this layer to the cache.
   * 
   * doesn't throw if an error occurs, just logs.
   * 
   * @param blobs
   * 
   * @param layer
   * @return
   */

  public Layer add(Layer layer) {
    try {

      Hasher hasher = cacheKey(layer.blobHash(), layer.parent());

      String key = hasher.hash().toString();

      log.info("adding cache key {}", key);

      if (!Files.exists(base.resolve(key))) {
        mapper.writeValue(base.resolve(key).toFile(), new CacheKey(layer));
      }

    }
    catch (IOException e) {
      log.warn("error adding to cache", e);
    }

    return layer;

  }

}
