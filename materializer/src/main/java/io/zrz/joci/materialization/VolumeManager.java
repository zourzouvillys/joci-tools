package io.zrz.joci.materialization;

import java.io.IOException;
import java.nio.file.Path;

import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;

public interface VolumeManager {

  /**
   * allocates a new volume that can hold at least the given number of bytes.
   *
   * @param size
   *          The minimum size this volume must be able to hold.
   *
   * @return
   * @throws IOException
   *
   */

  VolumeHandle allocateVolume(long size) throws IOException;

  /**
   * restores a snapshot to a local volume.
   *
   * @param snapshotId
   *          The snapshot ID to restore from.
   *
   * @return
   */

  VolumeHandle restoreFrom(String snapshotId) throws IOException;

  /**
   * handle that represents a mutable volume, available locally.
   */

  public interface VolumeHandle {

    /**
     * creates a snapshot of this volume.
     */

    String snaphost(NewSnapshot m);

    /**
     * resumes this volume after taking a snapshot.
     */

    void resumeFrom(String snapshotId);

    /**
     * destroys this volume.
     * 
     * @throws IOException
     */

    void destroy() throws IOException;

    /**
     * the path this volume is mounted at.
     *
     * after a snapshot is made this will throw until resumeFrom is called.
     *
     */

    Path currentPath();

  }

}
