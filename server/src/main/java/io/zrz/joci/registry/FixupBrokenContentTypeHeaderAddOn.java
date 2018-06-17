package io.zrz.joci.registry;

import java.io.IOException;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;

/**
 * fixup broken (empty) content-type header docker client sends.
 *
 * @author theo
 *
 */
public class FixupBrokenContentTypeHeaderAddOn implements AddOn {

  @Override
  public void setup(final NetworkListener networkListener, final FilterChainBuilder builder) {

    // Get the index of HttpServerFilter in the HttpServer filter chain
    final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

    if (httpServerFilterIdx >= 0) {
      builder.add(httpServerFilterIdx, new FixupBrokenContentTypeHeader());
    }

  }

  private static class FixupBrokenContentTypeHeader extends BaseFilter {

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {

      final Object message = ctx.getMessage();

      if (message instanceof HttpContent) {

        final HttpContent content = (HttpContent) message;

        if (content.getHttpHeader() != null) {

          final String ct = content.getHttpHeader().getHeader("content-type");

          if (ct != null && ct.trim().isEmpty()) {
            final HttpHeader hdrs = content.getHttpHeader();
            hdrs.getHeaders().removeHeader("content-type");
          }

        }

      }

      return ctx.getInvokeAction();

    }

  }

}
