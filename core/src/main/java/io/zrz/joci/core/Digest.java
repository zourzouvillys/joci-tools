package io.zrz.joci.core;

import com.google.common.hash.HashCode;
import com.google.common.io.BaseEncoding;

public class Digest {

  public Digest(final String value) {
    final String[] parts = value.split(":", 2);
    this.algorithm = parts[0];
    this.hash = parts[1];
  }

  public Digest(final String algo, String hash) {
    this.algorithm = algo;
    this.hash = hash;
  }

  public byte[] rawBytes() {
    return BaseEncoding.base16().lowerCase().decode(this.hash);
  }

  public HashCode toHashCode() {
    return HashCode.fromBytes(rawBytes());
  }

  final String algorithm;
  final String hash;

  @Override
  public String toString() {
    return algorithm + ":" + hash;
  }

  public String hash() {
    return this.hash();
  }

  public String getHash() {
    return this.hash;
  }

}
