package dev.bmac.gradle.intellij;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.BuildNumber;
import dev.bmac.gradle.intellij.xml.IdeaVersionElement;
import dev.bmac.gradle.intellij.xml.PluginElement;
import org.gradle.api.logging.Logger;

import java.util.*;

/**
 * Utility for managing the repo plugin list.
 */
public class PluginUpdatesUtil {
    public static final BuildNumber MIN_VERSION = Objects.requireNonNull(BuildNumber.fromString("193.2956.37"));


    /**
     * Updates or adds a plugin entry to the repo list. This has two different strategies based on the since-build of
     * the new entry and the existing ones in the repo.
     * If any plugin entry for the current plugin ID has a since-build set before MIN_VERSION, this method will add or update
     * and existing entry ensuring there is only one entry per plugin ID.
     * However if that condition is not met, this method will use the "enhanced" logic to allow multiple plugin entries with
     * the same ID. If the since-build version is the same as an existing entry, that entry is updated, however if there is
     * no existing entries with that since-build, a new entry will be created and existing entries until-build will be updated
     * to ensure there is no version overlap between entries.
     * @param plugin the current plugin being uploaded
     * @param plugins the list of plugins from the server
     * @param logger logger from gradle
     */
    public static void updateOrAdd(PluginElement plugin, List<PluginElement> plugins, Logger logger) {
        boolean allowsMultipleVersions = !hasLegacyPlugins(plugins);
        if (allowsMultipleVersions && plugin.getVersionInfo() != null && plugin.getVersionInfo().getSinceBuild() != null) {
            if (!versionAllowsMultipleEntries(plugin.getVersionInfo().getSinceBuild())) {
                logger.warn("Notice: Multiple plugin versions are being used in updatePlugins.xml which requires IDEA version "
                        + MIN_VERSION.asString() + " which is greater then this plugins since-build." +
                        " Users on earlier versions may experience issues when using this repository. One fix is to update" +
                        " the plugins since-build to be " + MIN_VERSION.asString() + " or greater.");
                allowsMultipleVersions = false;
            }
        }

        if (!allowsMultipleVersions || plugin.getVersionInfo() == null || plugin.getVersionInfo().getSinceBuild() == null) {
            logger.debug("Updating existing plugin entry or adding if none exists");
            for (int i = 0; i < plugins.size(); i++) {
                if (plugin.getId().equals(plugins.get(i).getId())) {
                    plugins.set(i, plugin);
                    return;
                }
            }
        } else {
            Map<BuildNumber, PluginElement> buildNumberMap = Maps.newHashMap();
            for (int i = 0; i < plugins.size(); i++) {
                PluginElement existingPlugin = plugins.get(i);
                if (plugin.getId().equals(existingPlugin.getId())) {
                    //The same since since-build version or null version info (singly entry mode)
                    if (existingPlugin.getVersionInfo() == null && plugin.getVersionInfo() == null ||
                            (existingPlugin.getVersionInfo() != null &&
                                    existingPlugin.getVersionInfo().getSinceBuild().compareTo(plugin.getVersionInfo().getSinceBuild()) == 0)) {
                        logger.debug("Updating existing plugin entry as since-build version either does not exist or is identical.");
                        plugins.set(i, plugin);
                        return;
                    } else if (existingPlugin.getVersionInfo() == null) {
                        BuildNumber currentMinusOne = plugin.getVersionInfo().getSinceBuild().minusOne();
                        logger.info("Adding idea-version with until-build set to " + currentMinusOne.asString() + " to existing plugin entry on the repository");
                        existingPlugin.setVersionInfo(new IdeaVersionElement(null, currentMinusOne));
                    }

                    if (existingPlugin.getVersion().equals(plugin.getVersion())) {
                        logger.error("Existing plugin entry with identical version detected in repository.");
                    }

                    if (existingPlugin.getVersionInfo().getSinceBuild() != null) {
                        buildNumberMap.put(existingPlugin.getVersionInfo().getSinceBuild(), existingPlugin);
                    }
                }
            }

            logger.debug("Creating new plugin version and updating existing entries to ensure no version conflict (if any exist)");

            ArrayList<BuildNumber> buildNumbers = new ArrayList<>(buildNumberMap.keySet());
            buildNumbers.add(plugin.getVersionInfo().getSinceBuild());
            buildNumbers.sort(BuildNumber::compareTo);
            int position = 0;
            for (BuildNumber bn : buildNumbers) {
                if (bn.equals(plugin.getVersionInfo().getSinceBuild())) {
                    break;
                }
                position++;
            }

            if (position > 0) {
                BuildNumber prior = buildNumbers.get(position - 1);
                PluginElement priorPlugin = buildNumberMap.get(prior);
                if (priorPlugin.getVersionInfo().getUntilBuild() == null ||
                                priorPlugin.getVersionInfo().getUntilBuild().compareTo(plugin.getVersionInfo().getSinceBuild()) >= 0) {
                    BuildNumber priorUntil = plugin.getVersionInfo().getSinceBuild().minusOne();
                    logger.info("Updating existing plugin entry with version " + priorPlugin.getVersion() +
                            " until version to " + priorUntil.asString());
                    priorPlugin.getVersionInfo().setUntilBuild(priorUntil);
                }
            }
            if (position < buildNumbers.size() - 1) {
                BuildNumber after = buildNumbers.get(position + 1);
                PluginElement afterPlugin = buildNumberMap.get(after);
                if (plugin.getVersionInfo().getUntilBuild() == null ||
                        plugin.getVersionInfo().getUntilBuild().compareTo(after) <= 0) {
                    BuildNumber afterPrior = after.minusOne();
                    logger.info("Updating current plugin entries until-build to " + afterPrior.asString()  +
                            " as entry with version " + afterPlugin.getVersion() + " has a later since-build");
                    plugin.getVersionInfo().setUntilBuild(afterPrior);
                }
            }
        }
        plugins.add(plugin);
    }

    /**
     * Returns true if an entry in pluginUpdates contains a version which is not compatible for multiple plugin entries
     * using unique since-until versions. (See BuildNumberUtils.MIN_VERSION). Also will return false if there already
     * exists multiple plugin entries.
     */
    private static boolean hasLegacyPlugins(List<PluginElement> plugins) {
        boolean legacyPluginFound = false;
        Set<String> ids = Sets.newHashSet();
        for (PluginElement plugin : plugins) {
            if (!ids.add(plugin.getId())) {
                return false;
            }
            if (plugin.getVersionInfo() != null && plugin.getVersionInfo().getUntilBuild() != null) {
                legacyPluginFound |= !versionAllowsMultipleEntries(plugin.getVersionInfo().getSinceBuild());
            }
        }
        return legacyPluginFound;
    }

    private static boolean versionAllowsMultipleEntries(BuildNumber version) {
        return version.compareTo(MIN_VERSION) > 0;
    }
}
