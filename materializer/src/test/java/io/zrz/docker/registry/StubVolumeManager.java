package io.zrz.docker.registry;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Preconditions;

import io.zrz.joci.materialization.VolumeManager;
import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;

public class StubVolumeManager implements VolumeManager {

  @Override
  public VolumeHandle allocateVolume(final long size) {
    System.err.println("-> allocating new volume");
    return new StubVolumeHandle(null);
  }

  @Override
  public VolumeHandle restoreFrom(final String snapshotId) {
    System.err.println("-> allocating volume from snapshot");
    return new StubVolumeHandle(snapshotId);
  }

  public class StubVolumeHandle implements VolumeHandle {

    private String snapshotId;

    public StubVolumeHandle(final String snapshotId) {
      this.snapshotId = snapshotId;
    }

    @Override
    public String snaphost(final NewSnapshot m) {
      System.err.println("-> creating snapshot with tag " + m.rollingHash().toString());
      this.snapshotId = "snap-" + m.rollingHash().toString().substring(0, 8);
      return this.snapshotId;
    }

    @Override
    public void resumeFrom(final String snapshotId) {
      Preconditions.checkArgument(this.snapshotId.equals(snapshotId), "expected %s, but volume is at %s", snapshotId, this.snapshotId);
      System.err.println("-> re-enabling volume");
      this.snapshotId = snapshotId;
    }

    @Override
    public void destroy() {
      System.err.println("-> destroying volume");
    }

    @Override
    public Path currentPath() {
      return Paths.get("/tmp/merged");
    }

  }

}
