package dev.bmac.gradle.intellij;

import com.adobe.testing.s3mock.junit4.S3MockRule;
import com.amazonaws.services.s3.AmazonS3;
import dev.bmac.gradle.intellij.repo.S3Repo;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import okhttp3.mockwebserver.MockWebServer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import software.amazon.awssdk.services.s3.S3Client;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;

public class S3PublishPluginTest {

    @ClassRule
    public static final S3MockRule S3_MOCK_RULE = S3MockRule.builder().silent().build();

    private static final String FILE_CONTENTS = "testFileContents";
    private static final String BUCKET_NAME = "test-bucket";
    private static final String LOCK_ID = "testLock";
    private static final String PLUGIN_ID = "pluginId";
    private static final String PLUGIN_NAME = "MyPlugin";
    private static final String VERSION = "1.0";

    private PluginUploaderBuilder builder;
    private final Marshaller marshaller;
    private final File testFile;
    private final AmazonS3 client = S3_MOCK_RULE.createS3Client();

    private final Logger logger;

    public S3PublishPluginTest() throws Exception {
        logger = Logging.getLogger(S3PublishPluginTest.class);
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

        client.createBucket(BUCKET_NAME);
        String endpoint = S3_MOCK_RULE.getServiceEndpoint();
        builder = new PluginUploaderBuilder(endpoint + "/plugins", PLUGIN_NAME, testFile, PLUGIN_ID, VERSION, logger);
    }

    @Test
    public void testPluginUploadEndToEnd() throws Exception {
        PluginElement pluginInstance = new PluginElement(PLUGIN_ID, VERSION,
                null, null, PLUGIN_NAME, null, null, testFile, ".");
        PluginsElement updates = new PluginsElement();
        updates.getPlugins().add(pluginInstance);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        marshaller.marshal(updates, writer);
        String updatePluginExpected = baos.toString();

        builder.setRepoType(new S3Repo("https://localhost/plugins", "todo", BUCKET_NAME, "us-west-2", client));
        builder.build(LOCK_ID).execute();

        client.getObject(BUCKET_NAME, "updatePlugins.xml");
    }
}
