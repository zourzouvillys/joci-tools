package io.zrz.joci.registry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import io.zrz.joci.core.FilesystemRegistry;

/**
 * example launcher for registry, using local path for images.
 * 
 * @author theo
 *
 */
public class Main {

  public static void main(final String[] args) {

    if (args.length != 2) {
      System.err.println("usage: main /path/to/images http://{ip}:{port}");
      System.exit(255);
    }

    Path path = Paths.get(args[0]);

    if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isWritable(path)) {
      throw new IllegalArgumentException("invalid path " + path.toString());
    }

    final URI baseUri = URI.create(args[1]);

    FilesystemRegistry registry = new FilesystemRegistry(path);

    try {

      ///
      final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, create(registry), false);

      server.getListeners().iterator().next().registerAddOn(new FixupBrokenContentTypeHeaderAddOn());

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          server.shutdownNow();
        }
      }));
      server.start();

      Thread.currentThread().join();
    }
    catch (IOException | InterruptedException ex) {
      ex.printStackTrace();
    }

  }

  public static ResourceConfig create(FilesystemRegistry registry) {
    final ResourceConfig resourceConfig = new ResourceConfig()
        .register(RangeHeaderConverter.class)
        .registerInstances(new DockerRegistry(registry));

    return resourceConfig;
  }
}
