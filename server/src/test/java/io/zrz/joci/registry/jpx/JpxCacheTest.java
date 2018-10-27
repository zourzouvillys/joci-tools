package io.zrz.joci.registry.jpx;

import static com.google.common.hash.HashCode.fromString;
import static io.zrz.joci.registry.jpx.MockBlobInfo.blobInfo;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.tuple.Pair.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

import io.zrz.joci.registry.jpx.JpxBuilder.Layer;

public class JpxCacheTest {

  @Test
  public void test() throws IOException {

    Path tempdir = Files.createTempDirectory("cache-tests");

    JpxCache cache = new JpxCache(tempdir);

    assertNull(cache.inCache(asList()));
    assertNull(cache.inCache(asList(Pair.of(blobInfo("xxx"), ""))));

    cache.add(
        new Layer(
            blobInfo("ccc"),
            asList(of(blobInfo("xxx"), "")),
            fromString("1234"),
            fromString("5678")));

    Layer found = cache.inCache(asList(Pair.of(blobInfo("xxx"), "")));

    assertNotNull(found);

    assertEquals(fromString("1234"), found.compressedHash());
    assertEquals(fromString("5678"), found.uncompressedHash());
    assertEquals(blobInfo("ccc").size(), found.size());
    assertEquals(JpxCache.hash(asList(of(blobInfo("xxx"), ""))), found.blobHash());

    //

    assertNull(cache.inCache(asList(Pair.of(blobInfo("xxx"), ""))));

    MoreFiles.deleteRecursively(tempdir, RecursiveDeleteOption.ALLOW_INSECURE);

  }

}
