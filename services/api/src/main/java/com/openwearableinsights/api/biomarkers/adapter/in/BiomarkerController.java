package com.openwearableinsights.api.biomarkers.adapter.in;

import com.openwearableinsights.api.biomarkers.application.BiomarkerReadingRepository;
import com.openwearableinsights.api.biomarkers.application.BiomarkerReferenceCatalog;
import com.openwearableinsights.api.biomarkers.domain.BiomarkerReading;
import com.openwearableinsights.api.biomarkers.domain.BiomarkerReferenceEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for imported blood biomarker (lab bloodwork) readings —
 * see docs/product/parity-matrix.md row 22.
 *
 * <p>Every reading returned here came from a real user-uploaded CSV row
 * (see {@code ingestion.application.ImportService}'s biomarker-CSV branch);
 * nothing is computed or fabricated. Reference ranges, when present, are
 * whatever the source CSV row itself supplied — never a range this service
 * invented (see {@link BiomarkerReferenceCatalog}).
 *
 * <p>This data is for personal tracking only and is not a medical
 * assessment or diagnosis — consult a healthcare professional to interpret
 * lab results. The Flutter UI repeats this alongside every reading.
 */
@RestController
@RequestMapping("/api/v1/biomarkers")
@Tag(name = "Biomarkers", description = "Imported blood biomarker (lab bloodwork) readings")
public class BiomarkerController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final BiomarkerReadingRepository readingRepository;

    public BiomarkerController(BiomarkerReadingRepository readingRepository) {
        this.readingRepository = readingRepository;
    }

    @GetMapping("/readings")
    @Operation(summary = "List imported biomarker readings",
            description = "Returns real readings imported from CSV, most recent first. Optionally filtered by biomarker name and/or date range. Empty list if nothing has been imported yet.")
    public List<BiomarkerReading> getReadings(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return readingRepository.findReadings(DEFAULT_ACCOUNT_ID, name, from, to);
    }

    @GetMapping("/names")
    @Operation(summary = "List biomarker names with at least one imported reading",
            description = "Distinct biomarker names actually present in this account's imported data, alphabetical — used to populate a trend picker without guessing at names nothing was ever imported for.")
    public List<String> getNames() {
        return readingRepository.findDistinctNames(DEFAULT_ACCOUNT_ID);
    }

    @GetMapping("/trend/{biomarkerName}")
    @Operation(summary = "Get the trend for one biomarker",
            description = "Returns every imported reading for this exact biomarker name, chronological (oldest first). Empty list if none exist — never a fabricated series.")
    public List<BiomarkerReading> getTrend(@PathVariable String biomarkerName) {
        return readingRepository.findTrend(DEFAULT_ACCOUNT_ID, biomarkerName);
    }

    @GetMapping("/reference")
    @Operation(summary = "Get the biomarker reference catalog",
            description = "Static, shipped-with-the-app catalog of biomarker names with category and a plain-language description, for display labeling. Carries no numeric reference range — see BiomarkerReferenceCatalog for why.")
    public List<BiomarkerReferenceEntry> getReference() {
        return BiomarkerReferenceCatalog.all();
    }
}
