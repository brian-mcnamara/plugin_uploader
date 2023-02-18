package dev.bmac.gradle.intellij;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileWriter;

public abstract class BasePluginUploaderTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected static final String LOCK_FILE = UploadPluginTask.UPDATE_PLUGINS_FILENAME + PluginUploader.LOCK_FILE_EXTENSION;

    protected static final String LOCK_ID = "testLock";
    protected static final String FILE_CONTENTS = "testFileContents";
    protected static final String PLUGIN_ID = "pluginId";
    protected static final String PLUGIN_NAME = "MyPlugin";
    protected static final String VERSION = "1.0";

    protected PluginUploaderBuilder builder;
    protected final Marshaller marshaller;
    protected File testFile;
    protected File blockmapFile;
    protected File hashFile;
    protected final Logger logger;

    BasePluginUploaderTest() throws Exception {
        logger = Logging.getLogger(IntellijPublishPluginTest.class);
        marshaller = PluginUpdatesUtil.MARSHALLER;
    }

    @Before
    public void setup() throws Exception {
        String filename = PLUGIN_ID + "-" + VERSION + ".zip";
        testFile = temporaryFolder.newFile(filename);

        FileWriter fw = new FileWriter(testFile);
        fw.append(FILE_CONTENTS);
        fw.close();

        blockmapFile = temporaryFolder.newFile(filename + ".blockmap.zip");
        hashFile = temporaryFolder.newFile(filename + ".hash.json");
    }
}
