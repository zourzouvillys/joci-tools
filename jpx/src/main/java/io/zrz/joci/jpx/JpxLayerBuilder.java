package io.zrz.joci.jpx;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.ByteStreams;

/**
 * materializes a JPX image layer based on a bunch of JARs stored as blobs.
 * 
 * @author theo
 *
 */
public class JpxLayerBuilder {

  private TarArchiveOutputStream tar;
  private List<String> manifest;
  private HashingOutputStream hashingOut;
  private GzipCompressorOutputStream compressStream;
  private BufferedOutputStream outputStream;

  public JpxLayerBuilder() {
  }

  public void open(Path target) {
    try {
      Preconditions.checkArgument(this.tar == null);
      this.outputStream = new BufferedOutputStream(Files.newOutputStream(target));
      this.hashingOut = new HashingOutputStream(Hashing.sha256(), outputStream);
      this.compressStream = new GzipCompressorOutputStream(hashingOut);
      TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(compressStream);
      tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
      this.tar = tarArchiveOutputStream;
      this.manifest = new LinkedList<>();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void addBlob(JpxDepNode node) {
    Path inputFile = node.file();
    TarArchiveEntry e = new TarArchiveEntry("/joci/" + inputFile.getFileName().toString());
    putEntry(e, inputFile, node);
    manifest.add("/joci/" + inputFile.getFileName().toString());
  }

  public void addBlob(String filename, long size, InputStream in) {
    TarArchiveEntry e = new TarArchiveEntry("/joci/" + filename);
    putEntry(e, size, in);
    manifest.add("/joci/" + filename);
  }

  private void putEntry(TarArchiveEntry e, long size, InputStream stream) {
    try {
      e.setSize(size);
      // everything set to epoch 0, so we have repeatable image hashes.
      e.setModTime(0);
      this.tar.putArchiveEntry(e);
      try (InputStream input = new BufferedInputStream(stream)) {
        ByteStreams.copy(input, this.tar);
        tar.closeArchiveEntry();
      }
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void putEntry(TarArchiveEntry e, Path file, JpxDepNode node) {
    try {
      e.setSize(Files.size(file));
      // everything set to epoch 0, so we have repeatable image hashes.
      e.setModTime(0);
      this.tar.putArchiveEntry(e);
      try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
        ByteStreams.copy(input, this.tar);
        tar.closeArchiveEntry();
      }
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public HashCode close() {
    try {
      // write out classpath list.
      TarArchiveEntry e = new TarArchiveEntry("/joci/classpath.txt");
      byte[] cp = this.manifest.stream().sequential().collect(Collectors.joining(":")).getBytes(UTF_8);
      e.setSize(cp.length);
      // repeatable modified time.
      e.setModTime(0);
      tar.putArchiveEntry(e);
      tar.write(cp);
      tar.closeArchiveEntry();
      this.tar.close();
      this.compressStream.close();
      this.hashingOut.close();
      this.outputStream.close();
      return this.hashingOut.hash();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

}
