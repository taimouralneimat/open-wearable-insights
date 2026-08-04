package com.openwearableinsights.api.biomarkers;

import com.openwearableinsights.api.biomarkers.application.BiomarkerReferenceCatalog;
import com.openwearableinsights.api.biomarkers.domain.BiomarkerReferenceEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity checks for the static {@link BiomarkerReferenceCatalog} seed data:
 * every entry is well-formed, names are unique, categories come from the
 * known set, and — the load-bearing rule for this module's data-honesty
 * guarantee — no entry carries a numeric reference range.
 */
class BiomarkerReferenceCatalogTest {

    @Test
    void catalog_isNotEmpty() {
        assertThat(BiomarkerReferenceCatalog.all()).isNotEmpty();
        assertThat(BiomarkerReferenceCatalog.all().size()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void everyEntry_hasNonBlankNameCategoryAndDescription() {
        for (BiomarkerReferenceEntry entry : BiomarkerReferenceCatalog.all()) {
            assertThat(entry.name()).isNotBlank();
            assertThat(entry.category()).isNotBlank();
            assertThat(entry.description()).isNotBlank();
        }
    }

    @Test
    void names_areUnique() {
        List<BiomarkerReferenceEntry> all = BiomarkerReferenceCatalog.all();
        Set<String> distinctNames = all.stream()
                .map(e -> e.name().toLowerCase())
                .collect(Collectors.toSet());
        assertThat(distinctNames).hasSize(all.size());
    }

    @Test
    void categories_areFromTheKnownSet() {
        Set<String> knownCategories = Set.of(
                BiomarkerReferenceCatalog.LIPID_CARDIOVASCULAR,
                BiomarkerReferenceCatalog.METABOLIC_GLUCOSE,
                BiomarkerReferenceCatalog.HORMONES,
                BiomarkerReferenceCatalog.VITAMINS_MINERALS,
                BiomarkerReferenceCatalog.INFLAMMATION,
                BiomarkerReferenceCatalog.BLOOD_COUNT,
                BiomarkerReferenceCatalog.KIDNEY,
                BiomarkerReferenceCatalog.LIVER,
                BiomarkerReferenceCatalog.ELECTROLYTES
        );
        for (BiomarkerReferenceEntry entry : BiomarkerReferenceCatalog.all()) {
            assertThat(knownCategories).contains(entry.category());
        }
    }

    @Test
    void lookup_isCaseInsensitiveAndTrimsWhitespace() {
        assertThat(BiomarkerReferenceCatalog.lookup("ldl cholesterol")).isPresent();
        assertThat(BiomarkerReferenceCatalog.lookup("  LDL Cholesterol  ")).isPresent();
        assertThat(BiomarkerReferenceCatalog.lookup("LDL CHOLESTEROL").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.LIPID_CARDIOVASCULAR);
    }

    @Test
    void lookup_unknownName_returnsEmptyNotAGuess() {
        assertThat(BiomarkerReferenceCatalog.lookup("Some Completely Made Up Marker")).isEmpty();
        assertThat(BiomarkerReferenceCatalog.lookup(null)).isEmpty();
    }

    @Test
    void knownAnchorBiomarkers_arePresentWithExpectedCategory() {
        assertThat(BiomarkerReferenceCatalog.lookup("Hemoglobin A1c (HbA1c)").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.METABOLIC_GLUCOSE);
        assertThat(BiomarkerReferenceCatalog.lookup("Testosterone").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.HORMONES);
        assertThat(BiomarkerReferenceCatalog.lookup("Vitamin D").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.VITAMINS_MINERALS);
        assertThat(BiomarkerReferenceCatalog.lookup("hs-CRP").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.INFLAMMATION);
        assertThat(BiomarkerReferenceCatalog.lookup("WBC").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.BLOOD_COUNT);
        assertThat(BiomarkerReferenceCatalog.lookup("eGFR").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.KIDNEY);
        assertThat(BiomarkerReferenceCatalog.lookup("ALT").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.LIVER);
        assertThat(BiomarkerReferenceCatalog.lookup("Chloride").get().category())
                .isEqualTo(BiomarkerReferenceCatalog.ELECTROLYTES);
    }
}
