import { Component } from "@angular/core";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { ComponentsBaseModule } from "src/app/shared/components/components.module";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { ChannelAddress } from "src/app/shared/shared";
import { HeatStorageTotalChartComponent } from "../../../heat/storage/history/chart/totalchart";
import { StorageTotalChartComponent } from "../chart/totalchart";

@Component({
    templateUrl: "./new-navigation.html",
    standalone: true,
    imports: [
        CommonUiModule,
        ComponentsBaseModule,
        StorageTotalChartComponent,
        HeatStorageTotalChartComponent,
    ],
})
export class CommonStorageHistoryComponent extends AbstractModal {

    protected hasThermalStorage: boolean = false;

    protected override getChannelAddresses(): ChannelAddress[] {
        this.hasThermalStorage = this.config?.hasThermalStorage() ?? false;
        return [];
    }
}
