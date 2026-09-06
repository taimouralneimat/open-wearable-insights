/**
 * Module: milestones
 *
 * <p>An original, non-parity delight feature: "Personal Records / Milestones"
 * surfaces the honest moments where an account's own current computed value
 * genuinely beats (or ties) its own real historical values on something this
 * app already tracks — e.g. a new longest habit streak, a best-ever tracked
 * week of steps, a new highest VO2max estimate, a new most-active strength
 * training period. Never a fabricated or inferred "record": every milestone
 * is a real comparison between two real numbers this app already computed
 * elsewhere, following this app's "never fabricate, say so honestly"
 * philosophy (see {@code Vo2MaxService}/{@code HealthspanService}/{@code
 * StrengthTrainingService}). An empty list is the honest, expected result
 * when nothing genuinely new has happened recently — never filled with
 * generic praise.
 *
 * <p><b>New module, not a bolt-on:</b> like {@code healthspan} (row 24) and
 * {@code monthlyreport} (row 30), this composes real, already-computed
 * outputs from several other modules ({@code journal}, {@code activities},
 * {@code vo2max}, {@code strength}) into its own original cross-domain
 * surface, rather than extending any one of those modules with a concern
 * that spans all of them.
 *
 * <p><b>Stateless, computed fresh on every request:</b> like {@code
 * HealthspanService}/{@code TrainingLoadService}, there is no persistence of
 * past milestones — every check re-derives "is this genuinely a new best"
 * from the other modules' own current query methods. This does mean a
 * milestone can appear on more than one request while its underlying
 * condition remains true (e.g. every day an account is still riding its own
 * longest-ever habit streak) — see {@code MilestoneService}'s class Javadoc
 * for why this is an accepted, honestly-documented tradeoff for a first
 * version rather than a reason to add a new "already shown" table.
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 */
package com.openwearableinsights.api.milestones;
