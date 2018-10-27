package io.zrz.joci.jpx;

public enum JpxJvmOption {

  HeapDumpOnOutOfMemoryError("-XX:+HeapDumpOnOutOfMemoryError"),

  DenyIllegalAccess("--illegal-access=deny")

  //
  ;

  private String arg;

  JpxJvmOption(String arg) {
    this.arg = arg;
  }

  public String arg() {
    return this.arg;
  }

}
