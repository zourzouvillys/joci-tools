package io.zrz.joci.gradle;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.gradle.api.model.ObjectFactory;

public class JociPluginExtension {

  public String message;
  public String baseImage;
  public List<String> jvmArgs = new LinkedList<>();
  public List<String> env = new LinkedList<>();
  public List<String> exposedPorts = new LinkedList<>();
  public String tag;
  public String mainClass;
  public boolean forceSync = false;

  @javax.inject.Inject
  public JociPluginExtension(ObjectFactory objectFactory) {
  }

  public void baseImage(String baseImage) {
    this.baseImage = baseImage;
  }

  public void tag(String tag) {
    this.tag = tag;
  }

  public void mainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  public void jvmArgs(String... args) {
    this.jvmArgs.addAll(Arrays.asList(args));
  }

  public void exposePort(int port) {
    this.exposedPorts.add(port + "/tcp");
  }

  public void env(String env) {
    this.env.add(env);
  }

  public void env(String key, String value) {
    env(key + "=" + value);
  }

}
