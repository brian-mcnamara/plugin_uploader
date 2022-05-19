package dev.bmac.gradle.intellij;

import com.adobe.testing.s3mock.junit4.S3MockRule;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class S3PublishPluginTest extends BasePluginUploaderTest {

    @ClassRule
    public static final S3MockRule S3_MOCK_RULE = S3MockRule.builder().withSecureConnection(false).silent().build();
    private static final String BUCKET_NAME = "test-bucket";
    private final AmazonS3 client = S3_MOCK_RULE.createS3Client();

    public S3PublishPluginTest() throws Exception {
        super();
    }

    @Before
    public void setup() throws Exception {

        client.createBucket(BUCKET_NAME);
        String endpoint = S3_MOCK_RULE.getServiceEndpoint();
        URIBuilder uriBuilder = new URIBuilder(endpoint);
        uriBuilder.setUserInfo(BUCKET_NAME)
                  .setPath("/plugins");
        builder = new PluginUploaderBuilder(uriBuilder.toString(), PLUGIN_NAME, testFile, PLUGIN_ID, VERSION, logger);
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

        builder.setRepoType(PluginUploader.RepoType.S3).build(LOCK_ID).execute();

        S3Object updateObject = client.getObject(BUCKET_NAME, "plugins/updatePlugins.xml");
        String updatePlugin = IOUtils.toString(updateObject.getObjectContent(), StandardCharsets.UTF_8);
        assertEquals(updatePluginExpected, updatePlugin.substring(updatePlugin.indexOf('\n') + 1));

        assertTrue(client.doesObjectExist(BUCKET_NAME, "plugins/" + PLUGIN_NAME + "/" + testFile.getName()));

        assertFalse(client.doesObjectExist(BUCKET_NAME, "plugins/updatePlugins.xml.lock"));
    }
}
