package io.zrz.joci.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.plugins.JavaPlugin;

public class JociPlugin implements Plugin<Project> {

  JociPluginExtension extension;

  @Override
  public void apply(Project project) {

    project.getPluginManager().apply(JavaPlugin.class);
    project.getPluginManager().apply(DistributionPlugin.class);

    Distribution distribution = ((DistributionContainer) project
        .getExtensions()
        .getByName("distributions"))
            .getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME);
    
    distribution.getContents();

    this.extension = project.getExtensions().create("joci", JociPluginExtension.class, project.getObjects());
    
    JociBuildManifestTask buildManifestTask = project.getTasks().create("jociBuildManifest", JociBuildManifestTask.class, this);
    project.getTasks().create("jociPush", JociPushTask.class, this, buildManifestTask);
    
  }

}
