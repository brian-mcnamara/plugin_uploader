package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.xml.IdeaVersionElement;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.api.logging.Logger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class PluginUpdatesUtilTest {

    private static final String TEST_ID = "dev.bmac.testPlugin";
    private static final String TEST_VERSION = "1.0.0";
    private static final String TEST_IDEA_VERSION = "201.3210.11";

    private Logger logger = mock(Logger.class);

    @Test
    public void testWithNoExistingPlugins() {
        PluginElement plugin = new PluginElement();
        plugin.setId(TEST_ID);
        plugin.setVersion(TEST_VERSION);
        plugin.setVersionInfo(new IdeaVersionElement(TEST_IDEA_VERSION, null));
        PluginsElement pluginsElement = new PluginsElement();
        PluginUpdatesUtil.updateOrAdd(plugin, pluginsElement.getPlugins(), logger);

        assertEquals(1, pluginsElement.getPlugins().size());
        PluginElement stored = pluginsElement.getPlugins().get(0);
        assertEquals(TEST_IDEA_VERSION, stored.getVersionInfo().getSinceBuildString());
        assertNull(stored.getVersionInfo().getUntilBuild());
    }

    @Test
    public void testPluginWithMultipleIdeaVersions() {
        PluginElement plugin = new PluginElement();
        plugin.setId(TEST_ID);
        plugin.setVersion(TEST_VERSION);
        plugin.setVersionInfo(new IdeaVersionElement(TEST_IDEA_VERSION, null));
        PluginsElement pluginsElement = new PluginsElement();
        pluginsElement.getPlugins().add(plugin);


        PluginElement pluginV2 = new PluginElement();
        pluginV2.setId(TEST_ID);
        pluginV2.setVersion("1.0.1");
        pluginV2.setVersionInfo(new IdeaVersionElement("211.3210.12", null));

        PluginUpdatesUtil.updateOrAdd(pluginV2, pluginsElement.getPlugins(), logger);
        assertEquals(2, pluginsElement.getPlugins().size());

        assertEquals("211.3210.11", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginWithIdeaVersionInBetweenExistingVersions() {
        PluginElement plugin = new PluginElement();
        plugin.setId(TEST_ID);
        plugin.setVersion(TEST_VERSION);
        plugin.setVersionInfo(new IdeaVersionElement("201.3210.10", "202.3210.10"));
        PluginsElement pluginsElement = new PluginsElement();
        pluginsElement.getPlugins().add(plugin);

        PluginElement plugin2 = new PluginElement();
        plugin2.setId(TEST_ID);
        plugin2.setVersion("1.1.0");
        plugin2.setVersionInfo(new IdeaVersionElement("202.3210.11", null));
        PluginUpdatesUtil.updateOrAdd(plugin2, pluginsElement.getPlugins(), logger);


        PluginElement plugin3 = new PluginElement();
        plugin3.setId(TEST_ID);
        plugin3.setVersion("1.0.1");
        plugin3.setVersionInfo(new IdeaVersionElement("202.123.1", null));

        PluginUpdatesUtil.updateOrAdd(plugin3, pluginsElement.getPlugins(), logger);
        assertEquals(3, pluginsElement.getPlugins().size());

        assertEquals("202.3210.10", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("202.123.1", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getSinceBuildString());

        assertEquals("202.123.0", getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("201.3210.10", getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getSinceBuildString());

        assertEquals("202.3210.11", getPluginVersion("1.1.0", pluginsElement).getVersionInfo().getSinceBuildString());
        assertNull(getPluginVersion("1.1.0", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginVersionChangeWithSameSinceBuild() {
        PluginElement plugin = new PluginElement();
        plugin.setId(TEST_ID);
        plugin.setVersion(TEST_VERSION);
        plugin.setVersionInfo(new IdeaVersionElement("201.3210.10", "202.3210.10"));
        PluginsElement pluginsElement = new PluginsElement();
        pluginsElement.getPlugins().add(plugin);

        PluginElement plugin2 = new PluginElement();
        plugin2.setId(TEST_ID);
        plugin2.setVersion("1.0.1");
        plugin2.setVersionInfo(new IdeaVersionElement("201.3210.10", "202.3210.10"));

        PluginUpdatesUtil.updateOrAdd(plugin2, pluginsElement.getPlugins(), logger);

        assertEquals(1, pluginsElement.getPlugins().size());

        PluginElement plugin3 = new PluginElement();
        plugin3.setId(TEST_ID);
        plugin3.setVersion("1.0.2");
        plugin3.setVersionInfo(new IdeaVersionElement("201.3210.10", "212.3210.10"));

        PluginUpdatesUtil.updateOrAdd(plugin3, pluginsElement.getPlugins(), logger);

        assertEquals(1, pluginsElement.getPlugins().size());
        assertEquals("212.3210.10", getPluginVersion("1.0.2", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginVersionChangeWithDifferentSinceNullUntil() {
        PluginElement plugin = new PluginElement();
        plugin.setId(TEST_ID);
        plugin.setVersion(TEST_VERSION);
        plugin.setVersionInfo(new IdeaVersionElement("201.3210", null));
        PluginsElement pluginsElement = new PluginsElement();
        pluginsElement.getPlugins().add(plugin);

        PluginElement plugin2 = new PluginElement();
        plugin2.setId(TEST_ID);
        plugin2.setVersion("1.0.1");
        plugin2.setVersionInfo(new IdeaVersionElement("202", null));

        PluginUpdatesUtil.updateOrAdd(plugin2, pluginsElement.getPlugins(), logger);

        assertEquals(2, pluginsElement.getPlugins().size());

        PluginElement plugin3 = new PluginElement();
        plugin3.setId(TEST_ID);
        plugin3.setVersion("1.0.2");
        plugin3.setVersionInfo(new IdeaVersionElement("202.1000", null));

        PluginUpdatesUtil.updateOrAdd(plugin3, pluginsElement.getPlugins(), logger);

        assertEquals(3, pluginsElement.getPlugins().size());
        assertEquals("201.3210", getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getSinceBuildString());
        assertEquals("201." + (Integer.MAX_VALUE - 1), getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getUntilBuildString());

        assertEquals("202.0", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getSinceBuildString());
        assertEquals("202.999", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
        assertNull(getPluginVersion("1.0.2", pluginsElement).getVersionInfo().getUntilBuildString());
    }


    private PluginElement getPluginVersion(String version, PluginsElement pluginsElement) {
        for (PluginElement plugin : pluginsElement.getPlugins()) {
            if (plugin.getVersion().equals(version)) {
                return plugin;
            }
        }
        return null;
    }
}
