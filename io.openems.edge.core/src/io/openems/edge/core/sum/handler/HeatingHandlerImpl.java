package io.openems.edge.core.sum.handler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.openems.edge.common.channel.calculate.CalculateIntegerSum;
import io.openems.edge.common.channel.calculate.CalculateLongSum;
import io.openems.edge.common.sum.SumOptions;
import io.openems.edge.heat.api.AsymmetricHeating;
import io.openems.edge.heat.api.ManagedSymmetricHeating;
import io.openems.edge.heat.api.SymmetricHeating;

@Component(service = { HeatingHandlerImpl.class })
public class HeatingHandlerImpl {

	private final List<SymmetricHeating> heatings = new CopyOnWriteArrayList<>();
	private final CalculateIntegerSum heatingActivePower = new CalculateIntegerSum();
	private final CalculateIntegerSum heatingActivePowerL1 = new CalculateIntegerSum();
	private final CalculateIntegerSum heatingActivePowerL2 = new CalculateIntegerSum();
	private final CalculateIntegerSum heatingActivePowerL3 = new CalculateIntegerSum();
	private final CalculateLongSum heatingActiveConsumptionEnergy = new CalculateLongSum();
	private final CalculateIntegerSum heatingThermalPower = new CalculateIntegerSum();
	private final CalculateLongSum heatingThermalEnergy = new CalculateLongSum();
	private final CalculateIntegerSum managedConsumptionActivePower = new CalculateIntegerSum();

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			policyOption = ReferencePolicyOption.GREEDY, //
			target = "(enabled=true)"//
	)
	public void addHeating(SymmetricHeating component) {
		this.heatings.add(component);
	}

	public void removeHeating(SymmetricHeating component) {
		this.heatings.remove(component);
	}

	@Activate
	public HeatingHandlerImpl() {
	}

	/**
	 * Calculates the sum-values for all registered Heating components.
	 *
	 * <p>
	 * This method resets all internal calculators and aggregates values from
	 * Symmetric and Asymmetric Heating components.
	 */
	public void calculate() {
		this.resetCalculators();

		for (var heating : this.heatings) {
			if (heating instanceof SumOptions sumOption && !sumOption.addToSum()) {
				continue;
			}
			this.heatingActivePower.addValue(heating.getActivePowerChannel());
			this.heatingActiveConsumptionEnergy.addValue(heating.getActiveConsumptionEnergyChannel());
			this.heatingThermalPower.addValue(heating.getThermalPowerChannel());
			this.heatingThermalEnergy.addValue(heating.getThermalEnergyChannel());

			if (heating instanceof ManagedSymmetricHeating) {
				this.managedConsumptionActivePower.addValue(heating.getActivePowerChannel());
			}

			if (heating instanceof AsymmetricHeating h) {
				this.heatingActivePowerL1.addValue(h.getActivePowerL1Channel());
				this.heatingActivePowerL2.addValue(h.getActivePowerL2Channel());
				this.heatingActivePowerL3.addValue(h.getActivePowerL3Channel());
			} else {
				this.heatingActivePowerL1.addValue(heating.getActivePowerChannel(), CalculateIntegerSum.DIVIDE_BY_THREE);
				this.heatingActivePowerL2.addValue(heating.getActivePowerChannel(), CalculateIntegerSum.DIVIDE_BY_THREE);
				this.heatingActivePowerL3.addValue(heating.getActivePowerChannel(), CalculateIntegerSum.DIVIDE_BY_THREE);
			}
		}
	}

	private void resetCalculators() {
		this.heatingActivePower.reset();
		this.heatingActivePowerL1.reset();
		this.heatingActivePowerL2.reset();
		this.heatingActivePowerL3.reset();
		this.heatingActiveConsumptionEnergy.reset();
		this.heatingThermalPower.reset();
		this.heatingThermalEnergy.reset();
		this.managedConsumptionActivePower.reset();
	}

	public Integer getActivePower() {
		return this.heatingActivePower.calculate();
	}

	public Integer getActivePowerL1() {
		return this.heatingActivePowerL1.calculate();
	}

	public Integer getActivePowerL2() {
		return this.heatingActivePowerL2.calculate();
	}

	public Integer getActivePowerL3() {
		return this.heatingActivePowerL3.calculate();
	}

	public Long getActiveConsumptionEnergy() {
		return this.heatingActiveConsumptionEnergy.calculate();
	}

	public Integer getThermalPower() {
		return this.heatingThermalPower.calculate();
	}

	public Long getThermalEnergy() {
		return this.heatingThermalEnergy.calculate();
	}

	public Integer getManagedConsumptionActivePower() {
		return this.managedConsumptionActivePower.calculate();
	}

}
