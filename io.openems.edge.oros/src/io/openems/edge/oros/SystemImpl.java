package io.openems.edge.oros;

import java.util.List;
import java.util.Map;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import com.google.common.collect.ImmutableMap;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.oem.AppLink;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.common.session.Language;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.sum.Sum;

@Designate(ocd = Config.class, factory = false)
@Component(
		name = System.SINGLETON_SERVICE_PID,
		immediate = true,
		property = {
				"enabled=true"
		}
)
public class SystemImpl extends AbstractOpenemsComponent implements System,
		OpenemsEdgeOem, OpenemsComponent, ModbusSlave {

	public static final String OR_OS = "OR/OS";
	public static final String OR_OS_EMS = "OR/OS EMS";
	public static final String OROS_ENERGY = "OROS Energy";
	public static final String OROS_ENERGY_FULL_NAME = "OROS Energy Europe GmbH";

	public static final String PACKAGE = "openems";
	public static final String VERSION = "2024.5.1";  // TODO: Use versioneer or something similar

	private static final List<Language> REQUIRED_LANGUAGES = List.of(
			Language.DE,
			Language.EN
	);

	private final Map<String, String> links = new ImmutableMap.Builder<String, String>()
			.put(OROS_ENERGY, "https://orosenergy.eu")
			.build();

	private final Map<String, AppLink> appLinks = new ImmutableMap.Builder<String, AppLink>()
			.put("App.TimeOfUseTariff.AncillaryCosts", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.LuoxEnergy", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.Awattar", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.ENTSO-E", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.GroupeE", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.Hassfurt", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.OctopusGo", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.OctopusHeat", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.RabotCharge", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.Stromdao", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.Swisspower", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.TimeOfUseTariff.Tibber", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Cloud.EnerixControl", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Cloud.Clever-PV", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.ModbusTcp.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.ModbusTcp.ReadWrite", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.ModbusRtu.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.ModbusRtu.ReadWrite", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.RestJson.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Api.RestJson.ReadWrite", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Timedata.InfluxDb", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Abl.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Alpitronic", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Cluster", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.HardyBarth", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.HardyBarth.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.IesKeywatt", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Keba", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Keba.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Goe.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Heidelberg.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Mennekes.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Webasto.Next", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evcs.Webasto.Unite", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Hardware.IoGpio", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evse.ElectricVehicle.Generic", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evse.ChargePoint.Keba", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evse.ChargePoint.Mennekes", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Evse.Controller.Cluster", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Hardware.KMtronic8Channel", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Heat.HeatPump", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Heat.CHP", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Heat.HeatingElement", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Heat.Askoma.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Heat.MyPv.ReadOnly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvSelfConsumption.GridOptimizedCharge", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvSelfConsumption.SelfConsumptionOptimization", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.LoadControl.ManualRelayControl", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.LoadControl.ThresholdControl", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Shelly", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Shelly.Meter", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Socomec", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.CarloGavazzi", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.PqPlus", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Janitza", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.GridMeter.Janitza", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.GridMeter.GoodWe", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.GridMeter.Kdk", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Discovergy", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.PhoenixContact", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Eastron", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Meter.Kdk", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.BeagleBoneBlack", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.Compulab", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.CM3", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.CM4", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.CM4Max", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.CM4S", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.OpenemsHardware.CM4S.Gen2", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvInverter.Fronius", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvInverter.Kaco", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvInverter.Kostal", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvInverter.Sma", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PvInverter.SolarEdge", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PeakShaving.PeakShaving", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PeakShaving.PhaseAccuratePeakShaving", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.PeakShaving.TimeSlotPeakShaving", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.FixActivePower", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.FixStateOfCharge", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.PowerPlantController", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.PrepareBatteryExtension", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.Limiter14a", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Prediction.Default", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Prediction.UnmanagedConsumption", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.put("App.Ess.SohCycle", AppLink.create()
					.emptyLink(Language.DE)
					.emptyLink(Language.EN)
			)
			.build();

	protected final ChannelManager channelManager;

	@Reference
	protected Sum sum;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	private Config config;

	public SystemImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				System.ChannelId.values()
		);
		this.channelManager = new ChannelManager(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, System.SINGLETON_COMPONENT_ID, System.SINGLETON_SERVICE_PID, true);
		this.config = config;

		if (OpenemsComponent.validateSingleton(this.cm, System.SINGLETON_SERVICE_PID, System.SINGLETON_COMPONENT_ID)) {
			return;
		}
		this.getChannelManager().activate(this.getComponentManager(), sum);
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, System.SINGLETON_COMPONENT_ID, System.SINGLETON_SERVICE_PID, true);
		this.config = config;

		if (OpenemsComponent.validateSingleton(this.cm, System.SINGLETON_SERVICE_PID, System.SINGLETON_COMPONENT_ID)) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	/**
	 * Helper wrapping class to handle everything related to Channels.
	 *
	 * @return the {@link ChannelManager}
	 */
	protected ChannelManager getChannelManager() {
		return this.channelManager;
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
		return new SystemUpdateParams(PACKAGE, VERSION, null, null);
	}

	@Override
	public String getLink(String key) {
		return this.links.get(key);
	}

	@Override
	public String getAppWebsiteUrl(String appId, Language language) {
		return OpenemsEdgeOem.getAppWebsiteUrlFromMap(this.appLinks, appId, language);
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

	/**
	 * Helper method for JUnit tests. Tests if the given {@link OpenemsEdgeOem}
	 * provides the same Website-URLs as {@link SystemImpl} - (i.e. all are
	 * not-null. See {@link #getAppWebsiteUrl(String)}
	 *
	 * @param oem the {@link OpenemsEdgeOem}
	 */
	public static void assertAllWebsiteUrlsSet(OpenemsEdgeOem oem) throws OpenemsException {
		var edge = new SystemImpl();

		for (var language : REQUIRED_LANGUAGES) {
			var missing = edge.appLinks.keySet().stream()
					.filter(appId -> oem.getAppWebsiteUrl(appId, language) == null) //
					.toList();

			if (!missing.isEmpty()) {
				throw new OpenemsException(
						"Missing " + language + " Website-URLs in Edge-OEM for [" + String.join(", ", missing) + "]");
			}
		}

		// Fallback test (e.g. unsupported language should fallback to english)
		var fallbackMissing = edge.appLinks.keySet().stream()
				.filter(appId -> oem.getAppWebsiteUrl(appId, Language.CZ) == null) //
				.toList();

		if (!fallbackMissing.isEmpty()) {
			throw new OpenemsException("Fallback does not work for [" + String.join(", ", fallbackMissing) + "]");
		}
	}

}
