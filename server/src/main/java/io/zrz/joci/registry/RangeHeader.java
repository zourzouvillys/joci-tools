package io.zrz.joci.registry;

import com.google.common.collect.Range;

public class RangeHeader {

  public static enum Unit {
    BYTES;
  }

  private Unit unit;

  private long from;

  private long to;

  public RangeHeader(final Unit unit, final long from, final long to) {
    this.unit = unit;
    this.from = from;
    this.to = to;
  }

  public Unit getUnit() {
    return unit;
  }

  public long getFrom() {
    return from;
  }

  public long getTo() {
    return to;
  }

  public static RangeHeader fromValue(final String range) {
    if (range == null) {
      return null;
    }
    final String[] tokens = range.replace("Range: ", "").split("=");
    final Unit unit = Unit.valueOf(tokens[0].toUpperCase());
    final String[] fromTo = tokens[1].split("-");
    final long from = Long.valueOf(fromTo[0]);
    final long to = Long.valueOf(fromTo[1]);
    return new RangeHeader(unit, from, to);
  }

  @Override
  public String toString() {
    return String.format("Range: %s=%d-%d", unit.name().toLowerCase(), from, to);
  }

  public Range<Long> getRange() {
    return Range.closed(getFrom(), getTo());
  }

}
