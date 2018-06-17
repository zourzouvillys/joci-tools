package io.zrz.joci.cli;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.IntSupplier;

import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Option;

import io.zrz.joci.core.FileLayers;
import io.zrz.joci.materialization.DefaultLayerApplicator;
import io.zrz.joci.materialization.LayerMergeCalculator;
import io.zrz.joci.materialization.SnapshotMaterializer;
import io.zrz.joci.materialization.LayerMergeCalculator.ExistingSnapshot;
import io.zrz.joci.materialization.LayerMergeCalculator.Materialization;
import io.zrz.joci.materialization.local.LocalDirectoryVolumeManager;
import io.zrz.joci.materialization.local.LocalSnapshotQueryProvider;

/**
 * generates a snapshot using the given open container image spec. well, actually, backward compatible with original
 * docker images.
 *
 * @author theo
 *
 */

public class Main implements IntSupplier {

  // @Option(name = "--registry", description = "V2 image repository to use")
  // String registryUrl = "http://localhost:8080/v2";

  @Option(name = { "-m", "--mountpoint" }, description = "path on filesystem to mount volume")
  String mountPath = "/mnt/";

  @Option(name = "--dry-run", description = "just list actions to perform, don't actually create snapshots")
  boolean dryRun = false;

  @Arguments(description = "path to local docker image export")
  String imageDirectory;

  @Override
  public int getAsInt() {
    try {
      // final RemoteImageRegistry registry = new RemoteImageRegistry("http://localhost:5000/v2");

      final LocalDirectoryVolumeManager volumeManager = new LocalDirectoryVolumeManager(Paths.get(mountPath));

      final FileLayers bundle = FileLayers.open(Paths.get(imageDirectory));

      final Materialization diff = LayerMergeCalculator.calculate(
          bundle,
          new LocalSnapshotQueryProvider(volumeManager));

      final ExistingSnapshot existingSnapshot = SnapshotMaterializer
          .fromMaterialization(
              diff,
              volumeManager,
              new DefaultLayerApplicator(bundle));

      System.err.println(existingSnapshot);
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return 0;

  }

  public void main(final String[] args) {
    System.exit(SingleCommand.singleCommand(Main.class).parse(args).getAsInt());
  }

}
