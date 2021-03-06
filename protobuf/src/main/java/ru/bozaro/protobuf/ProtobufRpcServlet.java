/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bozaro.protobuf.internal.MethodInfo;
import ru.bozaro.protobuf.internal.ServiceInfo;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;

/**
 * Servlet wrapper for Protobuf RPC
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufRpcServlet extends HttpServlet {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ProtobufRpcServlet.class);
  @NotNull
  private final ServiceHolder holder;

  public ProtobufRpcServlet(@NotNull ServiceHolder holder) {
    this.holder = holder;
  }

  @Override
  protected void service(@NotNull HttpServletRequest req, @NotNull final HttpServletResponse res) throws ServletException, IOException {
    final String pathInfo = req.getPathInfo();
    if (pathInfo != null) {
      final int begin = pathInfo.charAt(0) == '/' ? 1 : 0;
      final int separator = pathInfo.lastIndexOf('/');
      if (separator > 0) {
        ServiceInfo serviceInfo = holder.getService(pathInfo.substring(begin, separator));
        if (serviceInfo != null) {
          service(req, res, pathInfo.substring(separator + 1), serviceInfo);
          return;
        }
      }
    }
    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Service not found: " + pathInfo);
  }

  private void service(@NotNull HttpServletRequest req, @NotNull final HttpServletResponse res, @NotNull final String methodPath, @NotNull ServiceInfo serviceInfo) throws ServletException, IOException {
    final MethodInfo method = serviceInfo.getMethod(methodPath);
    if (method == null) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND, "Method not found: " + methodPath);
      return;
    }
    final Message.Builder msgRequest = method.requestBuilder();
    if (req.getMethod().equalsIgnoreCase("POST")) {
      method.requestByStream(msgRequest, req.getInputStream(), getCharset(req));
    }
    try {
      method.requestByParams(msgRequest, req.getParameterMap());
    } catch (ParseException e) {
      throw new ServletException(e);
    }
    try {
      final byte[] response = method.call(msgRequest.build(), StandardCharsets.UTF_8).get();
      try {
        if (response != null) {
          res.setContentType(method.getFormat().getMimeType());
          res.setContentLength(response.length);
          res.setCharacterEncoding(StandardCharsets.UTF_8.name());
          res.getOutputStream().write(response);
        } else {
          res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      } catch (IOException e) {
        log.error("Method error " + method.getName(), e);
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("Method error " + method.getName(), e);
    }
  }

  @NotNull
  private static Charset getCharset(@NotNull HttpServletRequest request) {
    final String charset = request.getCharacterEncoding();
    return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
  }
}
