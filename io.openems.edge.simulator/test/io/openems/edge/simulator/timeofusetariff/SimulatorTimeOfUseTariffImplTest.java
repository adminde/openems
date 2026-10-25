package io.openems.edge.simulator.timeofusetariff;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.simulator.datasource.csv.direct.SimulatorDatasourceCsvDirectImplTest;

public class SimulatorTimeOfUseTariffImplTest {

	/** The instant {@link io.openems.common.test.TestUtils#createDummyClock()} starts at. */
	private static final String CLOCK_START = "2020-01-01T00:00:00Z";

	private static final String PRICES = """
			Price
			100.0
			200.0
			300.0
			400.0
			""";

	private static SimulatorTimeOfUseTariffImpl create(Clock clock, MyConfig.Builder config) throws Exception {
		var datasource = SimulatorDatasourceCsvDirectImplTest.create("datasource0", PRICES);
		var test = new ComponentTest(new SimulatorTimeOfUseTariffImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("meta", new DummyMeta()) //
				.addReference("datasource", datasource) //
				.activate(config.build());
		return (SimulatorTimeOfUseTariffImpl) test.getSut();
	}

	private static MyConfig.Builder config() {
		return MyConfig.create() //
				.setId("timeOfUseTariff0") //
				.setDatasourceId("datasource0") //
				.setStartTime(CLOCK_START);
	}

	@Test
	void testServesTheColumnFromTheStartTime() throws Exception {
		var sut = create(createDummyClock(), config());

		assertArrayEquals(new Double[] { 100.0, 200.0, 300.0, 400.0 }, sut.getPrices().asArray());
	}

	@Test
	void testStartTimeAnchorsRowZero() throws Exception {
		var clock = createDummyClock();
		clock.leap(30, java.time.temporal.ChronoUnit.MINUTES);

		var sut = create(clock, config());

		// Two quarters into the series, so the first two rows are in the past.
		assertArrayEquals(new Double[] { 300.0, 400.0 }, sut.getPrices().asArray());
	}

	@Test
	void testBlankStartTimeFailsActivation() {
		// There is no fallback: at activation the Clock is still the real system
		// clock, so a missing anchor would serve prices for the wrong dates.
		assertThrows(Exception.class, () -> create(createDummyClock(), config().setStartTime("")));
	}

	@Test
	void testNoSellExpressionServesNoSellPrices() throws Exception {
		var sut = create(createDummyClock(), config());

		assertTrue(sut.getGridSellPrices().isEmpty());
	}

	@Test
	void testSellExpressionIsAppliedToTheMarketPrice() throws Exception {
		var sut = create(createDummyClock(), config().setSellCalculateExpression("x - 10"));

		assertArrayEquals(new Double[] { 90.0, 190.0, 290.0, 390.0 }, sut.getGridSellPrices().asArray());
	}
}
