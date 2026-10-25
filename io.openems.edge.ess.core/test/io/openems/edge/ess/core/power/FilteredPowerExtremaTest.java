package io.openems.edge.ess.core.power;

import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Relationship.EQUALS;
import static io.openems.edge.ess.power.api.Relationship.LESS_OR_EQUALS;
import static org.junit.Assert.assertEquals;

import java.util.function.Predicate;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.SolverStrategy;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;

public class FilteredPowerExtremaTest {

	private static final Predicate<Constraint> WITHOUT_SETPOINTS = constraint -> constraint.relationship != EQUALS;

	@Test
	public void testV1() throws Exception {
		this.test(SolverStrategy.OPTIMIZE_BY_MOVING_TOWARDS_TARGET);
	}

	@Test
	public void testV2() throws Exception {
		this.test(SolverStrategy.BALANCE);
	}

	private void test(SolverStrategy strategy) throws Exception {
		final var power = new EssPowerImpl();
		var ess0 = new DummyManagedSymmetricEss("ess0") //
				.setPower(power) //
				.withAllowedChargePower(-50000) //
				.withAllowedDischargePower(50000) //
				.withMaxApparentPower(50000);

		var cm = new DummyConfigurationAdmin();
		cm.getOrCreateEmptyConfiguration(EssPower.SINGLETON_SERVICE_PID);
		new ComponentTest(power) //
				.addReference("cm", cm) //
				.addReference("addEss", ess0) //
				.activate(MyConfig.create() //
						.setStrategy(strategy) //
						.setSymmetricMode(true) //
						.setDebugMode(false) //
						.setEnablePid(false) //
						.build()) //
				.next(new TestCase() //
						.onBeforeWriteCallbacks(() -> {
							power.addConstraint(power.createSimpleConstraint("Limit", ess0, ALL, ACTIVE,
									LESS_OR_EQUALS, 20000));
							power.addConstraint(
									power.createSimpleConstraint("Setpoint", ess0, ALL, ACTIVE, EQUALS, 10000));

							assertEquals(10000, power.getMaxPower(ess0, ALL, ACTIVE));
							assertEquals(10000, power.getMinPower(ess0, ALL, ACTIVE));
							assertEquals(20000, power.getMaxPower(ess0, ALL, ACTIVE, WITHOUT_SETPOINTS));
							assertEquals(-50000, power.getMinPower(ess0, ALL, ACTIVE, WITHOUT_SETPOINTS));
						}));
	}
}
