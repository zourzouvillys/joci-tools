package io.zrz.joci.materialization.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;

import io.zrz.joci.core.Digest;
import io.zrz.joci.materialization.VolumeManager;
import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;

/**
 * an ineffecient volume manager which just places each volume in a folder below a specified directory.
 *
 * snapshots are done by creating copies of the directory.
 *
 * @author theo
 *
 */

public class LocalDirectoryVolumeManager implements VolumeManager {

  private static final Logger log = LoggerFactory.getLogger(LocalDirectoryVolumeManager.class);

  private Path mountpoints;

  public LocalDirectoryVolumeManager(final Path mountpoints) {
    this.mountpoints = mountpoints;
  }

  /**
   * generate a unique local mountpoint for this volume.
   * 
   * @throws IOException
   */

  @Override
  public VolumeHandle allocateVolume(final long volumeSize) throws IOException {

    log.debug("create new volume ({} GB)", volumeSize);

    final byte[] bytes = new byte[8];
    ThreadLocalRandom.current().nextBytes(bytes);

    final String volumeId = "vol-" + BaseEncoding.base16().lowerCase().omitPadding().encode(bytes);

    Files.createDirectories(mountpoints.resolve(volumeId));

    return new DefaultVolumeHandle(mountpoints.resolve(volumeId));

  }

  @Override
  public VolumeHandle restoreFrom(final String snapshotId) throws IOException {

    final byte[] bytes = new byte[8];
    ThreadLocalRandom.current().nextBytes(bytes);
    final String volumeId = "vol-" + BaseEncoding.base16().lowerCase().omitPadding().encode(bytes);

    final Path src = mountpoints.resolve(snapshotId);
    final Path dst = mountpoints.resolve(volumeId);

    log.debug("copying from {} to {}", src, dst);

    //
    FileUtils.copyDirectory(
        src.toFile(),
        dst.toFile(),
        true);

    return new DefaultVolumeHandle(dst, snapshotId);

  }

  private class DefaultVolumeHandle implements VolumeHandle {

    private Path path;
    private String snapshotId;

    public DefaultVolumeHandle(final Path path) {
      this.path = path;
    }

    public DefaultVolumeHandle(final Path path, final String snapshotId) {
      this.path = path;
      this.snapshotId = snapshotId;
    }

    @Override
    public String snaphost(final NewSnapshot m) {

      this.snapshotId = "snap-" + m.rollingHash().toString().substring(0, 8);
      final Path dst = this.path.getParent().resolve(this.snapshotId);
      Preconditions.checkState(!Files.exists(dst), "snapshot %s already exists", snapshotId);

      log.debug("created snapshot {} dst={} src={}", snapshotId, dst, this.path);

      Process p;
      try {
        p = new ProcessBuilder("rsync", "-rac", ".", dst.toString())
            .directory(this.path.toFile())
            .inheritIO()
            .start();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

      int val;
      try {
        val = p.waitFor();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // FileUtils.copyDirectory(this.path.toFile(), dst.toFile(), true);

      log.debug("created snapshot {} dst={} src={} ({})", snapshotId, dst, this.path, val);

      if (val != 0)
        throw new IllegalArgumentException("exited with " + val);

      return this.snapshotId;

    }

    @Override
    public void resumeFrom(final String snapshotId) {

      Preconditions.checkState(
          this.snapshotId.equals(snapshotId),
          "needed snapshot %s but volume is %s",
          snapshotId,
          this.snapshotId);

    }

    @Override
    public void destroy() throws IOException {
      log.debug("destroyed volume {}", path);
      FileUtils.deleteDirectory(this.path.toFile());
    }

    @Override
    public Path currentPath() {
      return path;
    }

  }

  public String hasSnapshot(final Digest wanted) {
    final String snapId = "snap-" + wanted.getHash().substring(0, 8);
    return Files.exists(this.mountpoints.resolve(snapId))
        ? snapId
        : null;
  }

}
