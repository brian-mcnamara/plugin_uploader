// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the apache-license.txt file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * copy from intellij-community BuildNumber class minus loading build info from the application and unused methods
 */
public final class BuildNumber implements Comparable<BuildNumber> {
    private static final String STAR = "*";

    public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE;

    private final String myProductCode;
    private final int [] myComponents;

    public BuildNumber(@NotNull String productCode, int baselineVersion, int buildNumber) {
        this(productCode, new int[]{baselineVersion, buildNumber});
    }

    public BuildNumber(@NotNull String productCode, int ... components) {
        myProductCode = productCode;
        myComponents = components;
    }


    public @NotNull String asString() {
        StringBuilder builder = new StringBuilder();

        if (!myProductCode.isEmpty()) {
            builder.append(myProductCode).append('-');
        }

        for (int each : myComponents) {
            if (each != SNAPSHOT_VALUE) {
                builder.append(each);
            }
            builder.append('.');
        }
        if (builder.charAt(builder.length() - 1) == '.') {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    public static @Nullable BuildNumber fromString(@Nullable String version) {
        if (version == null) {
            return null;
        }
        version = version.trim();
        return version.isEmpty() ? null : fromString(version, null, null);
    }

    public static @Nullable BuildNumber fromString(@NotNull String version, @Nullable String pluginName, @Nullable String productCodeIfAbsentInVersion) {
        String code = version;
        int productSeparator = code.indexOf('-');
        String productCode;
        if (productSeparator > 0) {
            productCode = code.substring(0, productSeparator);
            code = code.substring(productSeparator + 1);
        }
        else {
            productCode = productCodeIfAbsentInVersion != null ? productCodeIfAbsentInVersion : "";
        }

        int baselineVersionSeparator = code.indexOf('.');

        if (baselineVersionSeparator > 0) {
            String baselineVersionString = code.substring(0, baselineVersionSeparator);
            if (baselineVersionString.trim().isEmpty()) {
                return null;
            }

            String[] stringComponents = code.split("\\.");
            int[] intComponentsList = new int[stringComponents.length];
            for (int i = 0, n = stringComponents.length; i < n; i++) {
                String stringComponent = stringComponents[i];
                int comp = parseBuildNumber(version, stringComponent, pluginName);
                intComponentsList[i] = comp;
                if (comp == SNAPSHOT_VALUE && (i + 1) != n) {
                    intComponentsList = Arrays.copyOf(intComponentsList, i + 1);
                    break;
                }
            }
            return new BuildNumber(productCode, intComponentsList);
        }
        else {
            int buildNumber = parseBuildNumber(version, code, pluginName);
            if (buildNumber <= 2000) {
                // it's probably a baseline, not a build number
                return new BuildNumber(productCode, buildNumber, 0);
            }

            int baselineVersion = getBaseLineForHistoricBuilds(buildNumber);
            return new BuildNumber(productCode, baselineVersion, buildNumber);
        }
    }

    private static int parseBuildNumber(String version, @NotNull String code, String pluginName) {
        if (STAR.equals(code)) {
            return SNAPSHOT_VALUE;
        }

        try {
            return Integer.parseInt(code);
        }
        catch (NumberFormatException e) {
            throw new RuntimeException("Invalid version number: " + version + "; plugin name: " + pluginName);
        }
    }

    @Override
    public int compareTo(@NotNull BuildNumber o) {
        int[] c1 = myComponents;
        int[] c2 = o.myComponents;

        for (int i = 0; i < Math.min(c1.length, c2.length); i++) {
            if (c1[i] == c2[i] && c1[i] == SNAPSHOT_VALUE) return 0;
            if (c1[i] == SNAPSHOT_VALUE) return 1;
            if (c2[i] == SNAPSHOT_VALUE) return -1;
            int result = c1[i] - c2[i];
            if (result != 0) return result;
        }

        return c1.length - c2.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuildNumber that = (BuildNumber)o;

        if (!myProductCode.equals(that.myProductCode)) return false;
        return Arrays.equals(myComponents, that.myComponents);
    }

    @Override
    public int hashCode() {
        int result = myProductCode.hashCode();
        result = 31 * result + Arrays.hashCode(myComponents);
        return result;
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Added by Brian to get a previous build number from this one.
     */
    public BuildNumber minusOne() {
        int[] componentsCopy;
        componentsCopy = Arrays.copyOf(myComponents, myComponents.length);
        for (int i = componentsCopy.length - 1; i >= 0; i--) {
            if (componentsCopy[i] > 0) {
                componentsCopy[i]--;
                break;
            }
            componentsCopy[i] = SNAPSHOT_VALUE - 1;
        }
        return new BuildNumber(myProductCode, componentsCopy);
    }

    /**
     * Added by Brian to get a future build number from this one.
     */
    public BuildNumber plusOne() {
        int[] componentsCopy;
        componentsCopy = Arrays.copyOf(myComponents, myComponents.length);
        for (int i = componentsCopy.length - 1; i >= 0; i--) {
            if (componentsCopy[i] < SNAPSHOT_VALUE - 1) {
                componentsCopy[i]++;
                break;
            }
            componentsCopy[i] = 1;
        }
        return new BuildNumber(myProductCode, componentsCopy);
    }

    // http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html
    private static int getBaseLineForHistoricBuilds(int bn) {
        if (bn >= 10000) return 88; // Maia, 9x builds
        if (bn >= 9500) return 85;  // 8.1 builds
        if (bn >= 9100) return 81;  // 8.0.x builds
        if (bn >= 8000) return 80;  // 8.0, including pre-release builds
        if (bn >= 7500) return 75;  // 7.0.2+
        if (bn >= 7200) return 72;  // 7.0 final
        if (bn >= 6900) return 69;  // 7.0 pre-M2
        if (bn >= 6500) return 65;  // 7.0 pre-M1
        if (bn >= 6000) return 60;  // 6.0.2+
        if (bn >= 5000) return 55;  // 6.0 branch, including all 6.0 EAP builds
        if (bn >= 4000) return 50;  // 5.1 branch
        return 40;
    }
}
