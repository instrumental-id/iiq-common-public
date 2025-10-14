package com.identityworksllc.iiq.common;

import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.identityworksllc.iiq.common.IIQFeature.*;

/**
 * A utility class for determining the current version of IIQ and what features
 * are supported in that version. This class should be kept up to date with new
 * releases and features in IIQ.
 */
public final class IIQVersion {
    /**
     * The supported versions of IIQ, along with the features they introduce and
     * the minimum Java version they require. Each version points to its previous
     * version, forming a chain back to the first supported version (7.3).
     *
     * A version supports a feature if it or any previous version in the chain
     * supports and does not remove the feature.
     */
    public enum Version {
        /**
         * IIQ 7.3
         */
        V7_3(null, 8, 7.3, IQServiceTLS),

        /**
         * IIQ 8.0
         */
        V8_0(V7_3, 8, 8.0, AcceleratorPack),
        /**
         * IIQ 8.1
         */
        V8_1(V8_0, 8, 8.1),
        /**
         * IIQ 8.1p1
         */
        V8_1_P1(V8_1, 8, 8.1),
        /**
         * IIQ 8.1p2
         */
        V8_1_P2(V8_1_P1, 8, 8.1),
        /**
         * IIQ 8.1p3
         */
        V8_1_P3(V8_1_P2, 8, 8.1, EncapsulatedConnectors),
        /**
         * IIQ 8.1p4
         */
        V8_1_P4(V8_1_P3, 8, 8.1),
        /**
         * IIQ 8.2
         */
        V8_2(V8_1, 8, 8.2, FixedJDBCGetObject, EncapsulatedConnectors, RapidSetup, AIRecommender, HostSpecificRequestHandlers, AttributeSyncWorkflows, PluginOAuth2),
        /**
         * IIQ 8.2p1
         */
        V8_2_P1(V8_2, 8, 8.2),
        /**
         * IIQ 8.2p2
         */
        V8_2_P2(V8_2_P1, 8, 8.2),
        /**
         * IIQ 8.2p3
         */
        V8_2_P3(V8_2_P2, 8, 8.2),
        /**
         * IIQ 8.2p4
         */
        V8_2_P4(V8_2_P3, 8, 8.2),
        /**
         * IIQ 8.2p5
         */
        V8_2_P5(V8_2_P4, 8, 8.2),
        /**
         * IIQ 8.2p6
         */
        V8_2_P6(V8_2_P5, 8, 8.2),
        /**
         * IIQ 8.2p7
         */
        V8_2_P7(V8_2_P6, 8, 8.2),
        /**
         * IIQ 8.3
         */
        V8_3(V8_2, 8, 8.3, BundleProfileService),
        /**
         * IIQ 8.3p1
         */
        V8_3_P1(V8_3, 8, 8.3),
        /**
         * IIQ 8.3p2
         */
        V8_3_P2(V8_3_P1, 8, 8.3),
        /**
         * IIQ 8.3p3
         */
        V8_3_P3(V8_3_P2, 8, 8.3),
        /**
         * IIQ 8.3p4
         */
        V8_3_P4(V8_3_P3, 8, 8.3),
        /**
         * IIQ 8.3p5
         */
        V8_3_P5(V8_3_P4, 8, 8.3, IQServiceTLSMandatory),
        /**
         * IIQ 8.4
         */
        V8_4(V8_3, 11, 8.4, AccessHistory, DataExtract, not(AcceleratorPack)),
        /**
         * IIQ 8.4p1
         */
        V8_4_P1(V8_4, 11, 8.4),
        /**
         * IIQ 8.4p2
         */
        V8_4_P2(V8_4_P1, 11, 8.4),
        /**
         * IIQ 8.4p3
         */
        V8_4_P3(V8_4_P2, 11, 8.4, IQServiceTLSMandatory),
        /**
         * IIQ 8.5
         */
        V8_5(V8_4, 11, 8.5, IdentityAttributeAccessControls),
        /**
         * IIQ 8.5p1
         */
        V8_5_P1(V8_5, 11, 8.5),
        /**
         * The latest known version of IIQ. This will be updated with each new release.
         */
        Latest(V8_5_P1, 11, 8.5);

        /**
         * The list of features introduced (or removed) in this version
         */
        private final List<FeatureWrapper> features = new ArrayList<>();

        /**
         * The minimum Java version required for this version of IIQ
         */
        private final int javaVersion;

        /**
         * The major version number (e.g., 8.3)
         */
        private final double majorVersion;

        /**
         * The previous version in the chain, or null if this is the first version (7.3)
         */
        private final Version previous;

        /**
         * Creates a new version enum
         * @param previous the previous version in the chain, or null if this is the first version
         * @param javaVersion the minimum Java version required for this version of IIQ
         * @param majorVersion the major version number (e.g., 8.3)
         * @param introducedFeatures the features introduced (or removed, if wrapped with not()) in this version
         */
        Version(Version previous, int javaVersion, double majorVersion, Object... introducedFeatures) {
            this.previous = previous;
            this.javaVersion = javaVersion;
            this.majorVersion = majorVersion;

            if (introducedFeatures != null) {
                for(Object obj : introducedFeatures) {
                    if (obj instanceof IIQFeature) {
                        features.add(new FeatureWrapper((IIQFeature) obj, false));
                    } else if (obj instanceof FeatureWrapper) {
                        features.add((FeatureWrapper) obj);
                    } else {
                        throw new IllegalArgumentException("Invalid feature type: " + obj.getClass().getName());
                    }
                }
            }
        }

        /**
         * Returns true if this version is at least the specified other version. If the other
         * version is a patch version, it will be rewound back to its base version for comparison.
         *
         * So for example, V8_1_P3.atLeast(V8_1_P1) will return true, as will V8_1_P3.atLeast(V8_1).
         *
         * Null indicates the earliest possible version, so this will always return true for null input.
         *
         * @param otherVersion the other version to compare against
         * @return true if this version is at least the other version, false otherwise
         */
        public boolean atLeast(Version otherVersion) {
            if (otherVersion == null) {
                return true;
            }

            if (otherVersion == this) {
                return true;
            }

            Version baseOther = otherVersion.getBaseVersion();
            if (baseOther == this && otherVersion != this) {
                // Same base but other is a patch and this is not, so this is not at least the other
                return false;
            }

            Version current = this;
            while (current != null) {
                if (current == baseOther) {
                    return true;
                }
                current = current.previous;
            }

            return false;
        }

        /**
         * Gets the base version (e.g., V8_1 for V8_1_P3)
         * @return The base version
         */
        public Version getBaseVersion() {
            Version base = this;
            while (base.previous != null && base.name().contains("_P")) {
                base = base.previous;
            }
            return base;
        }

        /**
         * Gets the major version of IIQ as a number
         * @return The major version (e.g., 8.3)
         */
        public double getMajorVersion() {
            return majorVersion;
        }

        /**
         * Gets the previous version in the chain, or null if this is the first version
         * @return The previous version, or null
         */
        public Version getPrevious() {
            return previous;
        }

        /**
         * Returns true if this version of IIQ supports the given feature. This version
         * and all parent versions will be checked.
         *
         * @param feature The feature to check
         * @return True if supported, false otherwise
         */
        public boolean supportsFeature(IIQFeature feature) {
            for(FeatureWrapper fw : features) {
                if (fw.feature == feature) {
                    return !fw.negative;
                }
            }
            if (previous != null) {
                return previous.supportsFeature(feature);
            }
            return false;
        }

        /**
         * Returns true if this version of IIQ requires at least the given Java version.
         * For 8.3, for example, this will return true for 11 and 8. For 8.4, it will return
         * false for 8 and true for 11.
         *
         * @param version The Java version to check
         * @return True if supported, false otherwise
         */
        public boolean supportsJavaVersion(int version) {
            return javaVersion <= version;
        }
    }

    /**
     * A wrapper for a feature that indicates whether it is being added or removed in this version.
     * This is for internal use only.
     */
    private static final class FeatureWrapper {
        /**
         * The feature being wrapped
         */
        private IIQFeature feature;

        /**
         * Whether the feature no longer exists as of this version
         */
        private boolean negative;

        /**
         * Creates a new feature wrapper
         * @param feature the feature
         * @param negative true if the feature is being removed in this version, false if it is being added
         */
        public FeatureWrapper(IIQFeature feature, boolean negative) {
            this.feature = feature;
            this.negative = negative;
        }
    }
    /**
     * The singleton instance of this class
     */
    private static final IIQVersion INSTANCE = new IIQVersion();

    /**
     * The cached version, once determined
     */
    private final AtomicReference<Version> cachedVersion;

    /**
     * Private constructor for singleton
     */
    private IIQVersion() {
        this.cachedVersion = new AtomicReference<>(null);
    }

    /**
     * Gets the current version of IIQ
     * @return The current version
     */
    public static Version current() {
        if (INSTANCE.cachedVersion.get() != null) {
            return INSTANCE.cachedVersion.get();
        }

        String majorVersion = sailpoint.Version.getVersion();
        String patchLevel = sailpoint.Version.getPatchLevel();

        // Default to the most likely version here if we can't determine it
        Version finalVersion = Version.V8_3;

        Version calculated = determineVersion(majorVersion, patchLevel);
        if (calculated != null) {
            finalVersion = calculated;
        }

        INSTANCE.cachedVersion.set(finalVersion);
        return finalVersion;
    }

    /**
     * Determines the version based on the major version and patch level strings
     * @param majorVersion The major version string
     * @param patchLevel The patch level string
     * @return The determined version, or null if it could not be determined
     */
    private static Version determineVersion(String majorVersion, String patchLevel) {
        if (Util.nullSafeEq(majorVersion, "7.3")) {
            return Version.V7_3;
        } else if (Util.nullSafeEq(majorVersion, "8.0")) {
            return Version.V8_0;
        } else if (Util.nullSafeEq(majorVersion, "8.1")) {
            if (Util.nullSafeEq(patchLevel, "p1")) {
                return Version.V8_1_P1;
            } else if (Util.nullSafeEq(patchLevel, "p2")) {
                return Version.V8_1_P2;
            } else if (Util.nullSafeEq(patchLevel, "p3")) {
                return Version.V8_1_P3;
            } else if (Util.nullSafeEq(patchLevel, "p4")) {
                return Version.V8_1_P4;
            } else {
                return Version.V8_1;
            }
        } else if (Util.nullSafeEq(majorVersion, "8.2")) {
            if (Util.nullSafeEq(patchLevel, "p1")) {
                return Version.V8_2_P1;
            } else if (Util.nullSafeEq(patchLevel, "p2")) {
                return Version.V8_2_P2;
            } else if (Util.nullSafeEq(patchLevel, "p3")) {
                return Version.V8_2_P3;
            } else if (Util.nullSafeEq(patchLevel, "p4")) {
                return Version.V8_2_P4;
            } else if (Util.nullSafeEq(patchLevel, "p5")) {
                return Version.V8_2_P5;
            } else if (Util.nullSafeEq(patchLevel, "p6")) {
                return Version.V8_2_P6;
            } else if (Util.nullSafeEq(patchLevel, "p7")) {
                return Version.V8_2_P7;
            } else {
                return Version.V8_2;
            }
        } else if (Util.nullSafeEq(majorVersion, "8.3")) {
            if (Util.nullSafeEq(patchLevel, "p1")) {
                return Version.V8_3_P1;
            } else if (Util.nullSafeEq(patchLevel, "p2")) {
                return Version.V8_3_P2;
            } else if (Util.nullSafeEq(patchLevel, "p3")) {
                return Version.V8_3_P3;
            } else if (Util.nullSafeEq(patchLevel, "p4")) {
                return Version.V8_3_P4;
            } else if (Util.nullSafeEq(patchLevel, "p5")) {
                return Version.V8_3_P5;
            } else {
                return Version.V8_3;
            }
        } else if (Util.nullSafeEq(majorVersion, "8.4")) {
            if (Util.nullSafeEq(patchLevel, "p1")) {
                return Version.V8_4_P1;
            } else if (Util.nullSafeEq(patchLevel, "p2")) {
                return Version.V8_4_P2;
            } else if (Util.nullSafeEq(patchLevel, "p3")) {
                return Version.V8_4_P3;
            } else {
                return Version.V8_4;
            }
        } else if (Util.nullSafeEq(majorVersion, "8.5")) {
            if (Util.nullSafeEq(patchLevel, "p1")) {
                return Version.V8_5_P1;
            } else {
                return Version.V8_5;
            }
        }

        return null;
    }

    /**
     * Wraps a feature as a negative (removal) feature
     * @param feature the feature to wrap
     * @return the wrapped feature
     */
    private static FeatureWrapper not(IIQFeature feature) {
        return new FeatureWrapper(feature, true);
    }
}
