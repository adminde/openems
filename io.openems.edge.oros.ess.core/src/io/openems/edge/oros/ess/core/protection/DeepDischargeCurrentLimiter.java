package io.openems.edge.oros.ess.core.protection;

import static io.openems.common.utils.IntUtils.maxInteger;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.max;

import java.util.Optional;

import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.oros.bms.api.BatteryManagementSystem;
import io.openems.edge.oros.bms.api.BatteryProtection;
import io.openems.edge.oros.ess.api.EnergyStorageProtection;
import io.openems.edge.oros.ess.api.EnergyStorageSystem;
import io.openems.edge.oros.pcs.api.PowerConversionSystem;

public class DeepDischargeCurrentLimiter extends CurrentLimiter {

	public static Optional<DeepDischargeCurrentLimiter> of(EnergyStorageSystem parent, 
			PowerConversionSystem inverter, BatteryManagementSystem battery) {

		if (parent instanceof EnergyStorageProtection protection) {
			return Optional.of(new DeepDischargeCurrentLimiter(protection, inverter, battery));
		}
		return Optional.empty();
	}

	public DeepDischargeCurrentLimiter(EnergyStorageProtection protection, 
			PowerConversionSystem inverter, BatteryManagementSystem battery) {
		super(inverter, battery, protection.getDeepDischargeProtectionCurrentChannel());
	}

	protected VoltageLimitValues getLimitValues() {
		return VoltageLimitValues.from(battery.isStarted(),
				battery.getInnerResistance().get(),
				battery.getRackCurrent().get(),
				battery.getRackVoltage().get(),
				battery.getDischargeMinVoltage().get(),
				battery instanceof BatteryProtection b ? b.getDeepDischargeProtectionVoltage().get() : null,
				inverter.getDcMinVoltage().get());
	}

	protected Integer calculateMaxCurrent(VoltageLimitValues values, PT1Filter filter) {
		double resistance = values.innerResistance() / 1000.;

		int voltageLimit = maxInteger(values.pcsVoltageLimit(),
				values.voltageLimit(), values.voltageProtectionLimit());
		double voltageDelta = subtract(values.voltage() / 1000., (double) voltageLimit);
		double currentDelta = voltageDelta / resistance;
		double currentLimit = currentDelta + values.current() / 1000.;
		return filter.applyPT1Filter(max(currentLimit, -5.0));
	}
}
