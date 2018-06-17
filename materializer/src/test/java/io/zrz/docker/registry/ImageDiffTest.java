package io.zrz.docker.registry;

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;

import io.zrz.joci.materialization.DefaultLayerApplicator;
import io.zrz.joci.materialization.LayerMergeCalculator;
import io.zrz.joci.materialization.SnapshotMaterializer;
import io.zrz.joci.materialization.LayerMergeCalculator.ExistingSnapshot;
import io.zrz.joci.materialization.LayerMergeCalculator.Materialization;
import io.zrz.joci.materialization.local.LocalDirectoryVolumeManager;
import io.zrz.joci.materialization.local.LocalImageRegistry;

public class ImageDiffTest {

  @Test
  public void test() throws IOException {

    // provides the images and manifests
    final LocalImageRegistry registry = new LocalImageRegistry(
        Paths.get("/var/folders/nh/w_tlvcls78ggy5llmpc749g40000gn/T/blobs8052656806604026882/"));

    final LocalDirectoryVolumeManager volumeManager = new LocalDirectoryVolumeManager(Paths.get("/tmp/volumes"));

    final Materialization actions = LayerMergeCalculator.calculate(
        registry.registry("service/something").manifest("latest"),
        new StubEbsController(volumeManager));

    final ExistingSnapshot existingSnapshot = SnapshotMaterializer
        .fromMaterialization(
            actions,
            volumeManager,
            new DefaultLayerApplicator(registry.registry("service/something")));

    System.err.println(existingSnapshot);

  }

  //

}
