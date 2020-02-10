package dev.bmac.gradle.intellij;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gradle.api.GradleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;

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

    public IntellijPublishPluginTest() throws Exception {
        plugin = new IntellijPublishPlugin() {
            @Override
            protected String getLockId() {
                return LOCK_ID;
            }
        };
        extension = new UploadPluginExtension();

        JAXBContext contextObj = JAXBContext.newInstance(PluginUpdates.class);

        marshaller = contextObj.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        testFile = File.createTempFile(getClass().getName(), ".zip");
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

        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Upload file
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //Get updatePlugin.xml
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Post updatePlugin
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return lock
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody(LOCK_ID));
        //Delete lock
        webServer.enqueue(new MockResponse().setResponseCode(204));

        plugin.execute(extension, null);

        RecordedRequest request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.LOCK_FILE, request.getPath());
        assertEquals(LOCK_ID, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + PLUGIN_NAME + "/" + testFile.getName(), request.getPath());
        assertEquals(FILE_CONTENTS, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());
        assertEquals("application/zip", request.getHeader("Content-Type"));


        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.UPDATE_PLUGIN_EAP, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.UPDATE_PLUGIN_EAP, request.getPath());
        assertEquals(updatePluginExpected, request.getBody().readUtf8());
        assertEquals("POST", request.getMethod());


        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.LOCK_FILE, request.getPath());
        assertEquals("GET", request.getMethod());

        request = webServer.takeRequest();
        assertEquals("/" + IntellijPublishPlugin.LOCK_FILE, request.getPath());
        assertEquals("DELETE", request.getMethod());
    }

    @Test
    public void testPluginLockExists() {
        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(201));

        try {
            plugin.execute(extension, null);
            fail("Expected the plugin to fail because lock already exists");
        } catch (GradleException e) {
            assertTrue(e.getMessage().contains("lock"));
        }
    }

    @Test
    public void testPluginLockChanged() {

        //Check for lock
        webServer.enqueue(new MockResponse().setResponseCode(404));
        //Set the lock
        webServer.enqueue(new MockResponse().setResponseCode(201));
        //return changed lock
        webServer.enqueue(new MockResponse().setResponseCode(201).setBody("DifferentLock"));

        try {
            plugin.execute(extension, null);
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
}
