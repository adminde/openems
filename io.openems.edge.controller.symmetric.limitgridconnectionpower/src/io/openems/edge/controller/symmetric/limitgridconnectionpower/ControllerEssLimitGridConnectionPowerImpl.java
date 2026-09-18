package io.openems.edge.controller.symmetric.limitgridconnectionpower;

import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.ManagedSymmetricEss;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Symmetric.LimitGridConnectionPower", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences({ "ess" })
public class ControllerEssLimitGridConnectionPowerImpl extends AbstractOpenemsComponent
		implements ControllerEssLimitGridConnectionPower, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(ControllerEssLimitGridConnectionPowerImpl.class);

	@Reference
	private Meta meta;

	@Reference
	private Sum sum;

	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.ess_id})(enabled=true))")
	private ManagedSymmetricEss ess;

	public ControllerEssLimitGridConnectionPowerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerEssLimitGridConnectionPower.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		// Check that we are On-Grid (and warn on undefined Grid-Mode)
		if (!this.ess.isOnGridOrUndefined(m -> this.logWarn(this.log, m))) {
			this.ess.setActivePowerLessOrEqualsWithoutFilter(this.id(), null);
			this.ess.setActivePowerGreaterOrEqualsWithoutFilter(this.id(), null);
			this._setActivePowerUpperLimit(null);
			this._setActivePowerLowerLimit(null);
			this._setStaticLimitFallback(false);
			this._setGridImportLimitExceeded(false);
			this._setGridExportLimitExceeded(false);
			return;
		}

		final var gridActivePower = this.sum.getGridActivePower().get();
		final var essActivePower = this.ess.getActivePower().get();
		final var essLimits = calculateLimits(gridActivePower, essActivePower, //
				Math.max(0, this.meta.getGridBuyHardLimitWithBuffer()), //
				Math.max(0, this.meta.getGridSellHardLimitWithBuffer()), //
				this.meta.getIsEssChargeFromGridAllowed(), //
				this.meta.getIsEssDischargeToGridAllowed());

		if (essLimits.isFallback()) {
			// Static limits are not derived from measurements
			this.ess.setActivePowerLessOrEqualsWithoutFilter(this.id(), essLimits.upper());
			this.ess.setActivePowerGreaterOrEqualsWithoutFilter(this.id(), essLimits.lower());
		} else {
			this.ess.setActivePowerLessOrEqualsWithFilter(this.id(), essLimits.upper());
			this.ess.setActivePowerGreaterOrEqualsWithFilter(this.id(), essLimits.lower());
		}

		this._setActivePowerUpperLimit(essLimits.upper());
		this._setActivePowerLowerLimit(essLimits.lower());
		this._setStaticLimitFallback(essLimits.isFallback());
		this._setGridImportLimitExceeded(//
				gridActivePower != null && gridActivePower > this.meta.getGridBuyHardLimit());
		this._setGridExportLimitExceeded(//
				gridActivePower != null && -gridActivePower > this.meta.getGridSellHardLimit());
	}

	/**
	 * Calculates the limits for the ESS active power.
	 *
	 * <p>
	 * The limits are derived from the power at the Grid Connection Point without
	 * the ESS contribution, so that the grid import stays below gridImportLimit and
	 * the grid export stays below gridExportLimit. If one of these limits is
	 * already exceeded by other consumers or producers, the resulting limit forces
	 * the ESS to charge or discharge the excess.
	 *
	 * <p>
	 * If charging from grid or discharging to grid is not allowed, the ESS is
	 * additionally limited to the local surplus or consumption. These limits never
	 * force the ESS to charge or discharge.
	 *
	 * <p>
	 * If the grid or the ESS active power is undefined, static limits are returned
	 * that only consider the ESS power itself.
	 *
	 * @param gridActivePower          the grid active power in [W], positive for
	 *                                 import, or null if undefined
	 * @param essActivePower           the ESS active power in [W], positive for
	 *                                 discharge, or null if undefined
	 * @param gridImportLimit          the maximum grid import power in [W], never
	 *                                 negative
	 * @param gridExportLimit          the maximum grid export power in [W], never
	 *                                 negative
	 * @param isChargeFromGridAllowed  whether the ESS may charge from grid
	 * @param isDischargeToGridAllowed whether the ESS may discharge to grid
	 * @return the {@link Limits}
	 */
	@VisibleForTesting
	static Limits calculateLimits(Integer gridActivePower, Integer essActivePower, int gridImportLimit,
			int gridExportLimit, boolean isChargeFromGridAllowed, boolean isDischargeToGridAllowed) {
		if (gridActivePower == null || essActivePower == null) {
			return new Limits(//
					isChargeFromGridAllowed ? -gridImportLimit : 0, //
					isDischargeToGridAllowed ? gridExportLimit : 0, //
					true);
		}

		// Power at the Grid Connection Point without the ESS contribution
		final var load = gridActivePower + essActivePower;

		var upper = load + gridExportLimit;
		if (!isDischargeToGridAllowed) {
			upper = Math.min(upper, Math.max(load, 0));
		}

		var lower = load - gridImportLimit;
		if (!isChargeFromGridAllowed) {
			lower = Math.max(lower, Math.min(load, 0));
		}

		return new Limits(lower, upper, false);
	}

	/**
	 * Limits for the ESS active power in [W]. Negative values for Charge; positive
	 * for Discharge.
	 *
	 * @param lower      the lower limit, applied as GreaterOrEquals constraint
	 * @param upper      the upper limit, applied as LessOrEquals constraint
	 * @param isFallback true if static limits are applied because the load is
	 *                   undefined
	 */
	record Limits(int lower, int upper, boolean isFallback) {
	}
}
