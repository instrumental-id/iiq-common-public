package com.identityworksllc.iiq.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link IIQVersion.Version} and {@link IIQFeature} classes.
 */
public class VersionTests {

    @Test
    void getBaseVersion_forPatchVersions_returnsBase() {
        assertEquals(IIQVersion.Version.V8_1, IIQVersion.Version.V8_1_P3.getBaseVersion());
        assertEquals(IIQVersion.Version.V8_2, IIQVersion.Version.V8_2_P7.getBaseVersion());
        // non-patch returns itself
        assertEquals(IIQVersion.Version.V8_2, IIQVersion.Version.V8_2.getBaseVersion());
    }

    @Test
    void atLeast_comparesAcrossPatchesAndMajors() {
        // patch vs patch
        assertTrue(IIQVersion.Version.V8_1_P3.atLeast(IIQVersion.Version.V8_1_P1));
        assertTrue(IIQVersion.Version.V8_2_P3.atLeast(IIQVersion.Version.V8_1_P1));
        // patch vs base
        assertTrue(IIQVersion.Version.V8_1_P3.atLeast(IIQVersion.Version.V8_1));
        // base not at least a later patch
        assertFalse(IIQVersion.Version.V8_1.atLeast(IIQVersion.Version.V8_1_P3));
        // different major
        assertFalse(IIQVersion.Version.V8_2.atLeast(IIQVersion.Version.V8_3));
        assertTrue(IIQVersion.Version.V8_3.atLeast(IIQVersion.Version.V8_2));
        // different major with patch
        assertTrue(IIQVersion.Version.V8_3.atLeast(IIQVersion.Version.V8_2_P1));
        assertFalse(IIQVersion.Version.V8_2_P1.atLeast(IIQVersion.Version.V8_3));
        // null means earliest
        assertTrue(IIQVersion.Version.V8_3.atLeast(null));
    }

    @Test
    void supportsFeature_introducedAndRemovedBehavior() {
        // AcceleratorPack introduced in V8_0
        assertTrue(IIQVersion.Version.V8_0.supportsFeature(IIQFeature.AcceleratorPack));
        // Still present in V8_3
        assertTrue(IIQVersion.Version.V8_3.supportsFeature(IIQFeature.AcceleratorPack));
        // Removed in V8_4 (declared as not(AcceleratorPack))
        assertFalse(IIQVersion.Version.V8_4.supportsFeature(IIQFeature.AcceleratorPack));

        // BundleProfileService introduced in V8_3
        assertTrue(IIQVersion.Version.V8_3.supportsFeature(IIQFeature.BundleProfileService));
        assertFalse(IIQVersion.Version.V8_2.supportsFeature(IIQFeature.BundleProfileService));

        // Feature that was never introduced should be false on earlier versions
        assertFalse(IIQVersion.Version.V7_3.supportsFeature(IIQFeature.PluginOAuth2));
    }

    @Test
    void supportsJavaVersion_checksMinimumJavaRequirement() {
        // V7_3 requires Java 8
        assertTrue(IIQVersion.Version.V7_3.supportsJavaVersion(8));
        assertTrue(IIQVersion.Version.V7_3.supportsJavaVersion(11));

        // V8_3_P5 is the last version to support Java 8
        assertTrue(IIQVersion.Version.V8_3_P5.supportsJavaVersion(8));
        assertTrue(IIQVersion.Version.V8_3_P5.supportsJavaVersion(11));

        assertFalse(IIQVersion.Version.V8_4.supportsJavaVersion(8));
        assertTrue(IIQVersion.Version.V8_4.supportsJavaVersion(11));
    }
}
