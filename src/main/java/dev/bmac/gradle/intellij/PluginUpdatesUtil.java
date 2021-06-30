package dev.bmac.gradle.intellij;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.BuildNumber;
import dev.bmac.gradle.intellij.xml.IdeaVersionElement;
import dev.bmac.gradle.intellij.xml.PluginElement;
import org.gradle.api.logging.Logger;

import java.util.*;

public class PluginUpdatesUtil {
    public static final BuildNumber MIN_VERSION = Objects.requireNonNull(BuildNumber.fromString("193.2956.37"));


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
            logger.debug("Creating new plugin version and updating existing entries to ensure no version conflict");
            List<PluginElement> existingPlugins = Lists.newArrayList();
            Map<BuildNumber, PluginElement> buildNumberMap = Maps.newHashMap();
            for (int i = 0; i < plugins.size(); i++) {
                PluginElement existingPlugin = plugins.get(i);
                if (plugin.getId().equals(existingPlugin.getId())) {
                    //The same since version.
                    if (existingPlugin.getVersionInfo() == null && plugin.getVersionInfo() == null ||
                            (existingPlugin.getVersionInfo() != null &&
                                    existingPlugin.getVersionInfo().getSinceBuild().compareTo(plugin.getVersionInfo().getSinceBuild()) == 0)) {
                        plugins.set(i, plugin);
                        return;
                    } else {
                        existingPlugins.add(existingPlugin);
                        if (existingPlugin.getVersion().equals(plugin.getVersion())) {
                            logger.error("Existing plugin version detected in repository.");
                        }
                    }
                }
            }
            //Pass two, will not get here if updating plugin
            BuildNumber untilVersion = plugin.getVersionInfo().getSinceBuild().minusOne();
            for (PluginElement existingPlugin : existingPlugins) {
                IdeaVersionElement existingPluginVersion = existingPlugin.getVersionInfo();
                //Ensure existing plugins have a idea-version set and the until version is set when the since build is before the new entries since-build
                if (existingPluginVersion == null ||
                        existingPluginVersion.getSinceBuild().compareTo(plugin.getVersionInfo().getSinceBuild()) < 0 &&
                                (existingPluginVersion.getUntilBuild() == null || existingPluginVersion.getUntilBuild().compareTo(untilVersion) > 0)) {
                    logger.info("Updating existing plugin entry with version " + existingPlugin.getVersion() +
                            " until version to " + untilVersion.asString());
                    BuildNumber since = existingPluginVersion == null ? null : existingPluginVersion.getSinceBuild();
                    existingPlugin.setVersionInfo(new IdeaVersionElement(since, untilVersion));
                }

                if (existingPluginVersion != null && existingPluginVersion.getSinceBuild() != null) {
                    buildNumberMap.put(existingPluginVersion.getSinceBuild(), existingPlugin);
                }
            }

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

            if (position < buildNumbers.size() - 1) {
                if (position > 0) {
                    BuildNumber prior = buildNumbers.get(position - 1);
                    PluginElement priorPlugin = buildNumberMap.get(prior);
                    if (prior.equals(priorPlugin.getVersionInfo().getSinceBuild())) {
                        priorPlugin.getVersionInfo().setUntilBuild(plugin.getVersionInfo().getSinceBuild().minusOne());
                    } else {
                        plugin.getVersionInfo().setUntilBuild(priorPlugin.getVersionInfo().getSinceBuild().minusOne());
                    }
                }
                BuildNumber after = buildNumbers.get(position + 1);
                PluginElement afterPlugin = buildNumberMap.get(after);
                if (after.equals(afterPlugin.getVersionInfo().getSinceBuild())) {
                    plugin.getVersionInfo().setUntilBuild(after.minusOne());
                } else {
                    plugin.getVersionInfo().setUntilBuild(afterPlugin.getVersionInfo().getSinceBuild().minusOne());
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
            if (plugin.getVersionInfo() != null && plugin.getVersionInfo().getSinceBuildString() != null) {
                legacyPluginFound |= !versionAllowsMultipleEntries(plugin.getVersionInfo().getSinceBuild());
            }
        }
        return legacyPluginFound;
    }

    private static boolean versionAllowsMultipleEntries(BuildNumber version) {
        return version.compareTo(MIN_VERSION) > 0;
    }
}
