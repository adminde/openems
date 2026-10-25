package io.openems.edge.heat.pump.core;

/**
 * Estimates the live Coefficient of Performance (COP) of a Heat Pump.
 *
 * <p>
 * Many Heat Pumps do not report their current COP as telemetry. This estimator
 * derives it from a single nominal rating point (`refCop` at `refSinkTemp` and
 * full load) plus two influences:
 *
 * <ul>
 * <li>a Carnot-based temperature term: the COP drops as the sink (flow/buffer)
 * temperature rises above the rating point. The implicit source temperature is
 * derived once from the rating point and an assumed quality grade
 * ({@link #DEFAULT_QUALITY_GRADE}).
 * <li>a part-load term: modulating compressors reach their best efficiency in
 * part load and are least efficient at full load, see
 * {@link #partLoadFactor(double)}.
 * </ul>
 *
 * <p>
 * Both terms are normalized to 1.0 at the rating point (full load, reference
 * sink temperature), so {@code estimateCop(refSinkTemp, 1.0) == refCop}.
 */
public final class HeatPumpPerformanceEstimator {

	/**
	 * Assumed quality grade (Gütegrad) relating the real COP to the theoretical
	 * Carnot COP. A typical value for a well-designed compression Heat Pump.
	 */
	public static final double DEFAULT_QUALITY_GRADE = 0.45;

	/**
	 * Sink (flow) temperature of the standard rating point in [°C], as used by
	 * datasheet ratings like A2/W35 or B0/W35.
	 */
	public static final double STANDARD_RATING_SINK_TEMPERATURE = 35.0;

	private static final double KELVIN_OFFSET = 273.15;

	private final double refCop;
	private final double qualityGrade;
	private final double sourceTempKelvin;

	private HeatPumpPerformanceEstimator(double refCop, double refSinkTempCelsius, double qualityGrade) {
		this.refCop = refCop;
		this.qualityGrade = qualityGrade;
		var refSinkKelvin = refSinkTempCelsius + KELVIN_OFFSET;
		// Derive the implicit source temperature from the rating point:
		// refCop = qualityGrade * Tsink / (Tsink - Tsource).
		this.sourceTempKelvin = refSinkKelvin * (1.0 - qualityGrade / refCop);
	}

	/**
	 * Creates an estimator for a COP rated at the standard rating point
	 * {@link #STANDARD_RATING_SINK_TEMPERATURE} (W35), using the
	 * {@link #DEFAULT_QUALITY_GRADE}.
	 *
	 * @param refCop the nominal COP at the standard rating point
	 * @return the estimator
	 */
	public static HeatPumpPerformanceEstimator of(double refCop) {
		return of(refCop, STANDARD_RATING_SINK_TEMPERATURE, DEFAULT_QUALITY_GRADE);
	}

	/**
	 * Creates an estimator using the {@link #DEFAULT_QUALITY_GRADE}.
	 *
	 * @param refCop            the nominal COP at the rating point
	 * @param refSinkTempCelsius the sink (flow/buffer) temperature of the rating
	 *                          point in [°C]
	 * @return the estimator
	 */
	public static HeatPumpPerformanceEstimator of(double refCop, double refSinkTempCelsius) {
		return of(refCop, refSinkTempCelsius, DEFAULT_QUALITY_GRADE);
	}

	/**
	 * Creates an estimator with an explicit quality grade.
	 *
	 * @param refCop            the nominal COP at the rating point
	 * @param refSinkTempCelsius the sink (flow/buffer) temperature of the rating
	 *                          point in [°C]
	 * @param qualityGrade      the assumed quality grade (Gütegrad), 0..1
	 * @return the estimator
	 */
	public static HeatPumpPerformanceEstimator of(double refCop, double refSinkTempCelsius, double qualityGrade) {
		return new HeatPumpPerformanceEstimator(refCop, refSinkTempCelsius, qualityGrade);
	}

	/**
	 * Estimates the COP at the given sink temperature and part-load ratio.
	 *
	 * @param sinkTempCelsius the current sink (flow/buffer) temperature in [°C]
	 * @param partLoadRatio   the electrical part-load ratio (P_el / P_el,nominal),
	 *                        clamped to 0..1
	 * @return the estimated COP, never below 1.0
	 */
	public double estimateCop(double sinkTempCelsius, double partLoadRatio) {
		var cop = this.refCop * this.temperatureFactor(sinkTempCelsius) * partLoadFactor(partLoadRatio);
		return Math.max(1.0, cop);
	}

	private double temperatureFactor(double sinkTempCelsius) {
		var sinkKelvin = sinkTempCelsius + KELVIN_OFFSET;
		// Guard against a non-positive temperature lift (sink at or below source).
		var lift = Math.max(1.0, sinkKelvin - this.sourceTempKelvin);
		var carnotCop = this.qualityGrade * sinkKelvin / lift;
		return carnotCop / this.refCop;
	}

	/**
	 * Part-load factor: a downward parabola peaking at part-load ratio 0.5 (≈ 1.08)
	 * and normalized to 1.0 at full load. Models the inverter benefit — modulating
	 * compressors are more efficient in part load than at full load.
	 *
	 * @param partLoadRatio the part-load ratio, clamped to 0..1
	 * @return the part-load factor
	 */
	private static double partLoadFactor(double partLoadRatio) {
		var p = Math.max(0.0, Math.min(1.0, partLoadRatio));
		return -0.32 * p * p + 0.32 * p + 1.0;
	}
}
