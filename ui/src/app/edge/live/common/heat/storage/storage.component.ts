// @ts-strict-ignore
import { Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { Modal } from "src/app/shared/components/flat/flat";
import { Converter } from "src/app/shared/components/shared/converter";
import { ChannelAddress, CurrentData, EdgeConfig, Utils } from "src/app/shared/shared";

import { ThermalStorageModalComponent } from "./modal/modal.component";

@Component({
    selector: "heat-storage",
    templateUrl: "./storage.component.html",
    standalone: false,
})
export class ThermalStorageComponent extends AbstractFlatWidget {

    public tessComponents: EdgeConfig.Component[] = [];
    public storageIconStyle: string | null = null;

    protected readonly DEZIDEGREE_CELSIUS_TO_DEGREE_CELSIUS = Converter.DEZIDEGREE_CELSIUS_TO_DEGREE_CELSIUS;
    protected modalComponent: Modal | null = null;

    protected override afterIsInitialized(): void {
        this.modalComponent = {
            component: ThermalStorageModalComponent,
        };
    }

    protected override getChannelAddresses(): ChannelAddress[] {

        const channelAddresses: ChannelAddress[] = [
            new ChannelAddress("_sum", "TessSoc"),
        ];

        // Exclude the MetaThermalEss cluster wrapper to avoid listing aggregated storages twice.
        this.tessComponents = this.config
            .getComponentsImplementingNature("io.openems.edge.heat.tess.api.ThermalEss")
            .filter(component => component.isEnabled && !this.config
                .getNatureIdsByFactoryId(component.factoryId)
                .includes("io.openems.edge.heat.tess.api.MetaThermalEss"));

        for (const component of this.tessComponents) {
            channelAddresses.push(
                new ChannelAddress(component.id, "Soc"),
                new ChannelAddress(component.id, "Temperature"),
            );
        }
        return channelAddresses;
    }

    protected override onCurrentData(currentData: CurrentData) {
        const soc = currentData.allComponents["_sum/TessSoc"];
        this.storageIconStyle = "storage-" + Utils.getStorageSocSegment(soc);
    }
}
