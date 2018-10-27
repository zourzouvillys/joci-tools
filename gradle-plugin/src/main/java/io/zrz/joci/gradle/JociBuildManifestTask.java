package io.zrz.joci.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Files;

import io.zrz.joci.jpx.JpxUpload;

public class JociBuildManifestTask extends DefaultTask {

  private JociPlugin plugin;

  @Inject
  public JociBuildManifestTask(JociPlugin plugin) {

    this.plugin = plugin;

    this.input = getProject()
        .getTasks()
        .getAt(JavaPlugin.JAR_TASK_NAME)
        .getOutputs()
        .getFiles()
        .plus(getProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));

    this.getInputs().files(this.input);

    this.manifestJson = new File(getProject().getBuildDir(), "joci.manifest");

    // by default we always cache, as it means we can avoid calculating the shasums
    // over all dependencies - which can be slow.
    this.getOutputs().cacheIf(e -> true);

    // the manifest file is the output
    this.getOutputs().file(this.manifestJson);

  }

  @Classpath
  public FileCollection input;

  @OutputFile
  public File manifestJson;

  @TaskAction
  public void generate() {

    try {

      String repo = JociGradleUtils.tagToRepo(plugin.extension.tag);

      // start
      JpxUpload ctx = new JpxUpload("https://" + repo + "/v2");

      ctx.mainClass(plugin.extension.mainClass);

      plugin.extension.env.forEach(ctx::env);
      plugin.extension.jvmArgs.forEach(ctx::option);
      plugin.extension.exposedPorts.forEach(ctx::expose);

      // .configurations.runtime.resolvedConfiguration.resolvedArtifacts
      Set<ResolvedArtifact> artifacts = this.getProject()
          .getConfigurations()
          .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
          .getResolvedConfiguration()
          .getResolvedArtifacts();

      this.getProject()
          .getTasksByName(JavaPlugin.JAR_TASK_NAME, false)
          .forEach(task -> {

            task.getOutputs()
                .getFiles()
                .forEach(f -> {
                  try {
                    ctx.addToClasspath(f.getCanonicalFile().toPath());
                  }
                  catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });

          });

      ArrayNode tree = toTree(artifacts);

      ctx.addToClasspath((ArrayNode) tree);

      Files.write(ctx.toNode().toString().getBytes(UTF_8), manifestJson);

    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }

  }

  private ArrayNode toTree(Set<ResolvedArtifact> artifacts) {
    ArrayNode cp = JsonNodeFactory.instance.arrayNode();
    artifacts.forEach(a -> cp.add(toNode(a)));
    return cp;
  }

  private ObjectNode toNode(ResolvedArtifact a) {

    ObjectNode o = JsonNodeFactory.instance.objectNode();

    o.put("name", a.getName());
    o.put("type", a.getType());
    o.put("extension", a.getExtension());
    o.put("classifier", a.getClassifier());
    o.put("file", a.getFile().toString());

    if (!a.getFile().exists()) {
      throw new IllegalArgumentException("local file " + a.getFile() + " is missing");
    }

    ObjectNode moduleVersion = o.putObject("moduleVersion");
    moduleVersion.putObject("id")
        .put("group", a.getModuleVersion().getId().getGroup())
        .put("version", a.getModuleVersion().getId().getVersion())
        .put("name", a.getModuleVersion().getId().getName());

    // moduleVersion.put("id", a.getModuleVersion().getId());
    ObjectNode id = o.putObject("id");
    id.put("displayName", a.getId().getDisplayName());
    id.put("componentIdentifier", a.getId().getComponentIdentifier().getDisplayName());

    return o;

  }

}
