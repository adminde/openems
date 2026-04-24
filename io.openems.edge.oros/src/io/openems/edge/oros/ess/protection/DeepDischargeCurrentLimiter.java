package io.openems.edge.oros.ess.protection;

import static io.openems.common.utils.IntUtils.maxInteger;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.max;

import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.bms.protection.VoltageProtection;
import io.openems.edge.oros.pcs.PowerConversionSystem;
import io.openems.edge.oros.ess.EnergyStorageSystem;

public class DeepDischargeCurrentLimiter extends CurrentLimiter {

	public DeepDischargeCurrentLimiter(EnergyStorageSystem parent) {
		super(parent, parent.getDeepDischargeProtectionCurrentChannel());
	}

	protected VoltageLimitValues getLimitValues(BatteryManagementSystem battery, PowerConversionSystem inverter) {
		return VoltageLimitValues.from(battery.isStarted(),
				battery.getInnerResistance().get(),
				battery.getCurrent().get(),
				battery.getVoltage().get(),
				battery.getDischargeMinVoltage().get(),
				battery instanceof VoltageProtection b ? b.getDeepDischargeProtectionVoltage().get() : null,
				inverter.getDcMinVoltage().get());
	}

	protected Integer calculateMaxCurrent(VoltageLimitValues values, int cycleTime, PT1Filter filter) {
		var resistance = values.innerResistance() / 1000.;

		int voltageLimit = maxInteger(values.pcsVoltageLimit(),
				values.voltageLimit(), values.voltageProtectionLimit());
		var voltageDelta = subtract(values.voltage(), voltageLimit);
		double currentDelta = voltageDelta / resistance;
		double currentLimit = currentDelta + (double) values.current();
		return filter.applyPT1Filter(max(currentLimit, -5.0));
	}
}
