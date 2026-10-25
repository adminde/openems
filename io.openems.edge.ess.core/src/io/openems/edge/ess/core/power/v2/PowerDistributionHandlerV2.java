package io.openems.edge.ess.core.power.v2;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.function.BooleanConsumer;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.type.Phase.SingleOrAllPhase;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.core.power.PowerDistributionHandler;
import io.openems.edge.ess.core.power.v1.data.ConstraintUtil;
import io.openems.edge.ess.power.api.Coefficient;
import io.openems.edge.ess.power.api.Coefficients;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.ess.power.api.Relationship;

/**
 * Handles Power-Distribution using Charge/Discharge Split without a linear
 * equation system.
 */
public class PowerDistributionHandlerV2 implements PowerDistributionHandler {

	private final Supplier<List<ManagedSymmetricEss>> esssSupplier;
	private final BooleanConsumer onSetNotSolved;
	private final Coefficients coefficients = new Coefficients();
	private final List<Constraint.Simple> cycleConstraints = new CopyOnWriteArrayList<>();

	private PowerDistribution pd;

	public PowerDistributionHandlerV2(Supplier<List<ManagedSymmetricEss>> esssSupplier,
			BooleanConsumer onSetNotSolved) {
		this.esssSupplier = esssSupplier;
		this.onSetNotSolved = onSetNotSolved;
		this.onUpdateEsss(); // Initialize
	}

	@Override
	public void onUpdateEsss() {
		// Re-Initialize Coefficients
		final var esss = this.esssSupplier.get();
		this.coefficients.initialize(false /* symmetricMode */, //
				esss.stream() //
						.map(OpenemsComponent::id) //
						.collect(toSet()));
	}

	@Override
	public void onAfterProcessImage() {
		final var esss = this.esssSupplier.get();
		this.pd = PowerDistribution.from(esss);
		this.cycleConstraints.clear();
	}

	@Override
	public void onBeforeWriteEvent() {
		if (this.pd == null) {
			return;
		}

		// Solve
		this.pd.solve();
		this.onSetNotSolved.accept(false);

		// Apply Power
		final var esss = this.esssSupplier.get();
		this.pd.applyToEsss(esss);
	}

	@Override
	public void onAfterWriteEvent() {
		// ignore, this.pd is recreated in onAfterProcessImage() at start of next cycle
	}

	@Override
	public void addConstraint(Constraint rawConstraint) {
		if (!(rawConstraint instanceof Constraint.Simple constraint)) {
			throw new IllegalArgumentException("Only Simple Constraints are allowed here");
		}

		if (this.pd == null) {
			return;
		}
		this.cycleConstraints.add(constraint);
		apply(this.pd, constraint);
	}

	private static void apply(PowerDistribution pd, Constraint.Simple constraint) {
		final var pwr = constraint.coefficient.getCoefficient().getPwr();
		final var essId = constraint.coefficient.getCoefficient().getEssId();
		final var value = constraint.value;

		switch (pwr) {
		case ACTIVE -> {
			switch (constraint.relationship) {
			case EQUALS //
				-> pd.setEquals(essId, value);
			case LESS_OR_EQUALS //
				-> pd.setLessOrEquals(essId, value);
			case GREATER_OR_EQUALS //
				-> pd.setGreaterOrEquals(essId, value);
			}
		}
		case REACTIVE -> {
			switch (constraint.relationship) {
			case EQUALS //
				-> pd.setReactiveEquals(essId, value);
			case LESS_OR_EQUALS //
				-> pd.setReactiveLessOrEquals(essId, value);
			case GREATER_OR_EQUALS //
				-> pd.setReactiveGreaterOrEquals(essId, value);
			}
		}
		}
	}

	@Override
	public void addConstraintAndValidate(Constraint constraint) throws OpenemsException {
		// TODO validate if required
		this.addConstraint(constraint);
	}

	@Override
	public Coefficient getCoefficient(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr)
			throws OpenemsException {
		return this.coefficients.of(ess.id(), phase, pwr);
	}

	@Override
	public Constraint createSimpleConstraint(String description, ManagedSymmetricEss ess, SingleOrAllPhase phase,
			Pwr pwr, Relationship relationship, int value) throws OpenemsException {
		return ConstraintUtil.createSimpleConstraint(this.coefficients, //
				description, ess.id(), phase, pwr, relationship, value);
	}

	@Override
	public void removeConstraint(Constraint constraint) {
		// TODO required?
		// this.constraints.remove(constraint);
	}

	@Override
	public int getPowerExtrema(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr, GoalType goal) {
		return getPowerExtrema(this.pd, ess, pwr, goal);
	}

	@Override
	public int getPowerExtrema(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr, GoalType goal,
			Predicate<Constraint> filter) {
		if (this.pd == null) {
			return 0;
		}
		// The distribution merges every Constraint into its bounds, so the filtered
		// Constraints are applied to a fresh distribution of the same ESS.
		var pd = PowerDistribution.from(this.esssSupplier.get());
		this.cycleConstraints.stream() //
				.filter(filter) //
				.forEach(constraint -> apply(pd, constraint));
		return getPowerExtrema(pd, ess, pwr, goal);
	}

	private static int getPowerExtrema(PowerDistribution pd, ManagedSymmetricEss ess, Pwr pwr, GoalType goal) {
		if (pd == null) {
			return 0;
		}
		if (pwr == Pwr.ACTIVE) {
			return pd.getActivePowerExtrema(ess.id(), goal);
		}
		return pd.getReactivePowerExtrema(ess.id(), goal);
	}
}