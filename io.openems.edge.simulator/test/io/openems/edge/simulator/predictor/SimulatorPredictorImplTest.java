package io.openems.edge.simulator.predictor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.predictor.api.prediction.LogVerbosity;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;
import io.openems.edge.simulator.datasource.csv.direct.SimulatorDatasourceCsvDirectImplTest;

public class SimulatorPredictorImplTest {

	private static final ChannelAddress SUM_PRODUCTION = new ChannelAddress("_sum", "ProductionActivePower");

	@Test
	void test() throws OpenemsException, Exception {
		final var clock = new TimeLeapClock(Instant.ofEpochSecond(946684800), ZoneId.of("UTC"));
		final var datasource = SimulatorDatasourceCsvDirectImplTest.create("datasource0", """
				10
				20
				30
				40
				""");
		final var sut = new SimulatorPredictorImpl();
		new ComponentTest(sut) //
				.addReference("datasource", datasource) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId("predictor0") //
						.setDatasourceId("datasource0") //
						.setChannelAddresses(SUM_PRODUCTION.toString()) //
						.setLogVerbosity(LogVerbosity.REQUESTED_PREDICTIONS) //
						.build()) //
				.deactivate();

		var p = sut.createNewPrediction(SUM_PRODUCTION);
		assertEquals(192, p.asArray().length);
		assertEquals(Integer.valueOf(20), p.asArray()[0]);
		assertEquals(Integer.valueOf(23), p.asArray()[1]);
		assertEquals(Integer.valueOf(27), p.asArray()[2]);
		assertEquals(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant(), p.getFirstTime());
	}

	/** A Datasource that hands out fixed values that lie ahead, timeDelta seconds apart. */
	private static final class FixedDatasource implements SimulatorDatasource {

		private final List<Integer> values;
		private final int timeDelta;

		/** What the Predictor asked for, so the test can assert on the horizon. */
		private final List<Integer> requestedCounts = new ArrayList<>();

		private FixedDatasource(List<Integer> values, int timeDelta) {
			this.values = values;
			this.timeDelta = timeDelta;
		}

		@Override
		public Set<String> getKeys() {
			return Set.of();
		}

		@Override
		public int getTimeDelta() {
			return this.timeDelta;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> List<T> getValues(OpenemsType type, ChannelAddress channelAddress) {
			return (List<T>) this.values;
		}

		@Override
		public <T> List<T> getNextValues(OpenemsType type, ChannelAddress channelAddress, int count) {
			this.requestedCounts.add(count);
			return SimulatorDatasource.super.getNextValues(type, channelAddress, count);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue(OpenemsType type, ChannelAddress channelAddress) {
			return (T) this.values.get(0);
		}
	}

	private static SimulatorPredictorImpl createWith(SimulatorDatasource datasource, TimeLeapClock clock)
			throws Exception {
		final var sut = new SimulatorPredictorImpl();
		new ComponentTest(sut) //
				.addReference("datasource", datasource) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(MyConfig.create() //
						.setId("predictor0") //
						.setDatasourceId("datasource0") //
						.setChannelAddresses(SUM_PRODUCTION.toString()) //
						.setLogVerbosity(LogVerbosity.NONE) //
						.build());
		return sut;
	}

	@Test
	void quarterHourlyValuesStartAtTheCurrentQuarterHour() throws Exception {
		// 10:07 -> the prediction starts at 10:00 and plays the source one to one
		final var clock = new TimeLeapClock(
				ZonedDateTime.of(2025, 6, 1, 10, 7, 0, 0, ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));
		var sut = createWith(new FixedDatasource(List.of(100, 200, 300), 900), clock);

		var p = sut.createNewPrediction(SUM_PRODUCTION);

		assertEquals(ZonedDateTime.of(2025, 6, 1, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant(), p.getFirstTime());
		assertEquals(192, p.asArray().length);
		assertEquals(Integer.valueOf(100), p.asArray()[0]);
		assertEquals(Integer.valueOf(200), p.asArray()[1]);
		assertEquals(Integer.valueOf(300), p.asArray()[2]);
		assertEquals(Integer.valueOf(100), p.asArray()[3]); // short source is cycled
	}

	@Test
	void hourlyValuesAreRepeatedPerQuarterHour() throws Exception {
		final var clock = new TimeLeapClock(Instant.ofEpochSecond(946684800), ZoneId.of("UTC"));
		var sut = createWith(new FixedDatasource(List.of(10, 20), 3600), clock);

		var values = sut.createNewPrediction(SUM_PRODUCTION).asArray();

		assertArrayEquals(new Integer[] { 10, 10, 10, 10, 20, 20, 20, 20, 10 }, Arrays.copyOf(values, 9));
	}

	@Test
	void fiveMinuteValuesAreAveragedLikeBefore() {
		// three five-minute values per quarter hour, source cycled: (10,20,30) (40,10,20) (30,40,10)
		var values = SimulatorPredictorImpl.rasterize(List.of(10, 20, 30, 40), 300, 3);

		assertArrayEquals(new Integer[] { 20, 23, 27 }, values);
	}

	@Test
	void emptySourceGivesNoPrediction() throws Exception {
		final var clock = new TimeLeapClock(Instant.ofEpochSecond(946684800), ZoneId.of("UTC"));
		var sut = createWith(new FixedDatasource(List.of(), 900), clock);

		assertEquals(Prediction.EMPTY_PREDICTION, sut.createNewPrediction(SUM_PRODUCTION));
	}

	@Test
	void thePredictorAsksForItsOwnHorizon() throws Exception {
		final var clock = new TimeLeapClock(Instant.ofEpochSecond(946684800), ZoneId.of("UTC"));
		var quarterHourly = new FixedDatasource(List.of(1, 2), 900);
		createWith(quarterHourly, clock).createNewPrediction(SUM_PRODUCTION);
		// 48 hours of quarter hours
		assertEquals(List.of(192), quarterHourly.requestedCounts);

		var fiveMinutely = new FixedDatasource(List.of(1, 2), 300);
		createWith(fiveMinutely, clock).createNewPrediction(SUM_PRODUCTION);
		// the same 48 hours, three values per quarter hour
		assertEquals(List.of(576), fiveMinutely.requestedCounts);
	}
}
