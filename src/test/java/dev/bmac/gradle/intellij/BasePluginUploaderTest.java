package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.File;
import java.io.FileWriter;

public abstract class BasePluginUploaderTest {

    protected static final String LOCK_FILE = UploadPluginTask.UPDATE_PLUGINS_FILENAME + PluginUploader.LOCK_FILE_EXTENSION;

    protected static final String LOCK_ID = "testLock";
    protected static final String FILE_CONTENTS = "testFileContents";
    protected static final String PLUGIN_ID = "pluginId";
    protected static final String PLUGIN_NAME = "MyPlugin";
    protected static final String VERSION = "1.0";

    protected PluginUploaderBuilder builder;
    protected final Marshaller marshaller;
    protected final File testFile;
    protected final Logger logger;

    BasePluginUploaderTest() throws Exception {
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
}
