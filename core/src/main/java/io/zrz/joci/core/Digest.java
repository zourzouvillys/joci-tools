package io.zrz.joci.core;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;

public final class Digest {

  public Digest(final ByteSource src) throws IOException {
    this.algorithm = "sha256";
    this.hash = src.hash(Hashing.sha256()).toString();
  }

  public Digest(final String value) {
    final String[] parts = value.split(":", 2);
    this.algorithm = parts[0];
    this.hash = parts[1];
  }

  public Digest(final String algo, String hash) {
    this.algorithm = algo;
    this.hash = hash;
  }

  public Digest(HashCode hash) {
    this("sha256", hash.toString());
  }

  public byte[] rawBytes() {
    return BaseEncoding.base16().lowerCase().decode(this.hash);
  }

  public HashCode toHashCode() {
    return HashCode.fromBytes(rawBytes());
  }

  final String algorithm;
  final String hash;

  @JsonValue
  @Override
  public final String toString() {
    return algorithm + ":" + hash;
  }

  public String algorithm() {
    return this.algorithm;
  }

  public String hash() {
    return this.hash;
  }

  public String getHash() {
    return this.hash;
  }

}
