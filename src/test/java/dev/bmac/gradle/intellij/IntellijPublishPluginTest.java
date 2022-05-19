package dev.bmac.gradle.intellij;

import com.google.common.io.Resources;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gradle.api.GradleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * Created by brian.mcnamara on Jan 30 2020
 **/
public class IntellijPublishPluginTest extends BasePluginUploaderTest {
    private MockWebServer webServer;

    public IntellijPublishPluginTest() throws Exception {
        super();
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        webServer = new MockWebServer();
        webServer.start();
        builder = new PluginUploaderBuilder(webServer.url("/").toString(), PLUGIN_NAME, testFile, blockmapFile,
                hashFile, PLUGIN_ID, VERSION, logger);
    }

    @After
    public void teardown() throws Exception {
        webServer.shutdown();
    }

    @Test
    public void testPluginEndToEnd() throws Exception {
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, PLUGIN_NAME, null, null, testFile, ".");
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
        assertNull(request.getHeader("authorization"));

        request = webServer.takeRequest();
        assertEquals("/" + LOCK_FILE, request.getPath());
        assertEquals(LOCK_ID, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertNull(request.getHeader("authorization"));


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
        assertEquals("/" + PLUGIN_NAME + "/" + blockmapFile.getName(), request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));

        request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + hashFile.getName(), request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("application/json", request.getHeader("Content-Type"));

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
        assertNull(request.getHeader("authorization"));
    }

    @Test
    public void testPluginEndToEndWithAuth() throws Exception {
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, PLUGIN_NAME, null, null, testFile, ".");
        PluginsElement updates = new PluginsElement();
        updates.getPlugins().add(pluginInstance);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        marshaller.marshal(updates, writer);

        enqueueResponses();

        final String authValue = "basic test:pass";
        builder.setAuthentication(authValue);
        builder.build(LOCK_ID).execute();

        //get lock
        RecordedRequest request = webServer.takeRequest();
        assertEquals(authValue, request.getHeader("authorization"));

        //post lock
        request = webServer.takeRequest();
        assertEquals(authValue, request.getHeader("authorization"));

        webServer.takeRequest(); //get lock
        webServer.takeRequest(); //get update file
        webServer.takeRequest(); //post plugin
        webServer.takeRequest(); //post blockmap
        webServer.takeRequest(); //post hash
        webServer.takeRequest(); //post update file
        webServer.takeRequest(); //get lock

        //delete
        request = webServer.takeRequest();
        assertEquals(authValue, request.getHeader("authorization"));
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
                null, null, testFile, ".");

        assertEquals("./plugin%20with%20space/" + testFile.getName(), pluginInstance.getUrl());
    }

    @Test
    public void testPluginUpdateAbsolute() {
        builder.setPluginName("plugin with space");
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, builder.getPluginName(),
                null, null, testFile, "https://repo.example.com/foo%20bar");

        // Confirm the base URL is not re-escaped
        assertEquals("https://repo.example.com/foo%20bar/plugin%20with%20space/" + testFile.getName(), pluginInstance.getUrl());
    }

    @Test
    public void testOnlyPluginUploadIfWriteToUpdateXmlFalse() throws Exception {
        builder.setUpdatePluginXml(false);
        //check update xml
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Upload blockmap
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Upload hash
        webServer.enqueue(new MockResponse().setResponseCode(201));

        builder.build(LOCK_ID).execute();

        assertEquals(4, webServer.getRequestCount());
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

        webServer.takeRequest(); //Get lock
        webServer.takeRequest(); //Set lock
        webServer.takeRequest(); //Get lock
        //Get update xml
        RecordedRequest recordedRequest = webServer.takeRequest();

        assertEquals("/" + newUpdateFile, recordedRequest.getPath());
        assertEquals("GET", recordedRequest.getMethod());

        //Post Plugin
        webServer.takeRequest();
        //Post blockmap
        webServer.takeRequest();
        //Post hash
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

        //Upload blockmap
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + PLUGIN_NAME + "/" + blockmapFile.getName(), recordedRequest.getPath());

        //Upload hash
        recordedRequest = webServer.takeRequest();
        assertEquals("/somePath/" + PLUGIN_NAME + "/" + hashFile.getName(), recordedRequest.getPath());

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

        webServer.takeRequest(); //get lock
        webServer.takeRequest(); //post lock
        webServer.takeRequest(); //get lock
        webServer.takeRequest(); //get update file
        webServer.takeRequest(); //post plugin
        webServer.takeRequest(); //post blockmap
        webServer.takeRequest(); //post hash

        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFile.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName()).replace("\r\n", "\n");
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

        webServer.takeRequest(); //get lock
        webServer.takeRequest(); //post lock
        webServer.takeRequest(); //get lock
        webServer.takeRequest(); //get update file
        webServer.takeRequest(); //post plugin
        webServer.takeRequest(); //post blockmap
        webServer.takeRequest(); //post hash

        RecordedRequest recordedRequest = webServer.takeRequest();
        String updatePlugin = recordedRequest.getBody().readString(Charset.defaultCharset());
        String expectedFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.expected"), Charset.defaultCharset())
                .replace("{filename}", testFile.getName()).replace("\r\n", "\n");
        assertEquals(expectedFile, updatePlugin.substring(updatePlugin.indexOf('\n') + 1));
    }

    @Test
    public void testUploadWithPutMethod() throws Exception {
        builder.setRepoType(PluginUploader.RepoType.REST_PUT);

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
        //Put blockmap
        recordedRequest = webServer.takeRequest();
        assertEquals("PUT", recordedRequest.getMethod());
        //Put hash
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
        System.setProperty(PluginUploader.RELEASE_CHECK_PROPERTY, "true");

        try {
            String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

            enqueueResponses(originalFile);

            builder.build(LOCK_ID).execute();

            assertEquals(10, webServer.getRequestCount());
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
            //Put blockmap
            recordedRequest = webServer.takeRequest();
            assertEquals("POST", recordedRequest.getMethod());
            //Put hash
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
        } finally {
            System.clearProperty(PluginUploader.RELEASE_CHECK_PROPERTY);
        }
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
        System.setProperty(PluginUploader.RELEASE_CHECK_PROPERTY, "true");

        try {
            String originalFile = Resources.toString(Resources.getResource("testUpdateXmlFileWithOldVersion.existing"), Charset.defaultCharset());

            enqueueResponses(originalFile);

            builder.build(LOCK_ID).execute();

            assertEquals(4, webServer.getRequestCount());
            //check update xml
            RecordedRequest recordedRequest = webServer.takeRequest();
            assertEquals("GET", recordedRequest.getMethod());
            //Put file
            recordedRequest = webServer.takeRequest();
            assertEquals("POST", recordedRequest.getMethod());
            //Put blockmap
            recordedRequest = webServer.takeRequest();
            assertEquals("POST", recordedRequest.getMethod());
            //Put hash
            recordedRequest = webServer.takeRequest();
            assertEquals("POST", recordedRequest.getMethod());
        } finally {
            System.clearProperty(PluginUploader.RELEASE_CHECK_PROPERTY);
        }
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
        //Upload blockmap
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Upload hash
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Post updatePlugin
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Delete lock
        webServer.enqueue(new MockResponse().setResponseCode(204));
    }
}
