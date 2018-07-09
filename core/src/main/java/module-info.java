module joci.core {
  requires com.google.common;
  requires jackson.annotations;

  requires com.fasterxml.jackson.databind;

  requires static org.immutables.value;
  requires static org.eclipse.jdt.annotation;

  exports io.zrz.joci.core;
}
