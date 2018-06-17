package io.zrz.docker.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zrz.joci.core.Digest;
import io.zrz.joci.materialization.SnapshotQueryProvider;
import io.zrz.joci.materialization.local.LocalDirectoryVolumeManager;

public class StubEbsController implements SnapshotQueryProvider {

  private static final Logger log = LoggerFactory.getLogger(StubEbsController.class);

  private LocalDirectoryVolumeManager volumeManager;

  public StubEbsController(final LocalDirectoryVolumeManager volumeManager) {
    this.volumeManager = volumeManager;
  }

  @Override
  public Map<Digest, String> lookup(final List<Digest> useful) {

    final Map<Digest, String> matches = new HashMap<>();

    for (final Digest wanted : useful) {

      final String snapId = volumeManager.hasSnapshot(wanted);

      if (snapId != null) {
        matches.put(wanted, snapId);
      }

    }

    matches.forEach((wanted, found) -> log.debug("got {} = {}", wanted, found));

    return matches;

  }

}
