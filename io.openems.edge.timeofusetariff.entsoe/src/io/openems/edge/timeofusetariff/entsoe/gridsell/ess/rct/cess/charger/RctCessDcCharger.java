package io.openems.edge.ess.rct.cess.charger;

import static io.openems.edge.common.type.TypeUtils.subtract;
import static io.openems.common.utils.IntUtils.maxInteger;

import java.util.function.Consumer;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.ess.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.timedata.api.TimedataProvider;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

public interface RctCessDcCharger extends 
		EssDcCharger, OpenemsComponent, ModbusSlave, EventHandler, TimedataProvider {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Binds this Charger to its parent {@link RctCessBatteryInverter}. Called by
	 * the Battery-Inverter on activation.
	 *
	 * @param inverter the {@link RctCessBatteryInverter}
	 */
	public void bindInverter(RctCessBatteryInverter inverter);

	/**
	 * Releases the binding to the parent {@link RctCessBatteryInverter}. Called by
	 * the Battery-Inverter on deactivation.
	 */
	public void unbindInverter();

    public static void calculateActualPowerFromBindings(RctCessDcCharger charger,
			RctCessBatteryInverter inverter) {
        var battery = inverter.getBatteryManagementSystem();
        if (battery == null) {
            return;
        }

        final Consumer<Value<Integer>> calculatePower = ignore -> {
            var dcPower = inverter.getDcPowerChannel().getNextValue().get();
            var batteryPower = battery.getRackPowerChannel().getNextValue().get();
            if (batteryPower == null || dcPower == null) {
                return;
            }
			setValue(charger, EssDcCharger.ChannelId.ACTUAL_POWER, maxInteger(subtract(dcPower, batteryPower), 0));
        };
        battery.getRackPowerChannel().onSetNextValue(calculatePower);
        inverter.getDcPowerChannel().onSetNextValue(calculatePower);
    }

	/**
	 * Derives the Charger VOLTAGE and CURRENT Channels from the DC-Voltage of the
	 * given inverter and the Charger's own {@link EssDcCharger.ChannelId.ACTUAL_POWER}.
	 *
	 * @param charger  the {@link RctCessDcCharger}
	 * @param inverter the {@link RctCessBatteryInverter} this Charger is bound to
	 */
	public static void calculateVoltageAndCurrentFromBindings(RctCessDcCharger charger,
			RctCessBatteryInverter inverter) {
		final Consumer<Value<Integer>> calculateVoltageAndCurrent = ignore -> {
			var voltage = inverter.getDcVoltageChannel().getNextValue().get();
			var power = charger.getActualPowerChannel().getNextValue().get();
			if (power == null || voltage == null) {
				return;
			}
			setValue(charger, EssDcCharger.ChannelId.VOLTAGE, voltage);
			setValue(charger, EssDcCharger.ChannelId.CURRENT, (power * 1000) / (voltage / 1000));
		};
		charger.getActualPowerChannel().onSetNextValue(calculateVoltageAndCurrent);
		inverter.getDcVoltageChannel().onSetNextValue(calculateVoltageAndCurrent);
	}

}
