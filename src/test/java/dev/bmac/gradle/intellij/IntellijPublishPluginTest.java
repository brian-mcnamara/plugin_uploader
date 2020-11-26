package dev.bmac.gradle.intellij;

import com.google.common.io.Resources;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * Created by brian.mcnamara on Jan 30 2020
 **/
public class IntellijPublishPluginTest {
    private static final String LOCK_ID = "testLock";
    private static final String FILE_CONTENTS = "testFileContents";
    private static final String PLUGIN_ID = "pluginId";
    private static final String PLUGIN_NAME = "MyPlugin";
    private static final String VERSION = "1.0";

    private MockWebServer webServer;
    private IntellijPublishPlugin plugin;
    private UploadPluginExtension extension;
    private Marshaller marshaller;
    private final File testFile;
    private final Logger logger;

    public IntellijPublishPluginTest() throws Exception {
        plugin = new IntellijPublishPlugin() {
            @Override
            protected String getLockId() {
                return LOCK_ID;
            }
        };
        logger = Logging.getLogger(IntellijPublishPluginTest.class);
        extension = new UploadPluginExtension();

        JAXBContext contextObj = JAXBContext.newInstance(PluginUpdates.class);

        marshaller = contextObj.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        testFile = File.createTempFile(getClass().getSimpleName(), ".zip");
        testFile.deleteOnExit();

        FileWriter fw = new FileWriter(testFile);
        fw.append(FILE_CONTENTS);
        fw.close();
    }

    @Before
    public void setup() throws Exception {
        webServer = new MockWebServer();
        webServer.start();
        extension.setHost(webServer.url("/").toString());
        extension.setFile(testFile);
        extension.setPluginId(PLUGIN_ID);
        extension.setPluginName(PLUGIN_NAME);
        extension.setVersion(VERSION);
    }

    @After
    public void teardown() throws Exception {
        webServer.shutdown();
    }

    @Test
    public void testPluginEndToEnd() throws Exception {
        PluginUpdates.Plugin pluginInstance = new PluginUpdates.Plugin(extension);
        PluginUpdates updates = new PluginUpdates();
        updates.updateOrAdd(pluginInstance);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        marshaller.marshal(updates, writer);
        String updatePluginExpected = new String(baos.toByteArray());

        enqueueResponses();

        plugin.execute(extension, logger);

        RecordedRequest request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + testFile.getName(), request.getPath());
        assertEquals(FILE_CONTENTS, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));

        String lockFile = UploadPluginExtension.UPDATE_PLUGINS_FILENAME + IntellijPublishPlugin.LOCK_FILE_EXTENSION;
        request = webServer.takeRequest();
        assertEquals("/" + lockFile, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + lockFile, request.getPath());
        assertEquals(LOCK_ID, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + lockFile, request.getPath());
        assertEquals("GET", request.getMethod());


        request = webServer.takeRequest();
        assertEquals("/" + UploadPluginExtension.UPDATE_PLUGINS_FILENAME, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + UploadPluginExtension.UPDATE_PLUGINS_FILENAME, request.getPath());
        assertEquals(updatePluginExpected, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());


        request = webServer.takeRequest();
        assertEquals("/" + lockFile, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + lockFile, request.getPath());
        assertEquals("DELETE", request.getMethod());
    }

    @Test
    public void testPluginLockExists() {
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(201));

        try {
            plugin.execute(extension, logger);
            fail("Expected the plugin to fail because lock already exists");
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("lock"));
        }
    }

    @Test
    public void testPluginLockChanged() {
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set the lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return changed lock
        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("DifferentLock"));

        try {
            plugin.execute(extension, logger);
            fail("Expected the plugin to fail because lock already exists");
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("claimed the lock"));
        }
    }

    @Test
    public void testPluginUpdateEncoding() {
        extension.setPluginName("plugin with space");
        PluginUpdates.Plugin plugin = new PluginUpdates.Plugin(extension);

        assertEquals("./plugin%20with%20space/" + testFile.getName(), plugin.getUrl());
    }

    @Test
    public void testOnlyPluginUploadIfWriteToUpdateXmlFalse() throws Exception {
        extension.setWriteToUpdateXml(false);
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));

        plugin.execute(extension, logger);

        assertEquals(1, webServer.getRequestCount());
        RecordedRequest request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + testFile.getName(), request.getPath());
        assertEquals(FILE_CONTENTS, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));
    }

    @Test
    public void testDifferentUpdatePluginName() throws Exception {
        String newUpdateFile = "differentFile.xml";
        extension.setUpdateFile(newUpdateFile);

        enqueueResponses();

        plugin.execute(extension, logger);

        webServer.takeRequest();
        webServer.takeRequest();
        webServer.takeRequest();
        webServer.takeRequest();
        RecordedRequest recordedRequest = webServer.takeRequest();

        assertEquals("/" + newUpdateFile, recordedRequest.getPath());
        assertEquals("GET", recordedRequest.getMethod());

        recordedRequest = webServer.takeRequest();
        assertEquals("/" + newUpdateFile, recordedRequest.getPath());
        assertEquals("POST", recordedRequest.getMethod());
    }

    @Test
    public void testHostWithoutTrailingSlash() throws Exception {
        String host = extension.getHost() + "somePath";
        extension.setHost(host);

        enqueueResponses();

        plugin.execute(extension, logger);

        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + PLUGIN_NAME + "/" + testFile.getName(), recordedRequest.getPath());

        String lockFile = UploadPluginExtension.UPDATE_PLUGINS_FILENAME + IntellijPublishPlugin.LOCK_FILE_EXTENSION;
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + lockFile, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + lockFile, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + lockFile, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + UploadPluginExtension.UPDATE_PLUGINS_FILENAME, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + UploadPluginExtension.UPDATE_PLUGINS_FILENAME, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + lockFile, recordedRequest.getPath());

        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + lockFile, recordedRequest.getPath());
    }

    @Test
    public void testUpdateXmlFile() throws Exception {
        extension.setDescription("testDescription");
        extension.setChangeNotes("testChangeNotes");
        extension.setUntilBuild("1.1");
        extension.setSinceBuild("1.0");

        enqueueResponses();

        plugin.execute(extension, logger);

        //Post file
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //set lock
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //check update xml
        webServer.takeRequest();

        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFile.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName());
        assertEquals(expectedFile, updatePlugin);
    }

    @Test
    public void testUpdateXmlFileWithOldVersion() throws Exception {
        extension.setDescription("testDescription");
        extension.setChangeNotes("testChangeNotes");
        extension.setUntilBuild("1.1");
        extension.setSinceBuild("1.0");

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        enqueueResponses(originalFile);

        plugin.execute(extension, logger);

        //Post file
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //set lock
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //check update xml
        webServer.takeRequest();

        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName());
        assertEquals(expectedFile, updatePlugin);
    }

    private void enqueueResponses() {
        enqueueResponses(null);
    }

    private void enqueueResponses(String updateXml) {
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Get updatePlugin.xml
        if (updateXml == null) {
            webServer.enqueue(new MockResponse().setResponseCode(404));
        } else {
            webServer.enqueue(new MockResponse().setResponseCode(200).setBody(updateXml));
        }
        //Post updatePlugin
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Delete lock
        webServer.enqueue(new MockResponse().setResponseCode(204));
    }
}
