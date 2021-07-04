package dev.bmac.gradle.intellij;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.BuildNumber;
import dev.bmac.gradle.intellij.xml.IdeaVersionElement;
import dev.bmac.gradle.intellij.xml.PluginElement;
import org.gradle.api.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for managing the repo plugin list.
 */
public class PluginUpdatesUtil {
    public static final BuildNumber MIN_VERSION = Objects.requireNonNull(BuildNumber.fromString("193.2956.37"));


    /**
     * Updates or adds a plugin entry to the repo list. This has two different strategies based on the since-build of
     * the new entry and the existing ones in the repo.
     * If the new plugin entry has a since-build set before MIN_VERSION, this method will add or update
     * and existing entry ensuring there is only one entry per plugin ID.
     * However if that condition is not met, this method will use the "enhanced" logic to allow multiple plugin entries with
     * the same ID. If the since-build version is the same as an existing entry, that entry is updated, however if there is
     * no existing entries with that since-build, a new entry will be created and existing entries until-build will be updated
     * to ensure there is no version overlap between entries.
     *
     * @param plugin the current plugin being uploaded
     * @param plugins the list of plugins from the server
     * @param logger logger from gradle
     */
    public static void updateOrAdd(PluginElement plugin, List<PluginElement> plugins, Logger logger) {
        List<Integer> existingEntries = getExistingEntries(plugin, plugins);
        boolean useMultiVersion = true;
        if (existingEntries.size() > 1) {
            if (plugin.getVersionInfo() != null && plugin.getVersionInfo().getSinceBuild() != null) {
                if (versionDoesNotAllowMultiVersion(plugin.getVersionInfo().getSinceBuild())) {
                    logger.warn("Notice: Multi-versioning is being used for this plugin on the repository which requires IDEA version "
                            + MIN_VERSION.asString() + ". However plugins since-build is below that." +
                            " Users on earlier versions may experience issues when using this repository. One fix is to update" +
                            " the plugins since-build to be " + MIN_VERSION.asString() + " or greater.");
                }
            } else {
                logger.error("The repository contains multi-version entries for this plugin which requires all uploads to set a since-build. " +
                        "Please specify a valid sinceBuild and try again.");
                return;
            }
        } else {
            if (plugin.getVersionInfo() == null ||
                    versionDoesNotAllowMultiVersion(plugin.getVersionInfo().getSinceBuild())) {
                useMultiVersion = false;
            } else if (existingEntries.size() == 1) {
                PluginElement existingPlugin = plugins.get(existingEntries.get(0));
                // If no since-build is set on existing entry, or a since-version is before MIN_VERSION,
                // we will override it and multi-version can kick in next time.
                if (existingPlugin.getVersionInfo() != null &&
                        versionDoesNotAllowMultiVersion(existingPlugin.getVersionInfo().getSinceBuild())) {
                    useMultiVersion = false;
                }
            }
        }

        if (!useMultiVersion || plugin.getVersionInfo() == null || plugin.getVersionInfo().getSinceBuild() == null) {
            logger.debug("Updating existing plugin entry or adding if none exists");
            if (existingEntries.size() == 1) {
                plugins.set(existingEntries.get(0), plugin);
                return;
            }
            plugins.add(plugin);
        } else {
            boolean addEntry = true;
            Map<BuildNumber, PluginElement> buildNumberMap = Maps.newHashMap();
            for (Integer existingPosition : existingEntries) {
                PluginElement existingPlugin = plugins.get(existingPosition);
                //The same since since-build version or null version info (singly entry mode)
                if (existingPlugin.getVersionInfo() == null && plugin.getVersionInfo() == null ||
                        (existingPlugin.getVersionInfo() != null &&
                                existingPlugin.getVersionInfo().getSinceBuild().compareTo(plugin.getVersionInfo().getSinceBuild()) == 0)) {
                    logger.debug("Updating existing plugin entry as since-build version either does not exist or is identical.");
                    plugins.set(existingPosition, plugin);
                    addEntry = false;
                    continue;
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
                        plugin.getVersionInfo().getUntilBuild().compareTo(after) >= 0) {
                    BuildNumber afterPrior = after.minusOne();
                    logger.info("Updating current plugin entries until-build to " + afterPrior.asString()  +
                            " as entry with version " + afterPlugin.getVersion() + " has a later since-build");
                    plugin.getVersionInfo().setUntilBuild(afterPrior);
                }
            }

            if (addEntry) {
                int location = plugins.size();
                if (existingEntries.size() > 0) {
                    if (existingEntries.size() == position) {
                        location = existingEntries.get(position - 1) + 1;
                    } else {
                        location = existingEntries.get(position);
                    }
                }
                plugins.add(location, plugin);
            }
        }
    }

    private static List<Integer> getExistingEntries(PluginElement pluginElement, List<PluginElement> plugins) {
        List<Integer> entries = Lists.newArrayList();
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).getId().equals(pluginElement.getId())) {
                entries.add(i);
            }
        }
        return entries;
    }

    private static boolean versionDoesNotAllowMultiVersion(BuildNumber version) {
        if (version == null) {
            return true;
        }
        return version.compareTo(MIN_VERSION) < 0;
    }
}
