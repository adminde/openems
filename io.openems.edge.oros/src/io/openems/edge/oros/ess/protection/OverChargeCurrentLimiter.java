package io.openems.edge.oros.ess.protection;

import static io.openems.edge.common.type.TypeUtils.multiply;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.max;

import io.openems.edge.common.filter.Pt1filter;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.oros.bms.BatteryManagementSystem;
import io.openems.edge.oros.bms.protection.VoltageProtection;
import io.openems.edge.oros.pcs.PowerConversionSystem;
import io.openems.edge.oros.ess.EnergyStorageSystem;

public class OverChargeCurrentLimiter extends CurrentLimiter {

	public OverChargeCurrentLimiter(EnergyStorageSystem parent) {
		super(parent, parent.getOverChargeProtectionCurrentChannel());
	}

	protected VoltageLimitValues getLimitValues(BatteryManagementSystem battery, PowerConversionSystem inverter) {
		return VoltageLimitValues.from(battery.isStarted(),
				battery.getInnerResistance().get(),
				battery.getCurrent().get(),
				battery.getVoltage().get(),
				battery.getChargeMaxVoltage().get(),
				battery instanceof VoltageProtection b ? b.getOverChargeProtectionVoltage().get() : null,
				inverter.getDcMaxVoltage().get());
	}

	protected Integer calculateMaxCurrent(VoltageLimitValues values, int cycleTime, Pt1filter filter) {
		filter.setCycleTime(cycleTime);
		var resistance = values.innerResistance() / 1000.;

		var voltageLimit = TypeUtils.min(values.voltageLimit(), values.voltageProtectionLimit(),
				values.pcsVoltageLimit());
		var voltageDelta = multiply(subtract(values.voltage(), voltageLimit), -1);
		var currentDelta = voltageDelta / resistance;
		var currentLimit = TypeUtils.subtract(currentDelta, (double) values.current());
		return filter.applyPt1Filter(max(currentLimit, -5.0));
	}

}
