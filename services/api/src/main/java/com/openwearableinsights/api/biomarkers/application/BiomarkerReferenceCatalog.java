package com.openwearableinsights.api.biomarkers.application;

import com.openwearableinsights.api.biomarkers.domain.BiomarkerReferenceEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Static, shipped-with-the-app reference catalog of biomarkers clinically
 * relevant to a fitness/recovery context — display labeling (category +
 * plain-language description) only, sourced from general, well-established
 * clinical-chemistry knowledge, not attributed to or copied from any single
 * vendor's panel or marketing copy.
 *
 * <p>Used two ways:
 * <ul>
 *   <li>{@link BiomarkerCsvParser} looks a row's {@code biomarker_name} up
 *       here (case-insensitive) to auto-fill {@code category} when the CSV
 *       itself doesn't supply one — a real lookup against shipped reference
 *       data, not a fabricated value.</li>
 *   <li>{@code GET /api/v1/biomarkers/reference} returns the full list so
 *       the Flutter UI can show what a name means, even before any reading
 *       for it has been imported.</li>
 * </ul>
 *
 * <p><strong>Deliberately carries no numeric reference range.</strong> A
 * "normal range" varies by lab, assay, sex, age, and population, and this
 * app has no way to know which convention applies to any given user's
 * result. Per this project's data-honesty rule, the only reference range
 * ever shown for a reading is the one that came from that reading's own CSV
 * row (see {@code biomarker_readings.reference_low/high} in the V08
 * migration) — never a range asserted here.
 *
 * <p>The CSV parser accepts any biomarker name, not just these — this
 * catalog only seeds display metadata for the names it recognizes.
 */
public final class BiomarkerReferenceCatalog {

    private BiomarkerReferenceCatalog() {}

    public static final String LIPID_CARDIOVASCULAR = "Lipid/Cardiovascular";
    public static final String METABOLIC_GLUCOSE = "Metabolic/Glucose";
    public static final String HORMONES = "Hormones";
    public static final String VITAMINS_MINERALS = "Vitamins/Minerals";
    public static final String INFLAMMATION = "Inflammation";
    public static final String BLOOD_COUNT = "Blood Count";
    public static final String KIDNEY = "Kidney";
    public static final String LIVER = "Liver";
    public static final String ELECTROLYTES = "Electrolytes";

    private static final List<BiomarkerReferenceEntry> ENTRIES = List.of(
            // Lipid/Cardiovascular
            new BiomarkerReferenceEntry("Apolipoprotein B (ApoB)", LIPID_CARDIOVASCULAR,
                    "Counts the number of cholesterol-carrying particles most linked to artery plaque buildup."),
            new BiomarkerReferenceEntry("Cholesterol/HDL Ratio", LIPID_CARDIOVASCULAR,
                    "Total cholesterol divided by HDL — a simple ratio some clinicians use alongside individual lipid values."),
            new BiomarkerReferenceEntry("HDL Cholesterol", LIPID_CARDIOVASCULAR,
                    "The cholesterol carried by particles that help clear excess cholesterol from the bloodstream."),
            new BiomarkerReferenceEntry("LDL Cholesterol", LIPID_CARDIOVASCULAR,
                    "The cholesterol carried by particles most associated with cardiovascular risk when elevated."),
            new BiomarkerReferenceEntry("Lipoprotein(a)", LIPID_CARDIOVASCULAR,
                    "A genetically-determined lipid particle; elevated levels are an independent cardiovascular risk marker."),
            new BiomarkerReferenceEntry("Non-HDL Cholesterol", LIPID_CARDIOVASCULAR,
                    "Total cholesterol minus HDL — every cholesterol type considered artery-damaging, in one number."),
            new BiomarkerReferenceEntry("Total Cholesterol", LIPID_CARDIOVASCULAR,
                    "The sum of all cholesterol carried in the blood, across every particle type."),
            new BiomarkerReferenceEntry("Triglycerides", LIPID_CARDIOVASCULAR,
                    "A blood fat used for energy storage; levels rise with excess calories, sugar, or alcohol intake."),
            new BiomarkerReferenceEntry("Omega-3 Index", LIPID_CARDIOVASCULAR,
                    "The share of omega-3 fatty acids in red blood cell membranes, reflecting recent dietary intake."),

            // Metabolic/Glucose
            new BiomarkerReferenceEntry("Glucose", METABOLIC_GLUCOSE,
                    "Blood sugar at the moment of the draw — the body's primary short-term energy fuel."),
            new BiomarkerReferenceEntry("HOMA-IR Score", METABOLIC_GLUCOSE,
                    "An estimate of insulin resistance calculated from fasting glucose and insulin together."),
            new BiomarkerReferenceEntry("Insulin", METABOLIC_GLUCOSE,
                    "The hormone that moves glucose out of the blood and into cells."),
            new BiomarkerReferenceEntry("Hemoglobin A1c (HbA1c)", METABOLIC_GLUCOSE,
                    "Average blood sugar over roughly the last three months, estimated from sugar bound to red blood cells."),

            // Hormones
            new BiomarkerReferenceEntry("Cortisol", HORMONES,
                    "The primary stress hormone; also follows a daily rhythm tied to waking and sleep."),
            new BiomarkerReferenceEntry("DHEA-S", HORMONES,
                    "A precursor hormone the adrenal glands produce, used downstream to make testosterone and estrogen."),
            new BiomarkerReferenceEntry("Estradiol", HORMONES,
                    "The primary form of estrogen, present in and physiologically relevant to all sexes."),
            new BiomarkerReferenceEntry("FSH", HORMONES,
                    "Follicle-stimulating hormone — a pituitary signal involved in reproductive-hormone regulation."),
            new BiomarkerReferenceEntry("Free Testosterone", HORMONES,
                    "The portion of testosterone not bound to carrier proteins, available for the body to use directly."),
            new BiomarkerReferenceEntry("LH", HORMONES,
                    "Luteinizing hormone — a pituitary signal that triggers testosterone/estrogen production."),
            new BiomarkerReferenceEntry("SHBG", HORMONES,
                    "Sex hormone-binding globulin — the carrier protein that determines how much testosterone/estrogen circulates free vs. bound."),
            new BiomarkerReferenceEntry("Testosterone", HORMONES,
                    "Total testosterone — bound plus free — a hormone relevant to muscle, bone, and recovery in all sexes."),
            new BiomarkerReferenceEntry("TSH", HORMONES,
                    "Thyroid-stimulating hormone — the pituitary's signal that drives thyroid hormone production, used to screen thyroid function."),

            // Vitamins/Minerals
            new BiomarkerReferenceEntry("Calcium", VITAMINS_MINERALS,
                    "A mineral essential for bone strength, muscle contraction, and nerve signaling."),
            new BiomarkerReferenceEntry("Iron", VITAMINS_MINERALS,
                    "A mineral required to build hemoglobin, the protein that carries oxygen in red blood cells."),
            new BiomarkerReferenceEntry("Iron Saturation", VITAMINS_MINERALS,
                    "The share of the blood's iron-carrying capacity currently occupied by iron."),
            new BiomarkerReferenceEntry("Magnesium", VITAMINS_MINERALS,
                    "A mineral involved in hundreds of enzymatic reactions, including muscle and nerve function."),
            new BiomarkerReferenceEntry("Potassium", VITAMINS_MINERALS,
                    "An electrolyte critical for heart rhythm, muscle contraction, and fluid balance."),
            new BiomarkerReferenceEntry("Sodium", VITAMINS_MINERALS,
                    "The primary electrolyte governing fluid balance and blood pressure regulation."),
            new BiomarkerReferenceEntry("Vitamin D", VITAMINS_MINERALS,
                    "A hormone-like vitamin, made from sun exposure and diet, important for bone and immune health."),

            // Inflammation
            new BiomarkerReferenceEntry("hs-CRP", INFLAMMATION,
                    "High-sensitivity C-reactive protein — a sensitive marker of low-grade systemic inflammation."),
            new BiomarkerReferenceEntry("Homocysteine", INFLAMMATION,
                    "An amino acid byproduct; elevated levels are linked to cardiovascular and vitamin-status concerns."),

            // Blood Count
            new BiomarkerReferenceEntry("Basophil %", BLOOD_COUNT,
                    "The share of white blood cells that are basophils, involved in allergic and inflammatory responses."),
            new BiomarkerReferenceEntry("Basophils", BLOOD_COUNT,
                    "The absolute count of basophils, a white blood cell type involved in allergic and inflammatory responses."),
            new BiomarkerReferenceEntry("Eosinophil %", BLOOD_COUNT,
                    "The share of white blood cells that are eosinophils, involved in allergic responses and parasite defense."),
            new BiomarkerReferenceEntry("Eosinophils", BLOOD_COUNT,
                    "The absolute count of eosinophils, a white blood cell type involved in allergic responses and parasite defense."),
            new BiomarkerReferenceEntry("Hematocrit", BLOOD_COUNT,
                    "The share of blood volume made up of red blood cells."),
            new BiomarkerReferenceEntry("Hemoglobin", BLOOD_COUNT,
                    "The oxygen-carrying protein inside red blood cells."),
            new BiomarkerReferenceEntry("Lymphocyte %", BLOOD_COUNT,
                    "The share of white blood cells that are lymphocytes, core cells of the immune system."),
            new BiomarkerReferenceEntry("Lymphocytes", BLOOD_COUNT,
                    "The absolute count of lymphocytes, core cells of the immune system."),
            new BiomarkerReferenceEntry("MCH", BLOOD_COUNT,
                    "Mean corpuscular hemoglobin — the average amount of hemoglobin per red blood cell."),
            new BiomarkerReferenceEntry("MCHC", BLOOD_COUNT,
                    "Mean corpuscular hemoglobin concentration — the average hemoglobin concentration within red blood cells."),
            new BiomarkerReferenceEntry("MCV", BLOOD_COUNT,
                    "Mean corpuscular volume — the average size of red blood cells, used to classify anemia types."),
            new BiomarkerReferenceEntry("MPV", BLOOD_COUNT,
                    "Mean platelet volume — the average size of platelets, the cells responsible for clotting."),
            new BiomarkerReferenceEntry("Monocytes %", BLOOD_COUNT,
                    "The share of white blood cells that are monocytes, which mature into tissue immune cells."),
            new BiomarkerReferenceEntry("Neutrophil %", BLOOD_COUNT,
                    "The share of white blood cells that are neutrophils, the first responders to infection."),
            new BiomarkerReferenceEntry("Platelets", BLOOD_COUNT,
                    "Cell fragments responsible for blood clotting."),
            new BiomarkerReferenceEntry("RBC", BLOOD_COUNT,
                    "Red blood cell count — the number of oxygen-carrying cells per volume of blood."),
            new BiomarkerReferenceEntry("RDW", BLOOD_COUNT,
                    "Red cell distribution width — how much red blood cell sizes vary, used alongside MCV to classify anemia."),
            new BiomarkerReferenceEntry("WBC", BLOOD_COUNT,
                    "White blood cell count — the body's total immune cell count."),

            // Kidney
            new BiomarkerReferenceEntry("BUN", KIDNEY,
                    "Blood urea nitrogen — a waste product filtered by the kidneys, used to assess kidney function."),
            new BiomarkerReferenceEntry("BUN/Creatinine Ratio", KIDNEY,
                    "A ratio of two kidney-filtered waste markers, used alongside each individually to interpret kidney function."),
            new BiomarkerReferenceEntry("Creatinine", KIDNEY,
                    "A muscle-metabolism waste product filtered by the kidneys, the standard basis for estimating kidney function."),
            new BiomarkerReferenceEntry("eGFR", KIDNEY,
                    "Estimated glomerular filtration rate — an estimate of how well the kidneys are filtering blood."),

            // Liver
            new BiomarkerReferenceEntry("ALP", LIVER,
                    "Alkaline phosphatase — an enzyme from liver and bone, used to screen for liver or bone conditions."),
            new BiomarkerReferenceEntry("ALT", LIVER,
                    "Alanine aminotransferase — a liver enzyme released into the blood when liver cells are stressed or damaged."),
            new BiomarkerReferenceEntry("Albumin", LIVER,
                    "The main protein made by the liver, also reflecting nutritional status."),
            new BiomarkerReferenceEntry("Albumin/Globulin Ratio", LIVER,
                    "The ratio of the two major blood protein groups, used alongside each individually."),
            new BiomarkerReferenceEntry("AST", LIVER,
                    "Aspartate aminotransferase — an enzyme found in liver and muscle, released when either is stressed or damaged."),
            new BiomarkerReferenceEntry("Globulin (Calculated)", LIVER,
                    "The non-albumin blood proteins, mostly immune-related, calculated as total protein minus albumin."),
            new BiomarkerReferenceEntry("Total Bilirubin", LIVER,
                    "A byproduct of red blood cell breakdown, processed by the liver."),
            new BiomarkerReferenceEntry("Total Protein", LIVER,
                    "The combined amount of albumin and globulin in the blood."),

            // Electrolytes
            new BiomarkerReferenceEntry("Carbon Dioxide", ELECTROLYTES,
                    "Reflects the blood's bicarbonate level, part of the body's acid-base balance."),
            new BiomarkerReferenceEntry("Chloride", ELECTROLYTES,
                    "An electrolyte that works with sodium to maintain fluid balance and blood pH.")
    );

    private static final Map<String, BiomarkerReferenceEntry> BY_NORMALIZED_NAME =
            ENTRIES.stream().collect(Collectors.toMap(
                    e -> normalize(e.name()), e -> e, (a, b) -> a));

    /** Every seeded reference entry, in catalog order. */
    public static List<BiomarkerReferenceEntry> all() {
        return ENTRIES;
    }

    /** Case-insensitive, whitespace-trimmed lookup by biomarker name. */
    public static Optional<BiomarkerReferenceEntry> lookup(String biomarkerName) {
        if (biomarkerName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NORMALIZED_NAME.get(normalize(biomarkerName)));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase();
    }
}
