# Competitive Parity Matrix

> This matrix uses **only public information** from official documentation,
> lawful user-provided exports, and observations entered by the user. It does
> not copy proprietary text, UI designs, or algorithms, and does not claim exact
> formula parity. Updated during every relevant release.

**Date verified**: 2026-07-18 (Phase 0 baseline)

## Legend

- **Explainability**: 🟢 full inputs/provenance exposed · 🟡 partial · 🔴 black-box
- **Status**: ☐ planned · 🟡 in progress · ✅ shipped

## Matrix

| # | Product area | Competitor capability | Public evidence source | Date verified | Garmin data required | In Garmin export | Via official Garmin API | Our capability | Our UI surface | Calculation method | Explainability | Status | Gap | Priority | Acceptance criteria | Legal/dependency constraint |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Recovery/readiness | Daily recovery score with strain recommendation | WHOOP public site/help | 2026-07-18 | HRV, RHR, sleep, stress | Partial | Yes (Health API) | Versioned readiness score with factor contributions | Readiness dashboard | Deterministic weighted deviation from personal baseline | 🟢 | 🟡 | Original methodology, not vendor parity | High | Score exposes inputs, provenance, baseline, factors, confidence, algo version, limitations | None |
| 2 | Strain | Daily & activity strain | WHOOP public site/help | 2026-07-18 | HR zones, activity duration | Partial | Yes | Transparent internal training-load model | Training-load view | HR-zone load + TRIMP variants + ACWR | 🟢 | ☐ | Need activity parsing | High | Load values normalized with provenance; no incomparable mixing | None |
| 3 | Sleep | Sleep stages, duration, efficiency, need | WHOOP & Garmin public docs | 2026-07-18 | Sleep sessions, stages | Yes | Yes | Sleep summary + personal baseline | Sleep view | Stage breakdown + consistency + debt estimate | 🟢 | 🟡 | Sleep-need estimate methodology | Medium | Duration, time-in-bed, stages, consistency, data-quality warnings | None |
| 4 | Sleep need | Personalized sleep need | WHOOP public help | 2026-07-18 | Sleep history, baseline | Partial | No | Sleep-need estimate vs. actual | Sleep view | Rolling baseline + deficit accumulation | 🟡 | ☐ | Methodology to document | Medium | Shows need, actual, deficit, sample size | None |
| 5 | Journal | Behavioral logging | WHOOP public help | 2026-07-18 | User input | N/A | N/A | Journal entries (caffeine, meals, travel, alcohol, RPE, soreness) | Journal view | User-selected behaviors | 🟢 | ☐ | Phase 3 | Medium | Entries stored with provenance; treated as untrusted input | None |
| 6 | Behavioral correlations | Behavior↔outcome correlations | WHOOP public help | 2026-07-18 | Journal + outcomes | N/A | N/A | Exploratory correlations with sample count & uncertainty | Correlations view | Statistical correlation with min sample size, no causation claims | 🟢 | ☐ | Phase 3 | Medium | Sample count, effect direction, uncertainty, "not causation" caution | None |
| 7 | Daily coaching | "Why is my readiness low?" | WHOOP public help | 2026-07-18 | Readiness factors | Derived | Derived | Grounded coach citing actual metrics | Coach view | LLM explains computed results; deterministic fallback | 🟢 | ☐ | Phase 3 | High | Answer cites metrics + provenance; data-quality limits explicit | Local LLM only |
| 8 | Weekly/monthly trends | Trend charts | WHOOP & Garmin public docs | 2026-07-18 | Time-series | Yes | Yes | Trend views with baselines | Trends view | Rolling baselines + deviation bands | 🟢 | ☐ | Phase 2-3 | Medium | Trends show baseline, deviation, data completeness | None |
| 9 | Long-term health trends | Multi-month views | Garmin public docs | 2026-07-18 | Time-series | Yes | Yes | Long-term trend views | Trends view | Aggregated time-series | 🟢 | ☐ | Phase 3 | Low | Long-term view with data-quality warnings | None |
| 10 | Activity analysis | Activity breakdown | Garmin public docs | 2026-07-18 | Activities, FIT | Yes | Yes | Activity list + detail | Activities view | Parsed from FIT/CSV | 🟢 | ☐ | Phase 2 | Medium | Activity type, duration, distance, pace, HR zones | FIT SDK license |
| 11 | Training recommendations | Hard/easy/rest recommendation | WHOOP public help | 2026-07-18 | Readiness, training load | Derived | Derived | Recommendation with supporting factors | Coach view | Rule-based on readiness + ACWR | 🟢 | ☐ | Phase 3 | High | Recommendation cites factors; not medical advice | None |
| 12 | Data quality | Data-completeness warnings | General | 2026-07-18 | All | N/A | N/A | Confidence score + stale-data warnings | All data views | Completeness + freshness checks | 🟢 | 🟡 | Phase 1 provisional | High | Every score shows confidence + data-quality level | None |
| 13 | User goals | Goal setting | General | 2026-07-18 | User input | N/A | N/A | Goals & preferences | Settings | User-configured | 🟢 | ☐ | Phase 3 | Low | Goals influence coaching | None |
| 14 | Export | Data export | GDPR/general | 2026-07-18 | All user data | N/A | N/A | Complete local export | Privacy → Export | Full data dump | 🟢 | ☐ | Phase 1-2 | High | Export is complete and local | None |
| 15 | Privacy | Local-first, no telemetry | General | 2026-07-18 | N/A | N/A | N/A | Loopback-only, no hosted telemetry | Settings | Architecture-enforced | 🟢 | 🟡 | Phase 0-1 | Critical | No health data leaves machine unless explicit | None |
| 16 | Mobile experience | iOS/Android app | WHOOP/Garmin public | 2026-07-18 | All | N/A | N/A | Flutter iOS/Android | Mobile app | Shared Flutter codebase | 🟢 | ☐ | Phase 4 | Medium | Mobile builds with secure storage | None |
| 17 | Watch experience | Watch app/widget | Garmin Connect IQ | 2026-07-18 | Readiness, sleep, factors | Derived | N/A | Connect IQ app | Watch | Syncs with backend | 🟢 | ☐ | Phase 5 | Low | Shows readiness, sleep, factors, sync time, data-quality warning | Connect IQ SDK |

## Notes

- This matrix is a living document. Update during every relevant release.
- "Our capability" describes original methodology, not vendor parity.
- Explainability is a core differentiator: every score exposes inputs and provenance.
- No proprietary formula, UI, or text is copied.