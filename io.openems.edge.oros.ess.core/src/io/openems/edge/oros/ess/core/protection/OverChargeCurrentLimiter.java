package io.openems.edge.oros.ess.core.protection;

import static io.openems.common.utils.IntUtils.minInteger;
import static io.openems.edge.common.type.TypeUtils.multiply;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.max;

import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.bms.api.BatteryProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class OverChargeCurrentLimiter extends CurrentLimiter {

	public OverChargeCurrentLimiter(EnergyStorageSystem parent,
			PowerConversionSystem inverter, BatteryManagementSystem battery) {
		super(inverter, battery, parent.getOverChargeProtectionCurrentChannel());
	}

	protected VoltageLimitValues getLimitValues() {
		return VoltageLimitValues.from(battery.isStarted(),
				battery.getInnerResistance().get(),
				battery.getRackCurrent().get(),
				battery.getRackVoltage().get(),
				battery.getChargeMaxVoltage().get(),
				battery instanceof BatteryProtection b ? b.getOverChargeProtectionVoltage().get() : null,
				inverter.getDcMaxVoltage().get());
	}

	protected Integer calculateMaxCurrent(VoltageLimitValues values, PT1Filter filter) {
		double resistance = values.innerResistance() / 1000.;

		int voltageLimit = minInteger(values.pcsVoltageLimit(),
				values.voltageLimit(), values.voltageProtectionLimit());
		double voltageDelta = multiply(subtract(values.voltage() / 1000., (double) voltageLimit), -1.);
		double currentDelta = voltageDelta / resistance;
		double currentLimit = currentDelta - values.current() / 1000.;
		return filter.applyPT1Filter(max(currentLimit, -5.0));
	}
}
