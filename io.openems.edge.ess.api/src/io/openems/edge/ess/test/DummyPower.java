package io.openems.edge.ess.test;

import static io.openems.common.utils.IntUtils.minInt;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.openems.edge.common.filter.DisabledFilter;
import io.openems.edge.common.filter.Filter;
import io.openems.edge.common.filter.PT1Filter;
import io.openems.edge.common.filter.PidFilter;
import io.openems.edge.common.type.Phase.SingleOrAllPhase;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.power.api.Coefficient;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.ess.power.api.PowerException;
import io.openems.edge.ess.power.api.Pwr;
import io.openems.edge.ess.power.api.Relationship;

public class DummyPower implements Power {

	private final Filter filter;
	private final List<ManagedSymmetricEss> esss = new ArrayList<>();

	private int maxApparentPower;
	private final Map<FilterKey, Filter> filters = new HashMap<>();
	private Clock pt1FilterClock = Clock.systemDefaultZone();
	private int pt1TimeConstant = 0;

	/**
	 * Creates a {@link DummyPower} with unlimited MaxApparentPower and
	 * {@link DisabledFilter}.
	 */
	public DummyPower() {
		this(Integer.MAX_VALUE, new DisabledFilter());
	}

	/**
	 * Creates a {@link DummyPower} with given MaxApparentPower and
	 * {@link DisabledFilter}.
	 *
	 * @param maxApparentPower the MaxApparentPower
	 */
	public DummyPower(int maxApparentPower) {
		this(maxApparentPower, new DisabledFilter());
	}

	public DummyPower(int maxApparentPower, Filter filter) {
		this.maxApparentPower = maxApparentPower;
		this.filter = filter;
	}

	/**
	 * Creates a {@link DummyPower} with unlimited MaxApparentPower and PID filter
	 * with the given parameters.
	 * 
	 * @param p the proportional gain
	 * @param i the integral gain
	 * @param d the derivative gain
	 */
	public DummyPower(double p, double i, double d) {
		this(Integer.MAX_VALUE, p, i, d);
	}

	/**
	 * Creates a {@link DummyPower} with given MaxApparentPower and PID filter with
	 * the given parameters.
	 * 
	 * @param maxApparentPower the MaxApparentPower
	 * @param p                the proportional gain
	 * @param i                the integral gain
	 * @param d                the derivative gain
	 */
	public DummyPower(int maxApparentPower, double p, double i, double d) {
		this(maxApparentPower, new PidFilter(p, i, d));
	}

	/**
	 * Registers a {@link ManagedSymmetricEss} with this {@link DummyPower}.
	 * 
	 * @param ess the {@link ManagedSymmetricEss}
	 */
	public void addEss(ManagedSymmetricEss ess) {
		this.esss.add(ess);
	}

	@Override
	public Constraint addConstraint(Constraint constraint) {
		return null;
	}

	@Override
	public Constraint addConstraintAndValidate(Constraint constraint) throws PowerException {
		return null;
	}

	@Override
	public Constraint createSimpleConstraint(String description, ManagedSymmetricEss ess, SingleOrAllPhase phase,
			Pwr pwr, Relationship relationship, int value) {
		return null;
	}

	@Override
	public void removeConstraint(Constraint constraint) {

	}

	public void setMaxApparentPower(int maxApparentPower) {
		this.maxApparentPower = maxApparentPower;
	}

	@Override
	public int getMaxPower(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr) {
		var result = this.maxApparentPower;
		for (var e : this.esss) {
			result = minInt(result, e.getMaxApparentPower().get(), e.getAllowedDischargePower().get());
		}
		return result;
	}

	@Override
	public int getMinPower(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr) {
		var result = this.maxApparentPower;
		for (var e : this.esss) {
			result = minInt(result, e.getMaxApparentPower().get(),
					TypeUtils.multiply(e.getAllowedChargePower().get(), -1));
		}
		return result * -1;
	}

	@Override
	public Coefficient getCoefficient(ManagedSymmetricEss ess, SingleOrAllPhase phase, Pwr pwr) {
		return null;
	}

	@Override
	public boolean isFilterEnabled() {
		return this.filter != null;
	}

	/**
	 * Configures the PT1 {@link Filter} that this {@link DummyPower} creates for
	 * every {@link Relationship} but EQUALS. By default it is disabled.
	 *
	 * @param clock        the {@link Clock}
	 * @param timeConstant the time constant in [ms], zero disables the filter
	 * @return myself
	 */
	public DummyPower withPt1Filter(Clock clock, int timeConstant) {
		this.pt1FilterClock = clock;
		this.pt1TimeConstant = timeConstant;
		return this;
	}

	@Override
	public Filter getFilter(String essId, String controllerId, Relationship relationship) {
		if (relationship == Relationship.EQUALS) {
			return this.filter;
		}
		return this.filters.computeIfAbsent(new FilterKey(essId, controllerId, relationship), key -> {
			if (this.pt1TimeConstant > 0) {
				return new PT1Filter(this.pt1FilterClock, this.pt1TimeConstant);
			}
			return new DisabledFilter();
		});
	}

	private static record FilterKey(String essId, String controllerId, Relationship relationship) {
	}
}
