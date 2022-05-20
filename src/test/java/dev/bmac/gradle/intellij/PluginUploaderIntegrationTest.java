package dev.bmac.gradle.intellij;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.jetbrains.plugin.blockmap.core.BlockMap;
import com.jetbrains.plugin.blockmap.core.FileHash;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.gradle.internal.impldep.com.amazonaws.util.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration test to ensure the task gets registered and parameters are passed into the Plugin uploader
 */
public class PluginUploaderIntegrationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServer httpServer;
    private Handler handler;
    File testFile;
    File projectDir;
    File buildFile;

    @Before
    public void init() throws Exception {
        projectDir = temporaryFolder.newFolder();
        testFile = new File(projectDir, "plugin.zip");
        buildFile = new File(projectDir, "build.gradle");

        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write("testContent".getBytes(StandardCharsets.UTF_8));
        }

        handler = new Handler();
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
                "    file.set(file('" + testFile.getPath().replace("\\", "/") + "'))\n" +
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

        File blockmapFile = new File(projectDir, testFile.getName() + GenerateBlockMapTask.BLOCKMAP_FILE_SUFFIX);
        File hashFile = new File(projectDir, testFile.getName() + GenerateBlockMapTask.HASH_FILE_SUFFIX);
        assertTrue(blockmapFile.exists());
        assertTrue(hashFile.exists());

        Gson gson = new Gson();
        BlockMap bm;
        try (ZipFile zf = new ZipFile(blockmapFile)) {
            ZipEntry entry = zf.getEntry("blockmap.json");
            bm = gson.fromJson(new InputStreamReader(zf.getInputStream(entry)), BlockMap.class);
        }

        FileHash fh = gson.fromJson(new FileReader(hashFile), FileHash.class);
        assertEquals(1, bm.getChunks().size());
        assertEquals("0dczqAQXRNbkt7mRtfON9Io3Z6zWdMnfIxySBogBpGA=", bm.getChunks().get(0).getHash());
        assertEquals("0dczqAQXRNbkt7mRtfON9Io3Z6zWdMnfIxySBogBpGA=", fh.getHash());

        List<RecordedRequest> requests = handler.requests;
        assertEquals("Basic pass", requests.get(1).auth);

        RecordedRequest updateXml = requests.get(7);
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
                "    file.set(file('" + testFile.getPath().replace("\\", "/") + "'))\n" +
                "    pluginId.set('testPlugin')\n" +
                "    version.set('1.0.0')\n" +
                "}");
        fw.flush();

        BuildResult buildResult = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath().forwardOutput().withArguments("uploadPlugin").buildAndFail();
        assertTrue(buildResult.getOutput().contains("This property isn't marked as optional and no value has been configured"));
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
            } else if (exchange.getRequestURI().getPath().endsWith(".zip") || exchange.getRequestURI().getPath().endsWith(".json")) {
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
