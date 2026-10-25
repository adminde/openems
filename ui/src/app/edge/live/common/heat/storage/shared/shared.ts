import { TranslateService } from "@ngx-translate/core";
import { NavigationConstants, NavigationTree } from "src/app/shared/components/navigation/shared";
import { Converter } from "src/app/shared/components/shared/converter";
import { OeFormlyField } from "src/app/shared/components/shared/oe-formly-component";
import { ChannelAddress, EdgeConfig } from "src/app/shared/shared";
import { SharedStorage } from "../../../storage/shared/shared";

export namespace SharedThermalStorage {

    /**
     * The percentage bar fill for thermal storages, distinguishing them from
     * battery storages within the same view.
     */
    export const PERCENTAGEBAR_FILL_COLOR = "var(--ion-color-heat)";

    /**
     * Navigation tree for systems without a battery storage; with one, the
     * thermal storages are shown inside the common storage menu instead.
     */
    export function getNavigationTree(translate: TranslateService) {
        return new NavigationTree("heat-storage", { baseString: "common/heat/storage" }, { name: "flame", color: "danger" }, translate.instant("GENERAL.THERMAL_STORAGE_SYSTEM"), "label", [
            NavigationConstants.CommonNodes.HISTORY(translate),
        ], null).toConstructorParams();
    }

    /**
     * Gets the physical thermal storages, excluding the MetaThermalEss cluster
     * wrapper to avoid listing aggregated storages twice.
     */
    export function getTessComponents(config: EdgeConfig): EdgeConfig.Component[] {
        return config
            .getComponentsImplementingNature("io.openems.edge.heat.tess.api.ThermalEss")
            .filter(component => component.isEnabled && !config
                .getNatureIdsByFactoryId(component.factoryId)
                .includes("io.openems.edge.heat.tess.api.MetaThermalEss"));
    }

    export function getLinesPerTess(translate: TranslateService, tess: EdgeConfig.Component): OeFormlyField[] {
        // Positive thermal power charges the storage; negative discharges it —
        // inverted to the ActivePower convention of a battery storage.
        return [
            {
                type: "channel-line",
                channel: new ChannelAddress(tess.id, "Temperature").toString(),
                name: translate.instant("GENERAL.TEMPERATURE"),
                converter: Converter.DEZIDEGREE_CELSIUS_TO_DEGREE_CELSIUS,
            },
            {
                type: "channel-line",
                channel: new ChannelAddress(tess.id, "ThermalPower").toString(),
                name: translate.instant("GENERAL.CHARGE"),
                converter: (value) => SharedStorage.powerInKw(value, 1, true),
            },
            {
                type: "channel-line",
                channel: new ChannelAddress(tess.id, "ThermalPower").toString(),
                name: translate.instant("GENERAL.DISCHARGE"),
                converter: (value) => SharedStorage.convertChargePowerInKw(value),
            },
        ];
    }
}
