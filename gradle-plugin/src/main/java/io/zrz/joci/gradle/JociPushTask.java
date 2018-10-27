package io.zrz.joci.gradle;

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
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

import io.zrz.joci.client.SuccessResponse;
import io.zrz.joci.jpx.JpxUpload;

public class JociPushTask extends DefaultTask {

  private JociPlugin plugin;

  @Inject
  public JociPushTask(JociPlugin plugin, JociBuildManifestTask manifestTask) {

    this.dependsOn(manifestTask);

    this.plugin = plugin;

    this.input = manifestTask.getOutputs().getFiles().getSingleFile();

    // we depend on the input manifest.
    this.getInputs().file(this.input);

    // write the image.id here.
    this.outputFile = new File(getProject().getBuildDir(), "image.id");

    this.getOutputs().file(this.outputFile);

    this.getOutputs().upToDateWhen(
        spec -> {
          return !plugin.extension.forceSync;
        });

    // we cache the image for the same input manifest. this allows us to
    // avoid fetching any dependencies or attempting to push unless
    // forced.
    this.getOutputs().cacheIf(e -> !plugin.extension.forceSync);

  }

  @InputFile
  public File input;

  @OutputFile
  public File outputFile;

  @TaskAction
  public void push() {

    // this.getProject().getPluginManager().

    try {

      // def file = getDestination()
      // file.parentFile.mkdirs()
      // file.write 'Hello!'

      String repo = JociGradleUtils.tagToRepo(plugin.extension.tag);
      String registry = JociGradleUtils.tagToRegistry(plugin.extension.tag);
      String tag = JociGradleUtils.tagToTag(plugin.extension.tag);

      // start
      JpxUpload ctx = new JpxUpload("https://" + repo + "/v2");

      ctx.loadManifest(this.input);

      // ctx.mainClass(plugin.extension.mainClass);

      plugin.extension.env.forEach(ctx::env);
      plugin.extension.jvmArgs.forEach(ctx::option);
      plugin.extension.exposedPorts.forEach(ctx::expose);

      // .configurations.runtime.resolvedConfiguration.resolvedArtifacts
      // Set<ResolvedArtifact> artifacts = this.getProject()
      // .getConfigurations()
      // .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
      // .getResolvedConfiguration()
      // .getResolvedArtifacts();
      //
      // this.getProject()
      // .getTasksByName(JavaPlugin.JAR_TASK_NAME, false)
      // .forEach(task -> {
      //
      // task.getOutputs()
      // .getFiles()
      // .forEach(f -> {
      // try {
      // ctx.addToClasspath(f.getCanonicalFile().toPath());
      // }
      // catch (IOException e) {
      // throw new RuntimeException(e);
      // }
      // });
      //
      // });
      //
      // ArrayNode tree = toTree(artifacts);
      //
      // ctx.addToClasspath((ArrayNode) tree);

      this.getLogger()
          .lifecycle("pushing to joci {}", plugin.extension.tag);

      Stopwatch timer = Stopwatch.createStarted();

      //
      SuccessResponse res = ctx.create(registry, tag);

      this.getLogger()
          .quiet("{}/{}:{} {} with {} in {} ({} uploaded)",
              repo, registry,
              tag,
              res.status(),
              res.target(),
              timer.stop(),
              ctx.uploadStats());

      res.previousTarget().ifPresent(previous -> {
        getLogger().info("previous {}", previous);
      });

      Files.write(res.target().getBytes(), this.outputFile);

      this.getLogger()
          .info("sizes: stable={}, changing={}", res.stableSize(), res.changingSize());

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

      // we need to materialize it.
      ComponentArtifactIdentifier x = a.getId();

      if (x instanceof PublishArtifactLocalArtifactMetadata) {
        ComponentArtifactIdentifier palam = x;
        DefaultProjectComponentIdentifier dpci = (DefaultProjectComponentIdentifier) palam.getComponentIdentifier();
        System.err.println(dpci);
        throw new IllegalArgumentException("xxx" + dpci.projectPath() + " / " + dpci.getIdentityPath() + " / " + dpci.getProjectName());
      }

      throw new IllegalArgumentException(x.getClass().toString());
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
