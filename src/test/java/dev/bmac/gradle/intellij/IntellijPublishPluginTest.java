package dev.bmac.gradle.intellij;

import com.google.common.io.Resources;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
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
    private static final String LOCK_FILE = UploadPluginTask.UPDATE_PLUGINS_FILENAME + PluginUploader.LOCK_FILE_EXTENSION;

    private MockWebServer webServer;
    private PluginUploaderBuilder builder;
    private final Marshaller marshaller;
    private final File testFile;
    private final Logger logger;

    public IntellijPublishPluginTest() throws Exception {
        logger = Logging.getLogger(IntellijPublishPluginTest.class);

        JAXBContext contextObj = JAXBContext.newInstance(PluginsElement.class);

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
        builder = new PluginUploaderBuilder(webServer.url("/").toString(), PLUGIN_NAME, testFile, PLUGIN_ID, VERSION, logger);
    }

    @After
    public void teardown() throws Exception {
        webServer.shutdown();
    }

    @Test
    public void testPluginEndToEnd() throws Exception {
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, PLUGIN_NAME, null, null, testFile);
        PluginsElement updates = new PluginsElement();
        updates.getPlugins().add(pluginInstance);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        marshaller.marshal(updates, writer);
        String updatePluginExpected = baos.toString();

        enqueueResponses();

        builder.build(LOCK_ID).execute();

        RecordedRequest request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals(LOCK_ID, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + testFile.getName(), request.getPath());
        assertEquals(FILE_CONTENTS, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));

        request = webServer.takeRequest();
        assertEquals("/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, request.getPath());
        String updatePlugin = request.getBody().readUtf8();
        String comment = updatePlugin.substring(0, updatePlugin.indexOf('\n'));
        assertFalse(comment.contains(PluginUploader.UNKNOWN_VERSION));
        assertTrue(comment.contains(PLUGIN_ID));
        assertEquals(updatePluginExpected, updatePlugin.substring(updatePlugin.indexOf('\n') + 1));
        assertEquals("POST", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals("DELETE", request.getMethod());
    }

    @Test
    public void testPluginLockExists() throws Exception {
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Check for lock second retry
        webServer.enqueue(new MockResponse().setResponseCode(201));

        try {
            builder.build(LOCK_ID).execute();
            fail("Expected the plugin to fail because lock already exists");
        } catch (GradleException e) {
            assertTrue(e.getCause().getMessage().contains("lock"));
        }
    }

    @Test
    public void testPluginLockChanged() throws Exception {
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set the lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return changed lock
        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("DifferentLock"));
        //Check for lock second retry
        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("DifferentLock"));

        try {
            builder.build(LOCK_ID).execute();
            fail("Expected the plugin to fail because lock already exists");
        } catch (GradleException e) {
            assertTrue(e.getCause().getMessage().contains("claimed the lock"));
        }
    }

    @Test
    public void testPluginUpdateEncoding() {
        builder.setPluginName("plugin with space");
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, builder.getPluginName(),
                null, null, testFile);

        assertEquals("./plugin%20with%20space/" + testFile.getName(), pluginInstance.getUrl());
    }

    @Test
    public void testOnlyPluginUploadIfWriteToUpdateXmlFalse() throws Exception {
        builder.setUpdatePluginXml(false);
        //check update xml
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));

        builder.build(LOCK_ID).execute();

        assertEquals(2, webServer.getRequestCount());
        RecordedRequest request = webServer.takeRequest();
        assertEquals("/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + testFile.getName(), request.getPath());
        assertEquals(FILE_CONTENTS, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));
    }

    @Test
    public void testDifferentUpdatePluginName() throws Exception {
        String newUpdateFile = "differentFile.xml";
        builder.setUpdateFile(newUpdateFile);

        enqueueResponses();

        builder.build(LOCK_ID).execute();

        //Get lock
        webServer.takeRequest();
        //Set lock
        webServer.takeRequest();
        //Get lock
        webServer.takeRequest();
        //Get update xml
        RecordedRequest recordedRequest = webServer.takeRequest();

        assertEquals("/" + newUpdateFile, recordedRequest.getPath());
        assertEquals("GET", recordedRequest.getMethod());

        //Post Plugin
        webServer.takeRequest();

        recordedRequest = webServer.takeRequest();
        assertEquals("/" + newUpdateFile, recordedRequest.getPath());
        assertEquals("POST", recordedRequest.getMethod());
    }

    @Test
    public void testHostWithoutTrailingSlash() throws Exception {
        String host = builder.getUrl() + "somePath";
        builder.setUrl(host);

        enqueueResponses();

        builder.build(LOCK_ID).execute();

        //Get lock
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + LOCK_FILE, recordedRequest.getPath());

        //Set lock
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + LOCK_FILE, recordedRequest.getPath());

        //Get lock
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + LOCK_FILE, recordedRequest.getPath());

        //Get update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, recordedRequest.getPath());

        //Upload plugin
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + PLUGIN_NAME + "/" + testFile.getName(), recordedRequest.getPath());

        //Update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + UploadPluginTask.UPDATE_PLUGINS_FILENAME, recordedRequest.getPath());

        //Get lock
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + LOCK_FILE, recordedRequest.getPath());

        //Delete lock
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + LOCK_FILE, recordedRequest.getPath());
    }

    @Test
    public void testUpdateXmlFile() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");

        enqueueResponses();

        builder.build(LOCK_ID).execute();

        //check lock
        webServer.takeRequest();
        //set lock
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //check update xml
        webServer.takeRequest();
        //Post file
        webServer.takeRequest();


        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFile.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName());
        assertEquals(expectedFile, updatePlugin.substring(updatePlugin.indexOf('\n') + 1));
    }

    @Test
    public void testUpdateXmlFileWithOldVersion() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        enqueueResponses(originalFile);

        builder.build(LOCK_ID).execute();

        //check lock
        webServer.takeRequest();
        //set lock
        webServer.takeRequest();
        //check lock
        webServer.takeRequest();
        //check update xml
        webServer.takeRequest();
        //Post file
        webServer.takeRequest();

        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName());
        assertEquals(expectedFile, updatePlugin.substring(updatePlugin.indexOf('\n') + 1));
    }

    @Test
    public void testUploadWithPutMethod() throws Exception {
        builder.setUploadMethod(PluginUploader.UploadMethod.PUT);

        enqueueResponses();

        builder.build(LOCK_ID).execute();

        //check lock
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //set lock
        recordedRequest = webServer.takeRequest();
        assertEquals("PUT", recordedRequest.getMethod());
        //check lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //check update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Put file
        recordedRequest = webServer.takeRequest();
        assertEquals("PUT", recordedRequest.getMethod());
        //Put update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("PUT", recordedRequest.getMethod());
        //get lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Delete lock
        recordedRequest = webServer.takeRequest();
        assertEquals("DELETE", recordedRequest.getMethod());
    }

    @Test
    public void testOverwritePluginFailsBuild() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");
        builder.setVersion("0.1");

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Get updatePlugin.xml
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(originalFile));
        //Get lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Delete lock
        webServer.enqueue(new MockResponse().setResponseCode(204));

        try {
            builder.build(LOCK_ID).execute();
            fail("Should have thrown exception");
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("already published to repository"));
        }

        assertEquals(6, webServer.getRequestCount());
        //check lock
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //set lock
        recordedRequest = webServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        //check lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //check update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //check lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Delete lock
        recordedRequest = webServer.takeRequest();
        assertEquals("DELETE", recordedRequest.getMethod());
    }

    @Test
    public void testOverwritePluginPassesWithAllowOverwrite() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");
        builder.setVersion("0.1");
        builder.setAllowOverwrite(true);

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        enqueueResponses(originalFile);

        builder.build(LOCK_ID).execute();

        assertEquals(8, webServer.getRequestCount());
        //check lock
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //set lock
        recordedRequest = webServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        //check lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //check update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Put file
        recordedRequest = webServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        //Put update xml
        recordedRequest = webServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        //get lock
        recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Delete lock
        recordedRequest = webServer.takeRequest();
        assertEquals("DELETE", recordedRequest.getMethod());
    }

    @Test
    public void testOverwritePluginWithUpdateXmlToFalseFailsBuild() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");
        builder.setVersion("0.1");
        builder.setUpdatePluginXml(false);

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        //Get updatePlugin.xml
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(originalFile));

        try {
            builder.build(LOCK_ID).execute();
            fail("Should have thrown exception");
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("already published to repository"));
        }

        assertEquals(1, webServer.getRequestCount());
        //check update xml
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
    }

    @Test
    public void testOverwritePluginWithUpdateXmlToFalsePassesWithAllowOverwrite() throws Exception {
        builder.setDescription("testDescription");
        builder.setChangeNotes("testChangeNotes");
        builder.setUntilBuild("1.1");
        builder.setSinceBuild("1.0");
        builder.setVersion("0.1");
        builder.setUpdatePluginXml(false);
        builder.setAllowOverwrite(true);

        String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

        enqueueResponses(originalFile);

        builder.build(LOCK_ID).execute();

        assertEquals(2, webServer.getRequestCount());
        //check update xml
        RecordedRequest recordedRequest = webServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        //Put file
        recordedRequest = webServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
    }

    private void enqueueResponses() {
        enqueueResponses(null);
    }

    private void enqueueResponses(String updateXml) {
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
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Post updatePlugin
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Delete lock
        webServer.enqueue(new MockResponse().setResponseCode(204));
    }
}
