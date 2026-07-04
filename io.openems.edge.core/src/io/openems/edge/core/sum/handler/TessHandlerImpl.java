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
import io.openems.edge.heat.tess.api.CalculateTessSoc;
import io.openems.edge.heat.tess.api.SocAveragingMethod;
import io.openems.edge.heat.tess.api.ThermalEss;

@Component(service = { TessHandlerImpl.class })
public class TessHandlerImpl {

	private final List<ThermalEss> tesss = new CopyOnWriteArrayList<>();
	private final CalculateIntegerSum tessCapacity = new CalculateIntegerSum();
	private final CalculateIntegerSum tessThermalPower = new CalculateIntegerSum();
	private final CalculateLongSum tessThermalChargeEnergy = new CalculateLongSum();
	private final CalculateLongSum tessThermalDischargeEnergy = new CalculateLongSum();
	private CalculateTessSoc tessSoc = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			policyOption = ReferencePolicyOption.GREEDY, //
			target = "(enabled=true)"//
	)
	public void addTess(ThermalEss component) {
		this.tesss.add(component);
	}

	public void removeTess(ThermalEss component) {
		this.tesss.remove(component);
	}

	@Activate
	public TessHandlerImpl() {
	}

	/**
	 * Calculates the sum-values for all registered Thermal Energy Storage Systems.
	 *
	 * <p>
	 * This method resets all internal calculators and aggregates values from all
	 * registered {@link ThermalEss} components.
	 */
	public void calculate() {
		this.resetCalculators();

		for (var tess : this.tesss) {
			if (tess instanceof SumOptions sumOption && !sumOption.addToSum()) {
				continue;
			}
			this.tessSoc.add(tess);
			this.tessCapacity.addValue(tess.getCapacityChannel());
			this.tessThermalPower.addValue(tess.getThermalPowerChannel());
			this.tessThermalChargeEnergy.addValue(tess.getThermalChargeEnergyChannel());
			this.tessThermalDischargeEnergy.addValue(tess.getThermalDischargeEnergyChannel());
		}
	}

	private void resetCalculators() {
		this.tessSoc = new CalculateTessSoc(SocAveragingMethod.ARITHMETIC);
		this.tessCapacity.reset();
		this.tessThermalPower.reset();
		this.tessThermalChargeEnergy.reset();
		this.tessThermalDischargeEnergy.reset();
	}

	public Integer getSoc() {
		return this.tessSoc.calculate();
	}

	public Integer getCapacity() {
		return this.tessCapacity.calculate();
	}

	public Integer getThermalPower() {
		return this.tessThermalPower.calculate();
	}

	public Long getThermalChargeEnergy() {
		return this.tessThermalChargeEnergy.calculate();
	}

	public Long getThermalDischargeEnergy() {
		return this.tessThermalDischargeEnergy.calculate();
	}

}
