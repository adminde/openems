import { Component } from "@angular/core";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { ComponentsBaseModule } from "src/app/shared/components/components.module";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { HeatStorageTotalChartComponent } from "../chart/totalchart";

@Component({
    templateUrl: "./new-navigation.html",
    standalone: true,
    imports: [
        CommonUiModule,
        ComponentsBaseModule,
        HeatStorageTotalChartComponent,
    ],
})
export class CommonHeatStorageHistoryComponent extends AbstractModal { }
