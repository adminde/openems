package io.openems.edge.oros.ess.core.protection;

import static io.openems.common.utils.IntUtils.maxInteger;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.max;

import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.bms.api.BatteryProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class DeepDischargeCurrentLimiter extends CurrentLimiter {

	public DeepDischargeCurrentLimiter(EnergyStorageSystem parent, 
			PowerConversionSystem inverter, BatteryManagementSystem battery) {
		super(inverter, battery, parent.getDeepDischargeProtectionCurrentChannel());
	}

	protected VoltageLimitValues getLimitValues() {
		return VoltageLimitValues.from(battery.isStarted(),
				battery.getInnerResistance().get(),
				battery.getCurrent().get(),
				battery.getVoltage().get(),
				battery.getDischargeMinVoltage().get(),
				battery instanceof BatteryProtection b ? b.getDeepDischargeProtectionVoltage().get() : null,
				inverter.getDcMinVoltage().get());
	}

	protected Integer calculateMaxCurrent(VoltageLimitValues values, PT1Filter filter) {
		var resistance = values.innerResistance() / 1000.;

		int voltageLimit = maxInteger(values.pcsVoltageLimit(),
				values.voltageLimit(), values.voltageProtectionLimit());
		var voltageDelta = subtract(values.voltage(), voltageLimit);
		double currentDelta = voltageDelta / resistance;
		double currentLimit = currentDelta + (double) values.current();
		return filter.applyPT1Filter(max(currentLimit, -5.0));
	}
}
