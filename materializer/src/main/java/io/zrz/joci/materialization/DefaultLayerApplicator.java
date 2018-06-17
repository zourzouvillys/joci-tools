package io.zrz.joci.materialization;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

import io.zrz.joci.core.LayerProvider;
import io.zrz.joci.materialization.LayerMergeCalculator.Layer;
import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;
import io.zrz.joci.materialization.VolumeManager.VolumeHandle;

public class DefaultLayerApplicator implements LayerApplicator {

  private static final Logger log = LoggerFactory.getLogger(DefaultLayerApplicator.class);

  private LayerProvider layers;

  public DefaultLayerApplicator(final LayerProvider layers) {
    this.layers = layers;
  }

  @Override
  public void apply(final NewSnapshot m, final Layer layer, final VolumeHandle volume) {
    try {
      new ApplicationContext(layer, volume.currentPath()).applyTo();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private class ApplicationContext {

    private Layer layer;
    private Path rootfs;

    public ApplicationContext(final Layer layer, final Path rootfs) {
      this.layer = layer;
      this.rootfs = rootfs;
    }

    /**
     * number of bytes written out.
     */

    public long totalBytes() {
      return this.total;
    }

    /**
     * total added entries.
     */

    public long totalEntries() {
      return this.merged;
    }

    /**
     * total number of deletes.
     */

    public long totalDeletes() {
      return this.deletes;
    }

    ApplicationContext applyTo() throws IOException {

      if (!Files.exists(rootfs)) {
        Files.createDirectory(rootfs);
      }

      Preconditions.checkNotNull(layer.config());
      Preconditions.checkNotNull(layer.config().digest());

      final File stream = layers.resolve(layer.config().digest()).toFile();

      final FileInputStream inputStream = new FileInputStream(stream);

      final TarArchiveInputStream tarInput;

      if (layer.config().mediaType().equals("application/vnd.docker.image.rootfs.diff.tar.gzip")) {
        final GzipCompressorInputStream gunzipped = new GzipCompressorInputStream(inputStream);
        tarInput = new TarArchiveInputStream(gunzipped);
      }
      else {
        tarInput = new TarArchiveInputStream(inputStream);
      }

      TarArchiveEntry entry = tarInput.getNextTarEntry();

      this.total = 0;

      while (entry != null) {

        apply(entry, tarInput);

        // entry.get

        entry = tarInput.getNextTarEntry();

      }

      log.info(
          "{}: wrote {} bytes ({} entries, {} deletes)",
          stream.toPath(),
          this.totalBytes(),
          this.totalEntries(),
          this.totalDeletes());

      return this;

    }

    private void apply(final TarArchiveEntry entry, final TarArchiveInputStream tarInput) throws IOException {

      final Path target = Paths.get(entry.getName());

      final String fileName = target.getFileName().toString();

      if (fileName.equals(".wh..wh..opq")) {

        // delete all siblings in the filesystem.
        deleteContent(target.getParent());

      }
      else if (fileName.startsWith(".wh.")) {

        // delete from the file
        deleteFile(target.getParent().resolve(fileName.substring(4)));

      }
      else {

        merge(entry, tarInput, target);

      }

    }

    private long total = 0;
    private int deletes;
    private int merged;

    /**
     * merge this entry, adjusting permissions / owner / content.
     *
     * @param tarInput
     * @param target
     * @throws IOException
     */

    private void merge(final TarArchiveEntry entry, final TarArchiveInputStream tarInput, final Path target) throws IOException {

      this.merged++;

      final Path dest = rootfs.resolve(target);

      if (entry.isBlockDevice() || entry.isCharacterDevice()) {

        // throw new IllegalArgumentException();
        // Files.setLastModifiedTime(dest, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS));
        // Files.setAttribute(dest, "unix:mode", entry.getMode(), LinkOption.NOFOLLOW_LINKS);
        return;

      }
      else if (entry.isFIFO()) {

        // Files.setLastModifiedTime(dest, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS));
        // Files.setAttribute(dest, "unix:mode", entry.getMode(), LinkOption.NOFOLLOW_LINKS);
        throw new IllegalArgumentException();

      }
      else if (entry.isDirectory()) {

        if (!Files.exists(dest)) {
          Files.createDirectory(dest);
        }

        Files.setLastModifiedTime(dest, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS));
        Files.setAttribute(dest, "unix:mode", entry.getMode(), LinkOption.NOFOLLOW_LINKS);

      }
      else if (entry.isLink()) {

        // Files.createLink(dest, rootfs.resolve(entry.getLinkName()));

      }
      else if (entry.isSymbolicLink()) {

        final Path rel = (entry.getLinkName().startsWith("/"))
            ? rootfs.resolve(Paths.get(entry.getLinkName().substring(1)))
            : dest.getParent().resolve(entry.getLinkName());

        Files.createSymbolicLink(dest, Paths.get(entry.getLinkName()));

      }
      else if (entry.isFile()) {

        Preconditions.checkArgument(!entry.isBlockDevice());
        Preconditions.checkArgument(!entry.isCharacterDevice());

        final Path parent = dest.getParent();

        if (!Files.isDirectory(parent)) {
          Files.createDirectory(parent);
        }

        // TODO: preallocate?
        final long remain = entry.getSize();

        final long bytes = Files.copy(tarInput, dest, StandardCopyOption.REPLACE_EXISTING);

        if (bytes != remain) {
          throw new IllegalStateException();
        }

        this.total += bytes;

        Files.setLastModifiedTime(dest, FileTime.from(entry.getLastModifiedDate().getTime(), TimeUnit.MILLISECONDS));
        Files.setAttribute(dest, "unix:mode", entry.getMode(), LinkOption.NOFOLLOW_LINKS);

      }
      else {

        // entry.getDevMajor();
        // entry.getDevMinor();

        throw new IllegalArgumentException();

      }

      // private static final java.lang.String CTIME_NAME = "ctime";
      // Files.setAttribute(dest, "unix:ctime", entry.getModTime(), LinkOption.NOFOLLOW_LINKS);

      // note: to set the owner/group, we need to be running as root.
      // Files.setAttribute(dest, "unix:uid", 501 /* (int) entry.getUserId() */, LinkOption.NOFOLLOW_LINKS);
      // Files.setAttribute(dest, "unix:gid", (int) entry.getLongGroupId(), LinkOption.NOFOLLOW_LINKS);

      // entry.getMode();

    }

    /**
     * delete all entries in the given path, but not the directory itself.
     * 
     * @throws IOException
     */

    private void deleteContent(final Path parent) throws IOException {
      if (Files.exists(rootfs.resolve(parent))) {
        MoreFiles.deleteDirectoryContents(parent, RecursiveDeleteOption.ALLOW_INSECURE);
        this.deletes++;
      }
    }

    /**
     * delete a specific file/directory.
     * 
     * @throws IOException
     */

    private void deleteFile(final Path target) throws IOException {
      if (Files.deleteIfExists(rootfs.resolve(target))) {
        this.deletes++;
      }
    }

  }

}
