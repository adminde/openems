import { Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { AbstractHistoryChartOverview } from "src/app/shared/components/chart/abstractHistoryChartOverview";
import { ChartComponentsModule } from "src/app/shared/components/chart/chart.module";
import { HistoryDataErrorModule } from "src/app/shared/components/history-data-error/history-data-error.module";
import { PickdateComponentModule } from "src/app/shared/components/pickdate/pickdate.module";
import { LocaleProvider } from "src/app/shared/provider/locale-provider";
import { HeatStorageTotalChartComponent } from "../chart/totalchart";

@Component({
    selector: "heat-storage-chart-overview",
    templateUrl: "./overview.html",
    standalone: true,
    imports: [
        CommonUiModule,
        LocaleProvider,
        ReactiveFormsModule,
        ChartComponentsModule,
        PickdateComponentModule,
        HistoryDataErrorModule,
        HeatStorageTotalChartComponent,
    ],
})
export class CommonHeatStorageOverviewComponent extends AbstractHistoryChartOverview {
}
