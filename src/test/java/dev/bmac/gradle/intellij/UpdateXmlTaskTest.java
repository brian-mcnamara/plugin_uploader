package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateXmlTaskTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    File testFile;
    File projectDir;
    File buildFile;

    @Before
    public void init() throws Exception {
        projectDir = temporaryFolder.newFolder();
        testFile = new File(projectDir, "updatePlugins.xml");
        buildFile = new File(projectDir, "build.gradle");
    }

    @Test
    public void testWithNoFile() throws Exception {
        FileWriter fw = new FileWriter(buildFile);
        fw.write("" +
                "plugins {\n" +
                "  id 'java'\n" +
                "  id 'dev.bmac.intellij.plugin-uploader'\n" +
                "}\n" +
                "tasks.named('updatePluginsXml') {" +
                "    updateFile.set(file('updatePlugins.xml'))\n" +
                "    downloadUrl.set('http://example.com')\n" +
                "    pluginName.set('testPlugin')\n" +
                "    pluginId.set('testPlugin')\n" +
                "    version.set('1.0.0')\n" +
                "    pluginDescription.set('description')\n" +
                "    changeNotes.set('changenotes')\n" +
                "    sinceBuild.set('211')\n" +
                "}");
        fw.flush();
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath().forwardOutput()
                .withArguments("--stacktrace", "updatePluginsXml").build();

        assertTrue(testFile.exists());

        PluginsElement pluginsElement = (PluginsElement) PluginUpdatesUtil.UNMARSHALLER.unmarshal(testFile);
        assertEquals(1, pluginsElement.getPlugins().size());
    }

    @Test
    public void testWithExistingFile() throws Exception {
        FileWriter fw = new FileWriter(testFile);

        fw.write("<plugins>\n" +
                "    <plugin id=\"testPlugin\" url=\"http://example.com\" version=\"1.0.0\">\n" +
                "        <change-notes>changenotes</change-notes>\n" +
                "        <description>description</description>\n" +
                "        <name>testPlugin</name>\n" +
                "        <idea-version since-build=\"211.0\"/>\n" +
                "    </plugin>\n" +
                "</plugins>\n");
        fw.flush();
        fw.close();

        fw = new FileWriter(buildFile);
        fw.write("" +
                "plugins {\n" +
                "  id 'java'\n" +
                "  id 'dev.bmac.intellij.plugin-uploader'\n" +
                "}\n" +
                "tasks.named('updatePluginsXml') {" +
                "    updateFile.set(file('updatePlugins.xml'))\n" +
                "    downloadUrl.set('http://example.com')\n" +
                "    pluginName.set('testPlugin')\n" +
                "    pluginId.set('testPlugin')\n" +
                "    version.set('1.0.1')\n" +
                "    pluginDescription.set('description')\n" +
                "    changeNotes.set('changenotes')\n" +
                "    sinceBuild.set('211')\n" +
                "}");
        fw.flush();
        fw.close();
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath().forwardOutput()
                .withArguments("--stacktrace", "updatePluginsXml").build();

        PluginsElement pluginsElement = (PluginsElement) PluginUpdatesUtil.UNMARSHALLER.unmarshal(testFile);
        assertEquals(1, pluginsElement.getPlugins().size());
        assertEquals("1.0.1", pluginsElement.getPlugins().get(0).getVersion());
    }
}
