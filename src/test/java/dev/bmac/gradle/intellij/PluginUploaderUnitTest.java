package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.repos.MockRepo;
import dev.bmac.gradle.intellij.repos.Repo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PluginUploaderUnitTest extends BasePluginUploaderTest {

    private MockRepo mockRepo;

    public PluginUploaderUnitTest() throws Exception {
        super();
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        mockRepo = mock(MockRepo.class);
        builder = new PluginUploaderBuilder("https://repo.example.com/intellij", PLUGIN_NAME, testFile,
                blockmapFile, hashFile, PLUGIN_ID, VERSION, logger);
        builder.setRepo(mockRepo);
    }

    @Test
    public void testUploadEndToEnd() throws Exception {
        when(mockRepo.get(eq(LOCK_FILE), any()))
            .then(invocation -> {
                Function f = invocation.getArgument(1, Function.class);
                return f.apply(Repo.RepoObject.empty());
            })
            .then(invocation -> {
                Function f = invocation.getArgument(1, Function.class);
                return f.apply(Repo.RepoObject.of(new ByteArrayInputStream(LOCK_ID.getBytes(StandardCharsets.UTF_8))));
            });
        when(mockRepo.get(eq(UploadPluginTask.UPDATE_PLUGINS_FILENAME), any())).then(invocation -> {
            Function f = invocation.getArgument(1, Function.class);
            return f.apply(Repo.RepoObject.empty());
        });

        builder.build(LOCK_ID).execute();

        InOrder inOrder = inOrder(mockRepo);

        inOrder.verify(mockRepo).get(eq(LOCK_FILE), any());
        inOrder.verify(mockRepo).upload(eq(LOCK_FILE), any(), eq("text/plain"));
        inOrder.verify(mockRepo).get(eq(LOCK_FILE), any());
        inOrder.verify(mockRepo).get(eq(UploadPluginTask.UPDATE_PLUGINS_FILENAME), any());
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + testFile.getName()), eq(testFile), eq("application/zip"));
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + blockmapFile.getName()), eq(blockmapFile), eq("application/zip"));
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + hashFile.getName()), eq(hashFile), eq("application/json"));
        inOrder.verify(mockRepo).upload(eq(UploadPluginTask.UPDATE_PLUGINS_FILENAME), any(), eq("application/xml"));
        inOrder.verify(mockRepo).get(eq(LOCK_FILE), any());
        inOrder.verify(mockRepo).delete(eq(LOCK_FILE));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testUploadWithoutChangesToUpdateFile() throws Exception {
        when(mockRepo.get(eq(UploadPluginTask.UPDATE_PLUGINS_FILENAME), any())).then(invocation -> {
            Function f = invocation.getArgument(1, Function.class);
            return f.apply(Repo.RepoObject.empty());
        });

        builder.setUpdatePluginXml(false).build(LOCK_ID).execute();

        InOrder inOrder = inOrder(mockRepo);

        inOrder.verify(mockRepo).get(eq(UploadPluginTask.UPDATE_PLUGINS_FILENAME), any());
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + testFile.getName()), eq(testFile), eq("application/zip"));
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + blockmapFile.getName()), eq(blockmapFile), eq("application/zip"));
        inOrder.verify(mockRepo).upload(eq(PLUGIN_NAME + "/" + hashFile.getName()), eq(hashFile), eq("application/json"));
        inOrder.verifyNoMoreInteractions();
    }

}
