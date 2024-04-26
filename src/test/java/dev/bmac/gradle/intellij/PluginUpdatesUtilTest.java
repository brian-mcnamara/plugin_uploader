package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.xml.IdeaVersionElement;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.api.logging.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PluginUpdatesUtilTest {

    private static final String TEST_ID = "dev.bmac.testPlugin";
    private static final String TEST_VERSION = "1.0.0";
    private static final String TEST_IDEA_VERSION = "201.3210.11";

    private Logger logger = mock(Logger.class);
    private PluginsElement pluginsElement;

    @Before
    public void init() {
        pluginsElement = new PluginsElement();
    }

    @Test
    public void testWithNoExistingPlugins() {
        addPluginToList(TEST_VERSION, TEST_IDEA_VERSION, null);

        assertEquals(1, pluginsElement.getPlugins().size());
        PluginElement stored = pluginsElement.getPlugins().get(0);
        assertEquals(TEST_IDEA_VERSION, stored.getVersionInfo().getSinceBuildString());
        assertNull(stored.getVersionInfo().getUntilBuild());
    }

    @Test
    public void testPluginWithMultipleIdeaVersions() {
        addPluginToList(TEST_VERSION, TEST_IDEA_VERSION, null);

        addPluginToList("1.0.1", "211.3210.12", null);

        assertEquals(2, pluginsElement.getPlugins().size());

        assertEquals("211.3210.11", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginWithIdeaVersionInBetweenExistingVersions() {
        addPluginToList(TEST_VERSION, "201.3210.10", "202.3210.10");

        addPluginToList("1.1.0", "202.3210.11", null);

        addPluginToList("1.0.1", "202.123.1", null);

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
        addPluginToList(TEST_VERSION, "201.3210.10", "202.3210.10");

        addPluginToList("1.0.1", "201.3210.10", "202.3210.10");

        assertEquals(1, pluginsElement.getPlugins().size());

        addPluginToList("1.0.2", "201.3210.10", "212.3210.10");

        assertEquals(1, pluginsElement.getPlugins().size());
        assertEquals("212.3210.10", getPluginVersion("1.0.2", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginVersionChangeWithDifferentSinceNullUntil() {
        addPluginToList(TEST_VERSION, "201.3210", null);

        addPluginToList("1.0.1", "202", null);

        assertEquals(2, pluginsElement.getPlugins().size());

        addPluginToList("1.0.2", "202.1000", null);

        assertEquals(3, pluginsElement.getPlugins().size());
        assertEquals("201.3210", getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getSinceBuildString());
        assertEquals("201." + (Integer.MAX_VALUE - 1), getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getUntilBuildString());

        assertEquals("202.0", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getSinceBuildString());
        assertEquals("202.999", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
        assertNull(getPluginVersion("1.0.2", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginWithNullVersionFollowedBySetVersionUnderMin() {
        addPluginToList(TEST_VERSION, null, null);

        addPluginToList("1.0.1", "181.1", null);

        assertEquals(1, pluginsElement.getPlugins().size());

        assertEquals("181.1", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getSinceBuildString());
        assertNull(getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginWithNullVersionFollowedBySetVersionAboveMin() {
        addPluginToList(TEST_VERSION, null, null);

        addPluginToList("1.0.1", "200.1", null);

        assertEquals(2, pluginsElement.getPlugins().size());

        assertNull(getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getSinceBuild());
        assertEquals("200.0", getPluginVersion("1.0.0", pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("200.1", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getSinceBuildString());
        assertNull(getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testNoPluginUploadedWhenMultiVersionExistAndSinceBuildNotSpecified() {
        addPluginToList(TEST_VERSION, "201.1", "201.2");

        addPluginToList("1.0.1", "201.3", null);

        assertEquals(2, pluginsElement.getPlugins().size());

        reset(logger);
        addPluginToList("1.0.2", null, "211.1");
        verify(logger).error(contains("specify a valid sinceBuild"));

        assertEquals(2, pluginsElement.getPlugins().size());
        assertNull(getPluginVersion("1.0.2", pluginsElement));
    }

    @Test
    public void testWarningWhenMultiVersionExistAndSinceBuildBeforeMinVersion() {
        addPluginToList(TEST_VERSION, "201.1", "201.2");

        addPluginToList("1.0.1", "201.3", null);

        assertEquals(2, pluginsElement.getPlugins().size());

        reset(logger);
        addPluginToList("1.0.2", "181.1", null);
        verify(logger).warn(contains("However plugins since-build is below that"));

        assertEquals(3, pluginsElement.getPlugins().size());
        assertNotNull(getPluginVersion("1.0.2", pluginsElement));
    }

    @Test
    public void testExistingEntryWithSinceBuildIsUpdatedOnNewEntryWithSinceLessThenUntil() {
        addPluginToList(TEST_VERSION, "201.1", "201.3");

        addPluginToList("1.0.1", "201.3", null);

        assertEquals(2, pluginsElement.getPlugins().size());
        assertEquals("201.2", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testExistingEntryWithSinceBuildIsNotUpdatedOnNewEntryWithSinceGreaterThenUntil() {
        addPluginToList(TEST_VERSION, "201.1", "201.2");

        addPluginToList("1.0.1", "201.3", null);

        assertEquals("201.2", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginUntilUpdatedWhenExistingEntryConflictsWithValue() {
        addPluginToList(TEST_VERSION, "201.1", "201.3");

        addPluginToList("1.1.0", "201.5", null);

        addPluginToList("1.0.1", "201.4", "201.6");

        assertEquals("201.4", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
        assertNull(getPluginVersion("1.1.0", pluginsElement).getVersionInfo().getUntilBuild());
    }

    @Test
    public void testPluginWithExistingSinceUpdatesUntil() {
        addPluginToList(TEST_VERSION, "201.1", "201.2");

        addPluginToList("1.1.0", "201.3", null);

        addPluginToList("1.0.1", "201.1", "201.6");

        assertEquals("201.2", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testExistingPluginWithNullSince() {
        addPluginToList(TEST_VERSION, null, "202.1");

        addPluginToList("1.1.0", "201.3", null);

        assertEquals(1, pluginsElement.getPlugins().size());
        assertEquals("201.3", getPluginVersion("1.1.0", pluginsElement).getVersionInfo().getSinceBuildString());
        assertNull(getPluginVersion("1.1.0", pluginsElement).getVersionInfo().getUntilBuild());

        addPluginToList("1.1.1", "201.4", null);
        assertEquals(2, pluginsElement.getPlugins().size());
    }

    @Test
    public void testPluginWithInvalidSince() {
        try {
            addPluginToList(TEST_VERSION, "someInvalidValue", null);
            fail("Expected some exception to be thrown");
        } catch (Exception e) {
            //expected
        }
    }

    @Test
    public void testPluginSorting() {
        addPluginToList("anotherRandomId", "1.0.0", "211.1", null);
        addPluginToList(TEST_VERSION, "211.1", null);
        addPluginToList("1.1.0", "212.1", null);
        addPluginToList("1.2.0", "212.2", null);
        addPluginToList("randomId", "1.0.0", "211.1", null);
        addPluginToList("1.1.1", "212.1", null);
        addPluginToList("0.0.1", "201.1", null);
        addPluginToList("1.0.1", "211.2", null);
        addPluginToList("1.3.0", "213.1", null);

        List<PluginElement> plugins = pluginsElement.getPlugins();
        assertEquals(8, plugins.size());
        assertEquals("anotherRandomId", plugins.get(0).getId());
        assertEquals("0.0.1", plugins.get(1).getVersion());
        assertEquals(TEST_VERSION, plugins.get(2).getVersion());
        assertEquals("1.0.1", plugins.get(3).getVersion());
        assertEquals("1.1.1", plugins.get(4).getVersion());
        assertEquals("1.2.0", plugins.get(5).getVersion());
        assertEquals("1.3.0", plugins.get(6).getVersion());
        assertEquals("randomId", plugins.get(7).getId());
    }

    @Test
    public void testPluginWithWildcardUntilBuild() {
        addPluginToList(TEST_VERSION, "200.1", "241.*");

        assertEquals(1, pluginsElement.getPlugins().size());

        assertEquals("241.*", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
    }

    @Test
    public void testPluginWithWildcardUntilBuildAfterUpdate() {
        addPluginToList(TEST_VERSION, "200.1", "241.*");
        addPluginToList("1.0.1", "241.2", "241.*");
        addPluginToList("1.0.2", "242", "242.*");
        addPluginToList("1.0.3", "242.2.100", "242.*");

        assertEquals(4, pluginsElement.getPlugins().size());

        assertEquals("241.1", getPluginVersion(TEST_VERSION, pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("241.*", getPluginVersion("1.0.1", pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("242.2.99", getPluginVersion("1.0.2", pluginsElement).getVersionInfo().getUntilBuildString());
        assertEquals("242.*", getPluginVersion("1.0.3", pluginsElement).getVersionInfo().getUntilBuildString());
    }


    private void addPluginToList(String version, String since, String until) {
        addPluginToList(TEST_ID, version, since, until);
    }

    private void addPluginToList(String id, String version, String since, String until) {
        PluginElement plugin = new PluginElement();
        plugin.setId(id);
        plugin.setVersion(version);
        if (since != null || until != null) {
            plugin.setVersionInfo(new IdeaVersionElement(since, until));
        }

        PluginUpdatesUtil.updateOrAdd(plugin, pluginsElement.getPlugins(), logger);
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
