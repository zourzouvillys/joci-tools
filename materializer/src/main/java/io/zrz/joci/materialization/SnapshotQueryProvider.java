package io.zrz.joci.materialization;

import java.util.List;
import java.util.Map;

import io.zrz.joci.core.Digest;

/**
 * interface for querying EBS.
 */

public interface SnapshotQueryProvider {

  /**
   * looks up the given layer identifiers, returning any found.
   *
   * @param wanted
   *          the layer identifiers that should be search for.
   *
   * @return a map of any layers which were already found, the key is the the provided layer ID, the value is the
   *         snapshot identifier.
   */

  Map<Digest, String> lookup(final List<Digest> wanted);

}
