// @ts-strict-ignore
import { Component } from "@angular/core";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { DataService } from "src/app/shared/components/shared/dataservice";
import { ChannelAddress, EdgeConfig } from "src/app/shared/shared";

import { LiveDataService } from "../../../../livedataservice";

@Component({
    templateUrl: "./modal.component.html",
    styleUrls: ["./modal.component.scss"],
    standalone: false,
    providers: [
        { provide: DataService, useClass: LiveDataService },
    ],
})
export class ThermalStorageModalComponent extends AbstractModal {

    public tessComponents: EdgeConfig.Component[] = [];

    protected override getChannelAddresses(): ChannelAddress[] {

        // Exclude the MetaThermalEss cluster wrapper to avoid listing aggregated storages twice.
        this.tessComponents = this.config
            .getComponentsImplementingNature("io.openems.edge.heat.tess.api.ThermalEss")
            .filter(component => component.isEnabled && !this.config
                .getNatureIdsByFactoryId(component.factoryId)
                .includes("io.openems.edge.heat.tess.api.MetaThermalEss"));

        const channelAddresses: ChannelAddress[] = [];
        for (const component of this.tessComponents) {
            channelAddresses.push(
                new ChannelAddress(component.id, "Soc"),
                new ChannelAddress(component.id, "Temperature"),
                new ChannelAddress(component.id, "MinTargetTemperature"),
                new ChannelAddress(component.id, "MaxTargetTemperature"),
            );
        }
        return channelAddresses;
    }
}
