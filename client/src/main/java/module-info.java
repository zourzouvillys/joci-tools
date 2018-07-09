
module joci.client {

  requires com.google.common;
  // requires jackson.annotations;
  // requires com.fasterxml.jackson.databind;
  // requires static org.immutables.value;
  // requires static org.eclipse.jdt.annotation;

  requires okhttp3;
  requires joci.core;

  exports io.zrz.joci.client;

}
