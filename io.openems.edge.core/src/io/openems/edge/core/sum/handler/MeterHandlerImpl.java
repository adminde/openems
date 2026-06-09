package io.openems.edge.core.sum.handler;

import static io.openems.common.utils.FunctionUtils.doNothing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.openems.common.types.MeterType;
import io.openems.edge.common.channel.calculate.CalculateIntegerSum;
import io.openems.edge.common.channel.calculate.CalculateLongSum;
import io.openems.edge.common.sum.SumOptions;
import io.openems.edge.evcs.api.MetaEvcs;
import io.openems.edge.meter.api.ElectricityMeter;

@Component(service = { MeterHandlerImpl.class }) //
public class MeterHandlerImpl {

	private final List<ElectricityMeter> meters = new CopyOnWriteArrayList<>();

	// Grid Calculators
	private final CalculateIntegerSum gridActivePower = new CalculateIntegerSum();
	private final CalculateIntegerSum gridActivePowerL1 = new CalculateIntegerSum();
	private final CalculateIntegerSum gridActivePowerL2 = new CalculateIntegerSum();
	private final CalculateIntegerSum gridActivePowerL3 = new CalculateIntegerSum();
	private final CalculateIntegerSum gridGensetActivePower = new CalculateIntegerSum();
	private final CalculateIntegerSum gridGensetActivePowerL1 = new CalculateIntegerSum();
	private final CalculateIntegerSum gridGensetActivePowerL2 = new CalculateIntegerSum();
	private final CalculateIntegerSum gridGensetActivePowerL3 = new CalculateIntegerSum();
	private final CalculateLongSum gridBuyActiveEnergy = new CalculateLongSum();
	private final CalculateLongSum gridSellActiveEnergy = new CalculateLongSum();

    private final CalculateIntegerSum gridReactivePower = new CalculateIntegerSum();
    private final CalculateIntegerSum gridReactivePowerL1 = new CalculateIntegerSum();
    private final CalculateIntegerSum gridReactivePowerL2 = new CalculateIntegerSum();
    private final CalculateIntegerSum gridReactivePowerL3 = new CalculateIntegerSum();
    private final CalculateLongSum gridLaggingReactiveEnergy = new CalculateLongSum();
    private final CalculateLongSum gridLeadingReactiveEnergy = new CalculateLongSum();

	// Production Calculators
	private final CalculateIntegerSum productionAcActivePower = new CalculateIntegerSum();
	private final CalculateIntegerSum productionAcActivePowerL1 = new CalculateIntegerSum();
	private final CalculateIntegerSum productionAcActivePowerL2 = new CalculateIntegerSum();
	private final CalculateIntegerSum productionAcActivePowerL3 = new CalculateIntegerSum();
	private final CalculateLongSum productionAcActiveEnergy = new CalculateLongSum();
	private final CalculateLongSum productionAcActiveEnergyNegative = new CalculateLongSum();

	// Consumption Calculators
	private final CalculateIntegerSum managedConsumptionActivePower = new CalculateIntegerSum();

	/**
	 * Adds an {@link ElectricityMeter} to the handler.
	 * 
	 * @param component the {@link ElectricityMeter} to add
	 */
	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			cardinality = ReferenceCardinality.MULTIPLE, //
			policyOption = ReferencePolicyOption.GREEDY, //
			target = "(enabled=true)"//
	)
	public void addMeter(ElectricityMeter component) {
		this.meters.add(component);
	}

	/**
	 * Removes an {@link ElectricityMeter} from the handler.
	 * 
	 * @param component the {@link ElectricityMeter} to remove
	 */
	public void removeMeter(ElectricityMeter component) {
		this.meters.remove(component);
	}

	@Activate
	public MeterHandlerImpl() {
	}

	/**
	 * Calculates the sum-values for all registered electricity meters.
	 * 
	 * <p>
	 * Resets all internal calculators and iterates through the meters to aggregate
	 * values for Grid, Production, and Managed Consumption based on their meter
	 * type.
	 */
	public void calculate() {
		this.resetCalculators();
		for (var meter : this.meters) {
			if (meter instanceof SumOptions sumOption && !sumOption.addToSum()) {
				continue;
			}
			switch (meter.getMeterType()) {
			case GRID, GRID_GENSET -> {
				this.gridActivePower.addValue(meter.getActivePowerChannel());
				this.gridActivePowerL1.addValue(meter.getActivePowerL1Channel());
				this.gridActivePowerL2.addValue(meter.getActivePowerL2Channel());
				this.gridActivePowerL3.addValue(meter.getActivePowerL3Channel());
				
				this.gridReactivePower.addValue(meter.getReactivePowerChannel());
				this.gridReactivePowerL1.addValue(meter.getReactivePowerL1Channel());
				this.gridReactivePowerL2.addValue(meter.getReactivePowerL2Channel());
				this.gridReactivePowerL3.addValue(meter.getReactivePowerL3Channel());

				this.gridBuyActiveEnergy.addValue(meter.getActiveProductionEnergyChannel());
				this.gridSellActiveEnergy.addValue(meter.getActiveConsumptionEnergyChannel());
				this.gridLaggingReactiveEnergy.addValue(meter.getReactiveLaggingEnergyChannel());
				this.gridLeadingReactiveEnergy.addValue(meter.getReactiveLeadingEnergyChannel());

				if (meter.getMeterType() == MeterType.GRID_GENSET) {
					this.gridGensetActivePower.addValue(meter.getActivePowerChannel());
					this.gridGensetActivePowerL1.addValue(meter.getActivePowerL1Channel());
					this.gridGensetActivePowerL2.addValue(meter.getActivePowerL2Channel());
					this.gridGensetActivePowerL3.addValue(meter.getActivePowerL3Channel());
				}
			}
			case PRODUCTION -> {
				this.productionAcActivePower.addValue(meter.getActivePowerChannel());
				this.productionAcActiveEnergy.addValue(meter.getActiveProductionEnergyChannel());
				this.productionAcActiveEnergyNegative.addValue(meter.getActiveConsumptionEnergyChannel());
				this.productionAcActivePowerL1.addValue(meter.getActivePowerL1Channel());
				this.productionAcActivePowerL2.addValue(meter.getActivePowerL2Channel());
				this.productionAcActivePowerL3.addValue(meter.getActivePowerL3Channel());
			}
			case MANAGED_CONSUMPTION_METERED -> {
				if (!(meter instanceof MetaEvcs)) {
					this.managedConsumptionActivePower.addValue(meter.getActivePowerChannel());
				}
			}
			case PRODUCTION_AND_CONSUMPTION -> // TODO
				// Production Power is positive, Consumption is negative
				doNothing();
			case CONSUMPTION_METERED -> // TODO
				// Consumption is positive
				doNothing();
			case CONSUMPTION_NOT_METERED -> // TODO
				// Consumption is positive
				doNothing();
			}
		}
	}

	private void resetCalculators() {
		this.gridActivePower.reset();
		this.gridActivePowerL1.reset();
		this.gridActivePowerL2.reset();
		this.gridActivePowerL3.reset();
		this.gridGensetActivePower.reset();
		this.gridGensetActivePowerL1.reset();
		this.gridGensetActivePowerL2.reset();
		this.gridGensetActivePowerL3.reset();
		this.gridBuyActiveEnergy.reset();
		this.gridSellActiveEnergy.reset();
		this.gridReactivePower.reset();
		this.gridReactivePowerL1.reset();
		this.gridReactivePowerL2.reset();
		this.gridReactivePowerL3.reset();
		this.gridLaggingReactiveEnergy.reset();
		this.gridLeadingReactiveEnergy.reset();
		this.productionAcActivePower.reset();
		this.productionAcActivePowerL1.reset();
		this.productionAcActivePowerL2.reset();
		this.productionAcActivePowerL3.reset();
		this.productionAcActiveEnergy.reset();
		this.productionAcActiveEnergyNegative.reset();
		this.managedConsumptionActivePower.reset();
	}

	// Grid Getters
	/**
	 * Returns the total active power of all GRID meters.
	 *
	 * @return summed active power in W
	 */
	public Integer getGridActivePower() {
		return this.gridActivePower.calculate();
	}

	/**
	 * Returns the summed active power of phase L1 for all GRID meters.
	 *
	 * @return summed active power for L1 in W
	 */
	public Integer getGridActivePowerL1() {
		return this.gridActivePowerL1.calculate();
	}

	/**
	 * Returns the summed active power of phase L1 for all GRID meters.
	 *
	 * @return summed active power for L1 in W
	 */
	public Integer getGridActivePowerL2() {
		return this.gridActivePowerL2.calculate();
	}

	/**
	 * Returns the summed active power of phase L1 for all GRID meters.
	 *
	 * @return summed active power for L1 in W
	 */
	public Integer getGridActivePowerL3() {
		return this.gridActivePowerL3.calculate();
	}

	/**
	 * Returns the total active power of GRID_GENSET meters only.
	 *
	 * @return summed genset active power in W
	 */
	public Integer gridGensetActivePower() {
		return this.gridGensetActivePower.calculate();
	}

	/**
	 * Returns the summed active power of phase L1 for GRID_GENSET meters.
	 *
	 * @return summed genset active power for L1 in W
	 */
	public Integer gridGensetActivePowerL1() {
		return this.gridGensetActivePowerL1.calculate();
	}

	/**
	 * Returns the summed active power of phase L2 for GRID_GENSET meters.
	 *
	 * @return summed genset active power for L2 in W
	 */
	public Integer gridGensetActivePowerL2() {
		return this.gridGensetActivePowerL2.calculate();
	}

	/**
	 * Returns the summed active power of phase L3 for GRID_GENSET meters.
	 *
	 * @return summed genset active power for L3 in W
	 */
	public Integer gridGensetActivePowerL3() {
		return this.gridGensetActivePowerL3.calculate();
	}

	/**
	 * Returns the total imported (buy) active energy from GRID and GRID_GENSET
	 * meters.
	 *
	 * @return summed imported energy in Wh
	 */
	public Long getGridBuyActiveEnergy() {
		return this.gridBuyActiveEnergy.calculate();
	}

	/**
	 * Returns the total exported (sell) active energy to the grid.
	 *
	 * @return summed exported energy in Wh
	 */
	public Long getGridSellActiveEnergy() {
		return this.gridSellActiveEnergy.calculate();
	}

	/**
	 * Returns the total reactive power of all GRID meters.
	 *
	 * @return summed reactive power in var
	 */
	public Integer getGridReactivePower() {
		return this.gridReactivePower.calculate();
	}

	/**
	 * Returns the summed reactive power of phase L1 for all GRID meters.
	 *
	 * @return summed reactive power for L1 in var
	 */
	public Integer getGridReactivePowerL1() {
		return this.gridReactivePowerL1.calculate();
	}

	/**
	 * Returns the summed reactive power of phase L1 for all GRID meters.
	 *
	 * @return summed reactive power for L1 in var
	 */
	public Integer getGridReactivePowerL2() {
		return this.gridReactivePowerL2.calculate();
	}

	/**
	 * Returns the summed reactive power of phase L1 for all GRID meters.
	 *
	 * @return summed reactive power for L1 in var
	 */
	public Integer getGridReactivePowerL3() {
		return this.gridReactivePowerL3.calculate();
	}

	/**
	 * Returns the total lagging (inductive) reactive energy of the grid
	 * meters.
	 *
	 * @return summed imported energy in varh
	 */
	public Long getGridLaggingReactiveEnergy() {
		return this.gridLaggingReactiveEnergy.calculate();
	}

	/**
	 * Returns the total leading (capacitive) reactive energy of the grid.
	 *
	 * @return summed exported energy in varh
	 */
	public Long getGridLeadingReactiveEnergy() {
		return this.gridLeadingReactiveEnergy.calculate();
	}

	// Production Getters
	/**
	 * Returns the total AC active power of all PRODUCTION meters.
	 *
	 * @return summed production active power in W
	 */
	public Integer getProductionAcActivePower() {
		return this.productionAcActivePower.calculate();
	}

	/**
	 * Returns the summed AC active power of phase L1 for PRODUCTION meters.
	 *
	 * @return summed production active power for L1 in W
	 */
	public Integer getProductionAcActivePowerL1() {
		return this.productionAcActivePowerL1.calculate();
	}

	/**
	 * Returns the summed AC active power of phase L2 for PRODUCTION meters.
	 *
	 * @return summed production active power for L2 in W
	 */
	public Integer getProductionAcActivePowerL2() {
		return this.productionAcActivePowerL2.calculate();
	}

	/**
	 * Returns the summed AC active power of phase L3 for PRODUCTION meters.
	 *
	 * @return summed production active power for L3 in W
	 */
	public Integer getProductionAcActivePowerL3() {
		return this.productionAcActivePowerL3.calculate();
	}

	/**
	 * Returns the total produced active energy.
	 *
	 * @return summed production energy in Wh
	 */
	public Long getProductionAcActiveEnergy() {
		return this.productionAcActiveEnergy.calculate();
	}

	/**
	 * Returns the negative production energy (i.e. energy counted as consumption on
	 * production meters).
	 *
	 * @return summed negative production energy in Wh
	 */
	public Long getProductionAcActiveEnergyNegative() {
		return this.productionAcActiveEnergyNegative.calculate();
	}

	// Consumption Getters

	/**
	 * Returns the total active power of managed consumption meters (excluding
	 * EVCS/MetaEvcs meters).
	 *
	 * @return summed managed consumption active power in W
	 */
	public Integer getManagedConsumptionActivePower() {
		return this.managedConsumptionActivePower.calculate();
	}

}