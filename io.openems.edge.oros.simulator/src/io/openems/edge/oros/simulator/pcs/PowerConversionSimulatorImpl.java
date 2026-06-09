package io.openems.edge.oros.simulator.pcs;

import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Relationship.GREATER_OR_EQUALS;
import static io.openems.edge.ess.power.api.Relationship.LESS_OR_EQUALS;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.ArrayList;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.BatteryInverterConstraint;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.sum.GridMode;
import io.openems.edge.oros.SymmetricComponent;
import io.openems.edge.oros.pcs.PowerConversionSystem;
import io.openems.edge.oros.simulator.bms.BatteryManagementSimulator;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Simulator.OROS.PCS",
		immediate = true,
		configurationPolicy = REQUIRE)
public class PowerConversionSimulatorImpl extends AbstractOpenemsComponent
		implements PowerConversionSimulator, PowerConversionSystem, ManagedSymmetricBatteryInverter,
		SymmetricBatteryInverter, SymmetricComponent, OpenemsComponent, ModbusSlave, TimedataProvider {

	/** Efficiency factor (%) used for AC/DC conversion. */
	public static final float EFFICIENCY_FACTOR = 98F;

	public static final float POWER_DERATING_ZONE = 5F;

	private final CalculateEnergyFromPower calculateChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	private Config config;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	public PowerConversionSimulatorImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				SymmetricBatteryInverter.ChannelId.values(),
				ManagedSymmetricBatteryInverter.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				SymmetricComponent.ChannelId.values(),
				PowerConversionSystem.ChannelId.values(),
				PowerConversionSimulator.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		setValue(this, SymmetricBatteryInverter.ChannelId.GRID_MODE, GridMode.ON_GRID);
		setValue(this, SymmetricBatteryInverter.ChannelId.MAX_APPARENT_POWER, config.maxApparentPower());
		this._setStartStop(StartStop.START);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run(Battery battery, int setActivePower, int setReactivePower) throws OpenemsNamedException {
		if (battery instanceof BatteryManagementSimulator bms) {
			this.run(bms, setActivePower, setReactivePower);
		}
	}

	public void run(BatteryManagementSimulator bms, int activePower, int reactivePower) throws OpenemsNamedException {
		if (!this.isEnabled()) {
			return;
		}
		// RACK_SOC channel is in [0.1 %] (per-mille) -> divide by 10 to get percent.
		var soc = bms.getRackSocChannel().value().get() / 10F;

		int maxChargePower = calculateAllowedChargePower(soc, this.config.maxChargePower());
		int maxDischargePower = calculateAllowedDischargePower(soc, this.config.maxDischargePower());
		if (soc >= 100F && activePower < 0) {
			activePower = 0;
			maxChargePower = 0;
		}
		else if (activePower < maxChargePower * -1) {
			activePower = maxChargePower * -1;
		}
		if (soc <= 0F && activePower > 0) {
			activePower = 0;
			maxDischargePower = 0;
		}
		else if (activePower > maxDischargePower) {
			activePower = maxDischargePower;
		}
		bms._setChargeMaxPower(maxChargePower);
		bms._setDischargeMaxPower(maxDischargePower);
		bms.run(activePower);

		int dcVoltage = bms.getRackVoltageChannel().getNextValue().get();
		int dcCurrentMa = (int) ((long) activePower * 1_000_000L / dcVoltage);
		this._setDcVoltage(dcVoltage);
		this._setDcCurrent(dcCurrentMa);
		this._setDcPower(activePower);

		setValue(this, SymmetricBatteryInverter.ChannelId.ACTIVE_POWER, activePower);
		setValue(this, SymmetricBatteryInverter.ChannelId.REACTIVE_POWER, reactivePower);
		this.calculateEnergy();
	}

	private void calculateEnergy() {
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			this.calculateChargeEnergy.update(null);
			this.calculateDischargeEnergy.update(null);
		} else if (activePower > 0) {
			this.calculateChargeEnergy.update(0);
			this.calculateDischargeEnergy.update(activePower);
		} else {
			this.calculateChargeEnergy.update(activePower * -1);
			this.calculateDischargeEnergy.update(0);
		}
	}

	/**
	 * Calculates allowed charge power with linear derating from (100 - {@link POWER_DERATING_ZONE})% to 100% SoC.
	 *
	 * @param soc            current SoC [%]
	 * @param maxChargePower maximum charge power [W], positive
	 * @return allowed charge power [W], positive
	 */
	private static int calculateAllowedChargePower(float soc, int maxChargePower) {
		if (soc >= 100) {
			return 0;
		}
		if (soc > (100 - POWER_DERATING_ZONE)) {
			return Math.round(maxChargePower * (100 - soc) / POWER_DERATING_ZONE);
		}
		return maxChargePower;
	}

	/**
	 * Calculates allowed discharge power with linear derating from {@link POWER_DERATING_ZONE}% to 0% SoC.
	 *
	 * @param soc               current SoC [%]
	 * @param maxDischargePower maximum discharge power [W], positive
	 * @return allowed discharge power [W], positive
	 */
	private static int calculateAllowedDischargePower(float soc, int maxDischargePower) {
		if (soc <= 0) {
			return 0;
		}
		if (soc < POWER_DERATING_ZONE) {
			return Math.round(maxDischargePower * soc / POWER_DERATING_ZONE);
		}
		return maxDischargePower;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public float getEfficiencyFactor() {
		return EFFICIENCY_FACTOR;
	}

	@Override
	public int getChargeMaxPower() {
		return this.config.maxChargePower();
	}

	@Override
	public int getDischargeMaxPower() {
		return this.config.maxDischargePower();
	}

	@Override
	public int getPowerPrecision() {
		return 1;
	}

	@Override
	public BatteryInverterConstraint[] getStaticConstraints() throws OpenemsNamedException {
		var constraints = new ArrayList<BatteryInverterConstraint>();

		var maxActivePower = this.getDischargeMaxPower();
		var minActivePower = -1 * this.getChargeMaxPower();
		constraints.add(new BatteryInverterConstraint("HyperCube II maximum Active Power",
				ALL, ACTIVE, LESS_OR_EQUALS, maxActivePower));
		constraints.add(new BatteryInverterConstraint("HyperCube II minimum Active Power",
				ALL, ACTIVE, GREATER_OR_EQUALS, minActivePower));

		return constraints.toArray(new BatteryInverterConstraint[constraints.size()]);
	}

	@Override
	public void setStartStop(StartStop value) {
		// not supported; simulator always stays in START
	}

	@Override
	public String debugLog() {
		return PowerConversionSystem.generateDebugLog(this);
	}

}
