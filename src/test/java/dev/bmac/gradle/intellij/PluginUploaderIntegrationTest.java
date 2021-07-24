package dev.bmac.gradle.intellij;

import com.google.common.collect.Lists;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Request;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gradle.internal.impldep.com.amazonaws.util.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration test to ensure the task gets registered and parameters are passed into the Plugin uploader
 */
public class PluginUploaderIntegrationTest {

    private HttpServer httpServer;
    private Handler handler;
    File testFile;
    File projectDir;
    File buildFile;

    @Before
    public void init() throws Exception {
        testFile = File.createTempFile(getClass().getSimpleName(), ".zip");
        testFile.deleteOnExit();
        projectDir = Files.createTempDirectory("test").toFile();
        buildFile = new File(projectDir, "build.gradle");


        handler = new Handler();
        ServerSocket socket = new ServerSocket(0);
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", socket.getLocalPort()), 0);
        httpServer.createContext("/", handler);
        httpServer.start();
    }

    @After
    public void destroy() throws Exception {
        httpServer.stop(0);
    }

    @Test
    public void testPlugin() throws Exception {
        FileWriter fw = new FileWriter(buildFile);
        fw.write("" +
                "plugins {\n" +
                "  id 'java'\n" +
                "  id 'dev.bmac.intellij.plugin-uploader'\n" +
                "}\n" +
                "uploadPlugin {" +
                "    url.set('http:/" + httpServer.getAddress().toString() + "')\n" +
                "    pluginName.set('testPlugin')\n" +
                "    file.set(file('" + testFile.getPath() + "'))\n" +
                "    pluginId.set('testPlugin')\n" +
                "    version.set('1.0.0')\n" +
                "    pluginDescription.set('description')\n" +
                "    changeNotes.set('changenotes')\n" +
                "    sinceBuild.set('211')\n" +
                "    authentication.set('Basic pass')" +
                "}");
        fw.flush();
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath().forwardOutput().withArguments("--stacktrace", "uploadPlugin").build();

        List<RecordedRequest> requests = handler.requests;
        assertEquals("Basic pass", requests.get(0).auth);

        RecordedRequest updateXml = requests.get(5);
        assertEquals("/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, updateXml.path);
        assertTrue(updateXml.body.contains("changenotes"));
        assertTrue(updateXml.body.contains("description"));
    }

    @Test
    public void testMissingRequiredParameters() throws Exception {
        FileWriter fw = new FileWriter(buildFile);
        fw.write("" +
                "plugins {\n" +
                "  id 'java'\n" +
                "  id 'dev.bmac.intellij.plugin-uploader'\n" +
                "}\n" +
                "uploadPlugin {" +
                //"    url.set('http:/" + httpServer.getAddress().toString() + "')\n" +
                "    pluginName.set('testPlugin')\n" +
                "    file.set(file('" + testFile.getPath() + "'))\n" +
                "    pluginId.set('testPlugin')\n" +
                "    version.set('1.0.0')\n" +
                "}");
        fw.flush();

        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath().forwardOutput().withArguments("uploadPlugin").buildAndFail();
        assertTrue(buildResult.getOutput().contains("Must specify url"));
    }

    public static class Handler implements HttpHandler {

        private String lockId = null;
        private List<PluginUploaderIntegrationTest.RecordedRequest> requests = Lists.newLinkedList();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            PluginUploaderIntegrationTest.RecordedRequest request = new PluginUploaderIntegrationTest.RecordedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    IOUtils.toString(exchange.getRequestBody()));
            requests.add(request);
            if (exchange.getRequestURI().getPath()
                    .endsWith(UploadPluginTask.UPDATE_PLUGINS_FILENAME + PluginUploader.LOCK_FILE_EXTENSION)) {
                if (exchange.getRequestMethod().equalsIgnoreCase("post")) {
                    lockId = request.body;
                    exchange.sendResponseHeaders(201, 0);
                } else if (exchange.getRequestMethod().equalsIgnoreCase("get")) {
                    if (lockId != null) {
                        exchange.sendResponseHeaders(200, lockId.length());
                        OutputStreamWriter osw = new OutputStreamWriter(exchange.getResponseBody());
                        osw.write(lockId);
                        osw.flush();
                        osw.close();

                    } else {
                        exchange.sendResponseHeaders(404, 0);

                    }
                } else if (exchange.getRequestMethod().equalsIgnoreCase("delete")) {
                    lockId = null;
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            } else if (exchange.getRequestURI().getPath().endsWith(".zip")) {
                exchange.sendResponseHeaders(201, 0);
            } else if (exchange.getRequestURI().getPath().endsWith(UploadPluginTask.UPDATE_PLUGINS_FILENAME)) {
                if (exchange.getRequestMethod().equalsIgnoreCase("get")) {
                    exchange.sendResponseHeaders(404, 0);
                } else if ((exchange.getRequestMethod().equalsIgnoreCase("post"))) {
                    exchange.sendResponseHeaders(201, 0);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
        }
    }

    private static class RecordedRequest {
        String path;
        String auth;
        String body;

        public RecordedRequest(String path, String auth, String body) {
            this.path = path;
            this.auth = auth;
            this.body = body;
        }
    }
}
