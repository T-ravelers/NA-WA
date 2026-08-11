package me.nawa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebConfigMultipartIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void multipartConfig_actualTomcatParsesAllowedFileAndRejectsOversizedFile() throws Exception {
        String previousFileLimit = System.getProperty("settlement.receipt.max-file-size-bytes");
        String previousRequestLimit = System.getProperty("settlement.receipt.max-request-size-bytes");
        System.setProperty("settlement.receipt.max-file-size-bytes", "4");
        System.setProperty("settlement.receipt.max-request-size-bytes", "1024");
        Tomcat tomcat = new Tomcat();
        try {
            tomcat.setBaseDir(tempDir.resolve("tomcat").toString());
            tomcat.setPort(0);
            Context context = tomcat.addContext("", tempDir.toString());
            Wrapper wrapper = Tomcat.addServlet(context, "upload", new MultipartEchoServlet());
            wrapper.setMultipartConfigElement(new WebConfig().multipartConfigElement());
            context.addServletMappingDecoded("/upload", "upload");
            tomcat.getConnector();
            tomcat.start();
            int port = tomcat.getConnector().getLocalPort();

            HttpResponse<String> accepted = upload(port, new byte[] {1, 2, 3});
            HttpResponse<String> rejected = upload(port, new byte[] {1, 2, 3, 4, 5});

            assertEquals(200, accepted.statusCode());
            assertEquals("receipt.jpg:3", accepted.body());
            assertEquals(413, rejected.statusCode());
        } finally {
            try {
                tomcat.stop();
                tomcat.destroy();
            } finally {
                restoreProperty("settlement.receipt.max-file-size-bytes", previousFileLimit);
                restoreProperty("settlement.receipt.max-request-size-bytes", previousRequestLimit);
            }
        }
    }

    private HttpResponse<String> upload(int port, byte[] fileBytes) throws Exception {
        String boundary = "nawa-boundary";
        byte[] prefix = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"receipt.jpg\"\r\n"
            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/upload"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static class MultipartEchoServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
            try {
                javax.servlet.http.Part file = request.getPart("file");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(file.getSubmittedFileName() + ":" + file.getSize());
            } catch (IllegalStateException exception) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            }
        }
    }
}
