package com.openwearableinsights.api.biomarkers;

import com.openwearableinsights.api.biomarkers.application.BiomarkerCsvParser;
import com.openwearableinsights.api.biomarkers.domain.BiomarkerCsvParseResult;
import com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BiomarkerCsvParser}. Pure logic, no mocks needed —
 * this class has no dependencies (see its Javadoc for why it's kept
 * stateless). Uses synthetic lab values only — no real health data.
 */
class BiomarkerCsvParserTest {

    @Test
    void looksLikeBiomarkerCsv_trueForBiomarkerHeader() {
        List<String> lines = List.of("date,biomarker_name,value,unit", "2026-07-01,Glucose,88,mg/dL");
        assertThat(BiomarkerCsvParser.looksLikeBiomarkerCsv(lines)).isTrue();
    }

    @Test
    void looksLikeBiomarkerCsv_falseForGenericVendorCsvHeader() {
        List<String> lines = List.of("time,metric_type,value,unit", "2026-07-27T08:00:00Z,hr,60.0,bpm");
        assertThat(BiomarkerCsvParser.looksLikeBiomarkerCsv(lines)).isFalse();
    }

    @Test
    void looksLikeBiomarkerCsv_falseForEmptyFile() {
        assertThat(BiomarkerCsvParser.looksLikeBiomarkerCsv(List.of())).isFalse();
    }

    @Test
    void looksLikeBiomarkerCsv_columnOrderDoesNotMatter() {
        List<String> lines = List.of("unit,value,date,biomarker_name", "mg/dL,88,2026-07-01,Glucose");
        assertThat(BiomarkerCsvParser.looksLikeBiomarkerCsv(lines)).isTrue();
    }

    @Test
    void parse_requiredColumnsOnly_parsesCorrectly() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "2026-07-01,Glucose,88,mg/dL"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rowErrors()).isEmpty();
        ParsedBiomarkerRow row = result.rows().get(0);
        assertThat(row.readingDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(row.biomarkerName()).isEqualTo("Glucose");
        assertThat(row.value()).isEqualTo(88.0);
        assertThat(row.unit()).isEqualTo("mg/dL");
        assertThat(row.referenceLow()).isNull();
        assertThat(row.referenceHigh()).isNull();
        // Auto-filled from the reference catalog since no category column was supplied.
        assertThat(row.category()).isEqualTo("Metabolic/Glucose");
    }

    @Test
    void parse_withOptionalReferenceRangeColumns_parsesBothBounds() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit,reference_low,reference_high",
                "2026-07-01,LDL Cholesterol,95,mg/dL,0,100"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        ParsedBiomarkerRow row = result.rows().get(0);
        assertThat(row.referenceLow()).isEqualTo(0.0);
        assertThat(row.referenceHigh()).isEqualTo(100.0);
    }

    @Test
    void parse_oneSidedReferenceRange_leavesOtherBoundNull() {
        // Real-world case: many labs only publish an upper bound (e.g. triglycerides "below X").
        List<String> lines = List.of(
                "date,biomarker_name,value,unit,reference_low,reference_high",
                "2026-07-01,Triglycerides,90,mg/dL,,150"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        ParsedBiomarkerRow row = result.rows().get(0);
        assertThat(row.referenceLow()).isNull();
        assertThat(row.referenceHigh()).isEqualTo(150.0);
    }

    @Test
    void parse_explicitCategoryColumn_overridesReferenceCatalogLookup() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit,category",
                "2026-07-01,Glucose,88,mg/dL,Custom Panel"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows().get(0).category()).isEqualTo("Custom Panel");
    }

    @Test
    void parse_unknownBiomarkerName_categoryIsNullNotGuessed() {
        // Data-honesty rule: a name not in the shipped reference catalog and
        // with no CSV category column must not get a fabricated category.
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "2026-07-01,Some Custom Lab Marker,3.2,units"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows().get(0).category()).isNull();
    }

    @Test
    void parse_invalidDate_rowErrorSkipsRowButOthersStillParse() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "not-a-date,Glucose,88,mg/dL",
                "2026-07-01,Glucose,90,mg/dL"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rowErrors()).hasSize(1);
        assertThat(result.rowErrors().get(0)).contains("invalid date");
    }

    @Test
    void parse_invalidNumericValue_rowErrorSkipsRow() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "2026-07-01,Glucose,not-a-number,mg/dL"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).isEmpty();
        assertThat(result.rowErrors()).hasSize(1);
        assertThat(result.rowErrors().get(0)).contains("invalid numeric value");
    }

    @Test
    void parse_blankBiomarkerName_rowError() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "2026-07-01,,88,mg/dL"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).isEmpty();
        assertThat(result.rowErrors()).hasSize(1);
        assertThat(result.rowErrors().get(0)).contains("biomarker_name is blank");
    }

    @Test
    void parse_unparseableOptionalReferenceValue_isWarningNotRowError() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit,reference_low,reference_high",
                "2026-07-01,Glucose,88,mg/dL,not-a-number,99"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rowErrors()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.rows().get(0).referenceLow()).isNull();
        assertThat(result.rows().get(0).referenceHigh()).isEqualTo(99.0);
    }

    @Test
    void parse_blankLinesBetweenRows_areSkipped() {
        List<String> lines = List.of(
                "date,biomarker_name,value,unit",
                "",
                "2026-07-01,Glucose,88,mg/dL",
                "   ",
                "2026-07-02,Glucose,90,mg/dL"
        );

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rowErrors()).isEmpty();
    }

    @Test
    void parse_emptyInput_returnsEmptyResultNotError() {
        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(List.of());
        assertThat(result.rows()).isEmpty();
        assertThat(result.rowErrors()).isEmpty();
    }

    /**
     * End-to-end check against the real synthetic fixture (see
     * src/test/resources/synthetic-biomarkers.csv and
     * packages/test-data/synthetic-biomarkers.csv) — 15 fabricated readings
     * across two dates and multiple categories, no real health data.
     */
    @Test
    void parse_syntheticFixtureFile_parsesCleanlyWithNoRowErrors() throws IOException {
        List<String> lines = readFixtureLines("synthetic-biomarkers.csv");

        assertThat(BiomarkerCsvParser.looksLikeBiomarkerCsv(lines)).isTrue();

        BiomarkerCsvParseResult result = BiomarkerCsvParser.parse(lines);

        assertThat(result.rowErrors()).isEmpty();
        assertThat(result.rows()).hasSize(15);
        // Every biomarker name in the fixture is a known catalog entry.
        assertThat(result.rows()).allMatch(r -> r.category() != null);

        // Spot-check one row with both reference bounds and a real category lookup.
        ParsedBiomarkerRow ldlApril = result.rows().stream()
                .filter(r -> r.biomarkerName().equals("LDL Cholesterol") && r.readingDate().equals(LocalDate.of(2026, 4, 15)))
                .findFirst().orElseThrow();
        assertThat(ldlApril.value()).isEqualTo(118.0);
        assertThat(ldlApril.referenceLow()).isEqualTo(0.0);
        assertThat(ldlApril.referenceHigh()).isEqualTo(99.0);
        assertThat(ldlApril.category()).isEqualTo("Lipid/Cardiovascular");

        // Spot-check a one-sided reference range (HDL: only a low bound is clinically standard).
        ParsedBiomarkerRow hdlApril = result.rows().stream()
                .filter(r -> r.biomarkerName().equals("HDL Cholesterol") && r.readingDate().equals(LocalDate.of(2026, 4, 15)))
                .findFirst().orElseThrow();
        assertThat(hdlApril.referenceLow()).isEqualTo(40.0);
        assertThat(hdlApril.referenceHigh()).isNull();
    }

    private static List<String> readFixtureLines(String resourceName) throws IOException {
        try (InputStream in = BiomarkerCsvParserTest.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IOException("Test fixture not found on classpath: " + resourceName);
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return content.lines().toList();
        }
    }
}
