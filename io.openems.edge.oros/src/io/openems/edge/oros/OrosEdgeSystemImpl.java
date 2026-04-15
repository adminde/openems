package io.openems.edge.oros;

import java.util.Map;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import com.google.common.collect.ImmutableMap;

import io.openems.common.channel.AccessMode;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.sum.Sum;

@Designate(ocd = Config.class, factory = false)
@Component(
		name = OrosEdgeSystem.SINGLETON_SERVICE_PID,
		immediate = true,
		property = {
				"enabled=true"
		}
)
public class OrosEdgeSystemImpl extends AbstractOpenemsComponent implements OrosEdgeSystem,
		OpenemsEdgeOem, OpenemsComponent, ModbusSlave {

	public static final String OR_OS = "OR/OS";
	public static final String OR_OS_EMS = "OR/OS EMS";
	public static final String OROS_ENERGY = "OROS Energy";
	public static final String OROS_ENERGY_FULL_NAME = "OROS Energy Europe GmbH";

	private final Map<String, String> links = new ImmutableMap.Builder<String, String>()
			.put(OROS_ENERGY, "https://orosenergy.eu")
			.build();

	private final Map<String, String> appLinks = new ImmutableMap.Builder<String, String>()
			.put("App.TimeOfUseTariff.AncillaryCosts", "")
			.put("App.TimeOfUseTariff.LuoxEnergy", "")
			.put("App.TimeOfUseTariff.Awattar", "")
			.put("App.TimeOfUseTariff.ENTSO-E", "")
			.put("App.TimeOfUseTariff.GroupeE", "")
			.put("App.TimeOfUseTariff.Hassfurt", "")
			.put("App.TimeOfUseTariff.OctopusGo", "")
			.put("App.TimeOfUseTariff.OctopusHeat", "")
			.put("App.TimeOfUseTariff.RabotCharge", "")
			.put("App.TimeOfUseTariff.Stromdao", "")
			.put("App.TimeOfUseTariff.Swisspower", "")
			.put("App.TimeOfUseTariff.Tibber", "")
			.put("App.Cloud.EnerixControl", "")
			.put("App.Cloud.Clever-PV", "")
			.put("App.Api.ModbusTcp.ReadOnly", "")
			.put("App.Api.ModbusTcp.ReadWrite", "")
			.put("App.Api.ModbusRtu.ReadOnly", "")
			.put("App.Api.ModbusRtu.ReadWrite", "")
			.put("App.Api.RestJson.ReadOnly", "")
			.put("App.Api.RestJson.ReadWrite", "")
			.put("App.Timedata.InfluxDb", "")
			.put("App.Evcs.Abl.ReadOnly", "")
			.put("App.Evcs.Alpitronic", "")
			.put("App.Evcs.Cluster", "")
			.put("App.Evcs.HardyBarth", "")
			.put("App.Evcs.HardyBarth.ReadOnly", "")
			.put("App.Evcs.IesKeywatt", "")
			.put("App.Evcs.Keba", "")
			.put("App.Evcs.Keba.ReadOnly", "")
			.put("App.Evcs.Goe.ReadOnly", "")
			.put("App.Evcs.Heidelberg.ReadOnly", "")
			.put("App.Evcs.Mennekes.ReadOnly", "")
			.put("App.Evcs.Webasto.Next", "")
			.put("App.Evcs.Webasto.Unite", "")
			.put("App.Hardware.IoGpio", "")
			.put("App.Evse.ElectricVehicle.Generic", "")
			.put("App.Evse.ChargePoint.Keba", "")
			.put("App.Evse.Controller.Cluster", "")
			.put("App.Hardware.KMtronic8Channel", "")
			.put("App.Heat.HeatPump", "")
			.put("App.Heat.CHP", "")
			.put("App.Heat.HeatingElement", "")
			.put("App.Heat.Askoma.ReadOnly", "")
			.put("App.Heat.MyPv.ReadOnly", "")
			.put("App.PvSelfConsumption.GridOptimizedCharge", "")
			.put("App.PvSelfConsumption.SelfConsumptionOptimization", "")
			.put("App.LoadControl.ManualRelayControl", "")
			.put("App.LoadControl.ThresholdControl", "")
			.put("App.Meter.Shelly", "")
			.put("App.Meter.Shelly.Meter", "")
			.put("App.Meter.Socomec", "")
			.put("App.Meter.CarloGavazzi", "")
			.put("App.Meter.PqPlus", "")
			.put("App.Meter.Janitza", "")
			.put("App.GridMeter.Janitza", "")
			.put("App.Meter.Discovergy", "")
			.put("App.Meter.PhoenixContact", "")
			.put("App.Meter.Eastron", "")
			.put("App.Meter.Kdk", "")
			.put("App.PvInverter.Fronius", "")
			.put("App.PvInverter.Kaco", "")
			.put("App.PvInverter.Kostal", "")
			.put("App.PvInverter.Sma", "")
			.put("App.PvInverter.SolarEdge", "")
			.put("App.PeakShaving.PeakShaving", "")
			.put("App.PeakShaving.PhaseAccuratePeakShaving", "")
			.put("App.PeakShaving.TimeSlotPeakShaving", "")
			.put("App.Ess.FixActivePower", "")
			.put("App.Ess.FixStateOfCharge", "")
			.put("App.Ess.PowerPlantController", "")
			.put("App.Ess.PrepareBatteryExtension", "")
			.put("App.Ess.Limiter14a", "")
			.put("App.Prediction.Default", "")
			.put("App.Prediction.UnmanagedConsumption", "")
			.put("App.Ess.SohCycle", "")
			.build();

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	protected volatile Sum sum = null;

	@Reference
	private ComponentManager componentManager;

	private Config config;

	public OrosEdgeSystemImpl() {
		super(
				OpenemsComponent.ChannelId.values(), //
				OrosEdgeSystem.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, SINGLETON_COMPONENT_ID, SINGLETON_SERVICE_PID, true);
		this.config = config;

		if (OpenemsComponent.validateSingleton(this.cm, SINGLETON_SERVICE_PID, SINGLETON_COMPONENT_ID)) {
			return;
		}
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, SINGLETON_COMPONENT_ID, SINGLETON_SERVICE_PID, true);
		this.config = config;

		if (OpenemsComponent.validateSingleton(this.cm, SINGLETON_SERVICE_PID, SINGLETON_COMPONENT_ID)) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public String getManufacturer() {
		return OROS_ENERGY_FULL_NAME;
	}

	@Override
	public String getManufacturerModel() {
		return OR_OS_EMS;
	}

	@Override
	public String getManufacturerOptions() {
		return "";
	}

	@Override
	public String getManufacturerVersion() {
		return "";
	}

	@Override
	public String getManufacturerSerialNumber() {
		return "";
	}

	@Override
	public String getManufacturerEmsSerialNumber() {
		return "";
	}

	@Override
	public SystemUpdateParams getSystemUpdateParams() {
		return new SystemUpdateParams("openems", "", "", "");
	}

	@Override
	public String getLink(String key) {
		return this.links.get(key);
	}

	@Override
	public String getAppWebsiteUrl(String appId) {
		return this.appLinks.get(appId);
	}

	@Override
	public String getBackendApiUrl() {
		return this.config.backendApiUrl();
	}

	@Override
	public String getOpenCageApiKey() {
		return this.config.openCageApiKey();
	}

	@Override
	public String getOpenMeteoApiKey() {
		return this.config.openMeteoApiKey();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				OrosEdgeSystem.getModbusSlaveNatureTable(accessMode));
	}

}
