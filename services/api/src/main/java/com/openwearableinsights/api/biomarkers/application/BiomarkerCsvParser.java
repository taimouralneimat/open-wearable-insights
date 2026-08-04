package com.openwearableinsights.api.biomarkers.application;

import com.openwearableinsights.api.biomarkers.domain.BiomarkerCsvParseResult;
import com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a biomarker CSV: {@code date,biomarker_name,value,unit}, with
 * optional {@code reference_low}, {@code reference_high}, {@code category}
 * columns, in any order.
 *
 * <p>Stateless and dependency-free by design — usable directly from {@code
 * ingestion.application.DryRunValidator} (dry-run record counting/row
 * validation) and {@code ingestion.application.ImportService} (real
 * persistence) without either needing a Spring-managed instance, the same
 * way {@code DryRunValidator}'s existing generic JSON/CSV counters are
 * plain static-ish helpers.
 *
 * <p>Per-row failures (bad date, non-numeric value, missing required field)
 * are collected as row errors and that row is skipped — one bad line never
 * fails the whole file, matching {@code DryRunValidator}'s existing
 * "unsupported records" discipline for other formats.
 */
public final class BiomarkerCsvParser {

    private BiomarkerCsvParser() {}

    private static final List<String> REQUIRED_COLUMNS = List.of("date", "biomarker_name", "value", "unit");

    /**
     * Whether this file's header row identifies it as a biomarker CSV
     * (contains every required column, in any order). Used by {@code
     * DryRunValidator} to route CSV files to this parser instead of the
     * generic time/metric_type CSV counter.
     */
    public static boolean looksLikeBiomarkerCsv(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        String headerLine = firstNonBlank(lines);
        if (headerLine == null) {
            return false;
        }
        Map<String, Integer> header = parseHeader(headerLine);
        return header.keySet().containsAll(REQUIRED_COLUMNS);
    }

    /**
     * Parses every data row. Assumes {@link #looksLikeBiomarkerCsv} was
     * already checked true for these lines.
     */
    public static BiomarkerCsvParseResult parse(List<String> lines) {
        List<ParsedBiomarkerRow> rows = new ArrayList<>();
        List<String> rowErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (lines == null || lines.isEmpty()) {
            return new BiomarkerCsvParseResult(rows, rowErrors, warnings);
        }

        int headerIndex = -1;
        Map<String, Integer> header = null;
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.trim().isEmpty()) {
                continue;
            }
            header = parseHeader(line);
            headerIndex = lineNumber;
            break;
        }
        if (header == null) {
            return new BiomarkerCsvParseResult(rows, rowErrors, warnings);
        }

        Integer dateIdx = header.get("date");
        Integer nameIdx = header.get("biomarker_name");
        Integer valueIdx = header.get("value");
        Integer unitIdx = header.get("unit");
        Integer refLowIdx = header.get("reference_low");
        Integer refHighIdx = header.get("reference_high");
        Integer categoryIdx = header.get("category");

        for (int i = headerIndex; i < lines.size(); i++) {
            String line = lines.get(i);
            int rowNumber = i + 1;
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] fields = line.split(",", -1);
            if (fields.length <= maxIndex(dateIdx, nameIdx, valueIdx, unitIdx)) {
                rowErrors.add("Row " + rowNumber + ": expected at least "
                        + (maxIndex(dateIdx, nameIdx, valueIdx, unitIdx) + 1)
                        + " fields, found " + fields.length);
                continue;
            }

            String dateRaw = fields[dateIdx].trim();
            String nameRaw = fields[nameIdx].trim();
            String valueRaw = fields[valueIdx].trim();
            String unitRaw = fields[unitIdx].trim();

            LocalDate readingDate;
            try {
                readingDate = LocalDate.parse(dateRaw);
            } catch (DateTimeParseException e) {
                rowErrors.add("Row " + rowNumber + ": invalid date '" + dateRaw + "' (expected yyyy-MM-dd)");
                continue;
            }

            if (nameRaw.isEmpty()) {
                rowErrors.add("Row " + rowNumber + ": biomarker_name is blank");
                continue;
            }

            double value;
            try {
                value = Double.parseDouble(valueRaw);
            } catch (NumberFormatException e) {
                rowErrors.add("Row " + rowNumber + ": invalid numeric value '" + valueRaw + "'");
                continue;
            }

            if (unitRaw.isEmpty()) {
                rowErrors.add("Row " + rowNumber + ": unit is blank");
                continue;
            }

            Double referenceLow = parseOptionalDouble(fields, refLowIdx, rowNumber, "reference_low", warnings);
            Double referenceHigh = parseOptionalDouble(fields, refHighIdx, rowNumber, "reference_high", warnings);

            String category = parseOptionalString(fields, categoryIdx);
            if (category == null) {
                category = BiomarkerReferenceCatalog.lookup(nameRaw).map(e -> e.category()).orElse(null);
            }

            rows.add(new ParsedBiomarkerRow(readingDate, nameRaw, value, unitRaw, referenceLow, referenceHigh, category));
        }

        return new BiomarkerCsvParseResult(rows, rowErrors, warnings);
    }

    private static String firstNonBlank(List<String> lines) {
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }

    private static Map<String, Integer> parseHeader(String headerLine) {
        String[] cols = headerLine.split(",", -1);
        Map<String, Integer> header = new LinkedHashMap<>();
        for (int i = 0; i < cols.length; i++) {
            header.put(cols[i].trim().toLowerCase(), i);
        }
        return header;
    }

    private static int maxIndex(int... indices) {
        int max = 0;
        for (int idx : indices) {
            max = Math.max(max, idx);
        }
        return max;
    }

    private static Double parseOptionalDouble(String[] fields, Integer idx, int rowNumber, String columnName, List<String> warnings) {
        if (idx == null || idx >= fields.length) {
            return null;
        }
        String raw = fields[idx].trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            warnings.add("Row " + rowNumber + ": ignoring unparseable " + columnName + " '" + raw + "'");
            return null;
        }
    }

    private static String parseOptionalString(String[] fields, Integer idx) {
        if (idx == null || idx >= fields.length) {
            return null;
        }
        String raw = fields[idx].trim();
        return raw.isEmpty() ? null : raw;
    }
}
