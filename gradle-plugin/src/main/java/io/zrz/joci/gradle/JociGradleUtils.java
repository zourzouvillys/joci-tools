package io.zrz.joci.gradle;

public class JociGradleUtils {

  public static String tagToRepo(String tag) {
    int repo = tag.indexOf('/');
    return tag.substring(0, repo);
  }

  public static String tagToRegistry(String tag) {
    int repo = tag.indexOf('/');
    tag = tag.substring(repo + 1);
    int tpos = tag.indexOf(':');
    if (tpos == -1) {
      return tag;
    }
    return tag.substring(0, tpos);
  }

  public static String tagToTag(String tag) {
    int part = tag.indexOf('/');
    tag = tag.substring(part + 1);
    int repo = tag.lastIndexOf(':');
    if (repo == -1) {
      return "latest";
    }
    return tag.substring(repo + 1);
  }

}
