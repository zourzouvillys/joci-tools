package io.zrz.joci.materialization;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import io.zrz.joci.core.Config;
import io.zrz.joci.core.Digest;
import io.zrz.joci.core.ImageBundle;

/**
 * converts a container manigest into an volume, using snapshots to represent layers.
 *
 * for each layer, we check to see if a snapshot already exists for it. if it does, we use that for the volume otherwise
 * we create a snapshot and tag it with the checksum.
 *
 * small layers are merged into later ones - the threshold is configurable, by default it is 32MB.
 *
 * volumes are sized just large enough to contain the image content. when a new layer is added, the volume is resized if
 * needed.
 *
 * to create the snapshots, we mount a volume (potentially based on a snapshot if there is one available for any lower
 * level image) and then for each layer we write out the content, unmount, and create a snapshot. we repeat this process
 * until we have created all the snapshots.
 *
 * because a layer can theoretically be used with different descendants, we use a rolling hash of the layers applied.
 * this is calculated by joining each of the digests with ':'. so a snapshot based on sha256:aaa, sha256:bbb, and
 * sha256:ccc would be sha256:(aaa:bbb:ccc).
 *
 * each snapshot is tagged with "image:rootfs:digest" and a value of the rolling digest, e.g "sha256:{hash}".
 *
 * @author theo
 *
 */

public class LayerMergeCalculator {

  private static final Logger log = LoggerFactory.getLogger(LayerMergeCalculator.class);

  /**
   * the manifest we want to be an EBS snapshot.
   */

  private final ImageBundle container;

  private List<Layer> layers = new LinkedList<>();

  private SnapshotQueryProvider controller;

  static class Layer {

    private final Config config;
    private final Layer parent;
    private HashCode rollingHash;
    private Materialization mybase;
    private String snapshotId;

    public Layer(final Config config, final Layer parent) {

      this.config = config;

      this.parent = parent;

      final Hasher hasher = Hashing.sha256().newHasher();
      hasher.putBytes(parent.rollingHash.asBytes());
      hasher.putBytes(config.digest().toHashCode().asBytes());

      this.rollingHash = hasher.hash();

    }

    public Layer(final Config config) {

      this.config = config;
      this.parent = null;
      this.rollingHash = config.digest().toHashCode();

    }

    public Materialization materialize() {
      return materialize(0);
    }

    /**
     * materializes a snapshot of this layer, either by finding an existing one, or creating one.
     */

    public Materialization materialize(final int depth) {

      if (this.snapshotId != null) {
        return ExistingSnapshot.fromSnapshotId(this.snapshotId, this.rollingHash, this.rollingSize());
      }

      // this layer doesn't exist, so we need to materialize it.

      if (parent == null) {
        return NewSnapshot.fromLayer(this);
      }

      mybase = this.parent.materialize(depth + 1);

      if (mybase instanceof NewSnapshot) {

        final NewSnapshot ns = (NewSnapshot) mybase;

        if (ns.incrementalSize() < 3.2e+7) {
          // add this layer into it.
          return ns.withLayer(this);
        }

      }

      return new NewSnapshot(mybase, ImmutableList.of(this));

    }

    private long rollingSize() {
      return this.parent == null ? size() : (size() + this.parent.size());
    }

    long size() {
      return this.config.size().longValue();
    }

    void existingSnapshot(final String snapshotId) {
      this.snapshotId = snapshotId;
    }

    @Override
    public String toString() {
      return this.config.digest() + " (" + size() + " bytes)";
    }

    public Config config() {
      return this.config;
    }

    public Digest rollingDigest() {
      return new Digest("sha256", rollingHash.toString());
    }

  }

  public interface Materialization {

    /**
     *
     */

    boolean isExistingSnapshot();

    /**
     * the cumulative size of this image, e.g the total size including all previous layers when the snapshot is turned
     * into a volume.
     */

    long rollingSize();

    /**
     * the rolling hash for this materialization.
     */

    HashCode rollingHash();

    /**
     * if this is an existing snapshot, provides the ID of it. else throws.
     */

    String snapshotId();

  }

  public static class NewSnapshot implements Materialization {

    Materialization basedOn;
    ImmutableList<Layer> includes;

    public NewSnapshot(Materialization basedOn, ImmutableList<Layer> includes) {
      this.basedOn = basedOn;
      this.includes = includes;
    }

    public static NewSnapshot fromLayer(final Layer layer) {
      return new NewSnapshot(null, ImmutableList.of(layer));
    }

    public Materialization withLayer(final Layer layer) {
      return new NewSnapshot(this.basedOn, ImmutableList.<Layer>builder().addAll(includes).add(layer).build());
    }

    public long incrementalSize() {
      return includes.stream().mapToLong(x -> x.size()).sum();
    }

    @Override
    public long rollingSize() {
      return (basedOn == null ? 0 : basedOn.rollingSize()) + includes.stream().mapToLong(x -> x.size()).sum();
    }

    @Override
    public HashCode rollingHash() {

      HashCode lastCode = null;

      if (this.basedOn != null) {
        lastCode = basedOn.rollingHash();
      }
      else if (this.includes.size() == 1) {
        return this.includes.get(0).rollingHash;
      }

      for (final Layer layer : this.includes) {
        if (lastCode == null) {
          lastCode = layer.config.digest().toHashCode();
        }
        else {
          final Hasher hasher = Hashing.sha256().newHasher();
          hasher.putBytes(lastCode.asBytes());
          hasher.putBytes(layer.config.digest().toHashCode().asBytes());
          lastCode = hasher.hash();
        }
      }

      return lastCode;

    }

    @Override
    public boolean isExistingSnapshot() {
      return false;
    }

    @Override
    public String snapshotId() {
      throw new IllegalStateException();
    }

    public Materialization getBasedOn() {
      return this.basedOn;
    }

    public ImmutableList<Layer> getIncludes() {
      return this.includes;
    }

  }

  public static class ExistingSnapshot implements Materialization {

    private String snapshotId;
    private HashCode rollingHash;
    private long size;

    public ExistingSnapshot(String snapId, HashCode rollingHash2, long size) {
      this.snapshotId = snapId;
      this.rollingHash = rollingHash2;
      this.size = size;
    }

    @Override
    public long rollingSize() {
      return size;
    }

    @Override
    public HashCode rollingHash() {
      return rollingHash;
    }

    @Override
    public boolean isExistingSnapshot() {
      return true;
    }

    @Override
    public String snapshotId() {
      return this.snapshotId;
    }

    public static ExistingSnapshot fromSnapshotId(String snapId, HashCode rollingHash, long size) {
      return new ExistingSnapshot(snapId, rollingHash, size);
    }

  }

  /**
   *
   */

  private LayerMergeCalculator(final ImageBundle container, final SnapshotQueryProvider controller) {
    this.container = Objects.requireNonNull(container);
    this.controller = Objects.requireNonNull(controller);
  }

  public static Materialization calculate(final ImageBundle container, final SnapshotQueryProvider controller) {
    return new LayerMergeCalculator(container, controller).calculate();
  }

  /**
   * perform the EBS-ification.
   *
   * starting at the lowest layer, build a series of snapshots by basing the volume on a previous snapshot.
   *
   * @return
   *
   */

  private Materialization calculate() {

    // populate the layer tree.
    container.layers().forEach(this::processLayer);

    // perform a single query for the snapshots.

    final List<Digest> useful = layers.stream()
        .map(x -> x.rollingDigest())
        .collect(Collectors.toList());

    final Map<Digest, String> found = controller.lookup(useful);

    for (final Map.Entry<Digest, String> e : found.entrySet()) {

      layers.stream()
          .filter(l -> l.rollingDigest().equals(e.getKey()))
          .forEach(l -> l.existingSnapshot(e.getValue()));

    }

    // starting from the last layer, look for a snapshot with this id.

    return this.lastLayer().materialize();

  }

  private void apply(final Materialization snapId) {

    if (snapId instanceof NewSnapshot) {
      apply((NewSnapshot) snapId);
    }
    else if (snapId instanceof ExistingSnapshot) {
      apply((ExistingSnapshot) snapId);
    }
    else {
      throw new RuntimeException();
    }

  }

  private void apply(final NewSnapshot snapId) {

    if (snapId.basedOn != null) {
      apply(snapId.basedOn);
    }

    System.err.println(" -> CREATE NEW [tag=" + snapId.rollingHash().toString() + "] "
        + toHuman(snapId.incrementalSize()) + " = "
        + snapId.includes.stream().map(x -> x.config.digest().getHash().toString().substring(0, 8) + "[" + toHuman(x.config.size()) + "]")
            .collect(Collectors.joining(", ")));

  }

  private String toHuman(final long size) {
    if (size >= 1e+9) {
      return String.format("%d GB", (long) (size / 1e+9));
    }
    else if (size >= 1e+6) {
      return String.format("%d MB", (long) (size / 1e+6));
    }
    else if (size >= 1e+3) {
      return String.format("%d KB", (long) (size / 1e+3));
    }
    return String.format("%d B", size);
  }

  private void apply(final ExistingSnapshot snapId) {
    System.err.println(" -> MATERIALIZE " + snapId);
  }

  private Layer lastLayer() {
    return this.layers.get(layers.size() - 1);
  }

  /**
   *
   * @param layer
   */

  private void processLayer(final Config layer) {

    switch (layer.mediaType()) {
      case "application/vnd.docker.image.rootfs.diff.tar.gzip":
        break;
      case "application/vnd.docker.image.rootfs.diff.tar":
        break;
      default:
        throw new IllegalArgumentException("unsupported layer type: " + layer.mediaType());
    }

    if (layers.isEmpty())
      this.layers.add(new Layer(layer));
    else
      this.layers.add(new Layer(layer, layers.get(layers.size() - 1)));

    // final ImageDiff diff = ImageTools.openImageDiff(base.resolve(layer.getDigest()));
    // diff.open();

  }

}
