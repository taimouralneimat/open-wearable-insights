/**
 * Module: monthlyreport
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 *
 * <p>Backs docs/product/parity-matrix.md row 30 ("Monthly/longitudinal
 * performance assessment"). A new module rather than an addition to {@code
 * readiness} because, like {@code healthspan} (row 24), this composes real
 * already-computed outputs from three other modules -- readiness-score
 * history, training load, and sleep trends -- into an original, single
 * cross-domain report, rather than extending readiness's own single-day
 * score. Mirrors {@code healthspan}'s composite-module shape (own
 * module/application/domain/adapter split, no persistence of its own, no
 * scheduled job -- computed fresh on every request from data other modules
 * already own).
 *
 * <p>Not a reproduction of any vendor's proprietary monthly-report design or
 * scoring -- see {@code
 * monthlyreport.application.MonthlyPerformanceReportService}'s class Javadoc
 * for the full, disclosed methodology.
 */
package com.openwearableinsights.api.monthlyreport;
