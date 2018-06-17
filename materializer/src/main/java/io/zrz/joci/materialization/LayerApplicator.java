package io.zrz.joci.materialization;

import io.zrz.joci.materialization.LayerMergeCalculator.Layer;
import io.zrz.joci.materialization.LayerMergeCalculator.NewSnapshot;
import io.zrz.joci.materialization.VolumeManager.VolumeHandle;

public interface LayerApplicator {

  /**
   * applies a layer to the given volume.
   *
   * @param snapop
   * @param layer
   * @param volume
   */

  void apply(NewSnapshot snapop, Layer layer, VolumeHandle volume);

}
