package io.zrz.joci.materialization;

import java.io.IOException;
import java.util.Objects;

import com.google.common.base.Preconditions;

import io.zrz.joci.materialization.LayerMergeCalculator.ExistingSnapshot;
import io.zrz.joci.materialization.LayerMergeCalculator.Layer;
import io.zrz.joci.materialization.LayerMergeCalculator.Materialization;
import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;
import io.zrz.joci.materialization.VolumeManager.VolumeHandle;

/**
 * materializes a snapshot.
 *
 * @author theo
 *
 */

public class SnapshotMaterializer {

  /**
   * create a snapshot from a materialization spec.
   *
   * @param actions
   * @throws IOException
   */

  public static ExistingSnapshot fromMaterialization(final Materialization m, final VolumeManager vmgr, final LayerApplicator applicator) throws IOException {

    if (m.isExistingSnapshot()) {
      return (ExistingSnapshot) m;
    }

    try (final VolumeContext ctx = new VolumeContext(vmgr, applicator)) {
      return ctx.materialize((NewSnapshot) m);
    }

  }

  /**
   *
   */

  private static class VolumeContext implements AutoCloseable {

    private VolumeManager vmgr;
    private VolumeHandle volume;
    private LayerApplicator applicator;

    public VolumeContext(final VolumeManager vmgr, final LayerApplicator applicator) {
      this.vmgr = vmgr;
      this.applicator = applicator;
    }

    private ExistingSnapshot materialize(final NewSnapshot m) throws IOException {

      if (m.getBasedOn() == null) {

        Preconditions.checkState(this.volume == null);
        this.volume = vmgr.allocateVolume(m.rollingSize());

      }
      else if (m.getBasedOn().isExistingSnapshot()) {

        this.initializeWith(m.getBasedOn().snapshotId(), m.rollingSize());

      }
      else {

        // need to build it.
        this.initializeWith(this.materialize((NewSnapshot) m.getBasedOn()).snapshotId(), m.rollingSize());

      }

      m.getIncludes().forEach(l -> applyLayer(m, l));

      final String snapId = this.generateSnapshot(m);

      return ExistingSnapshot.fromSnapshotId(snapId, m.rollingHash(), m.rollingSize());

    }

    private void applyLayer(final NewSnapshot m, final Layer l) {

      this.applicator.apply(m, l, this.volume);

    }

    private String generateSnapshot(final NewSnapshot m) {
      return Objects.requireNonNull(this.volume.snaphost(m));
    }

    private void initializeWith(final String snapshotId, final long requiredsize) throws IOException {

      if (this.volume == null) {
        this.volume = this.vmgr.restoreFrom(snapshotId);
      }

      this.volume.resumeFrom(snapshotId);

    }

    @Override
    public void close() throws IOException {
      if (this.volume != null) {
        this.volume.destroy();
        this.volume = null;
      }
    }

  }

}
