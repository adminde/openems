import { Component, ViewChild } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { TranslateService } from "@ngx-translate/core";
import { BaseChartDirective } from "ng2-charts";
import { NgxSpinnerModule } from "ngx-spinner";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { AbstractHistoryChart } from "src/app/shared/components/chart/abstracthistorychart";
import { ChartComponentsModule } from "src/app/shared/components/chart/chart.module";
import { HistoryDataErrorModule } from "src/app/shared/components/history-data-error/history-data-error.module";
import { QueryHistoricTimeseriesEnergyResponse } from "src/app/shared/jsonrpc/response/queryHistoricTimeseriesEnergyResponse";
import { ChannelAddress, ChartConstants, EdgeConfig } from "src/app/shared/shared";
import { ChartAxis, HistoryUtils, Utils, YAxisType } from "src/app/shared/utils/utils";
import { SharedThermalStorage } from "../../shared/shared";

@Component({
    selector: "oe-common-heat-storage-total-chart",
    templateUrl: "../../../../../../../shared/components/chart/abstracthistorychart.html",
    standalone: true,
    imports: [
        CommonUiModule,
        BaseChartDirective,
        ReactiveFormsModule,
        ChartComponentsModule,
        HistoryDataErrorModule,
        NgxSpinnerModule,
    ],
})
export class HeatStorageTotalChartComponent extends AbstractHistoryChart {
    @ViewChild(BaseChartDirective) private chart?: BaseChartDirective;

    public static getChartData(translate: TranslateService, chartType: string, config: EdgeConfig): HistoryUtils.ChartData {

        const tessComponents = SharedThermalStorage.getTessComponents(config);

        // Anchor the temperature axis to the configured operating band of the
        // storages, so the axis always shows the range from the lowest minimum to
        // the highest maximum temperature — readable without hovering.
        const readTemperature = (component: EdgeConfig.Component, property: string): number | null => {
            const raw = config.getPropertyFromComponent<number | string>(component, property);
            if (raw == null) {
                return null;
            }
            const value = Number(raw);
            return Number.isFinite(value) ? value : null;
        };
        const configuredMinima = tessComponents
            .map(component => readTemperature(component, "minTemperature"))
            .filter((value): value is number => value != null);
        const configuredMaxima = tessComponents
            .map(component => readTemperature(component, "maxTemperature"))
            .filter((value): value is number => value != null);
        const suggestedMin = configuredMinima.length > 0 ? Math.min(...configuredMinima) : undefined;
        const suggestedMax = configuredMaxima.length > 0 ? Math.max(...configuredMaxima) : undefined;

        const yAxes: HistoryUtils.yAxes[] = [{
            unit: YAxisType.ENERGY,
            position: "left",
            yAxisId: ChartAxis.LEFT,
        }];

        if (chartType === "line") {
            yAxes.push({
                unit: YAxisType.PERCENTAGE,
                position: "right",
                yAxisId: ChartAxis.RIGHT,
            }, {
                unit: YAxisType.TEMPERATURE,
                position: "right",
                yAxisId: ChartAxis.RIGHT_2,
                displayGrid: false,
                suggestedMin: suggestedMin,
                suggestedMax: suggestedMax,
            });
        }

        const input: HistoryUtils.InputChannel[] = [
            {
                name: "_sum/TessThermalPower",
                powerChannel: ChannelAddress.fromString("_sum/TessThermalPower"),
            },
            {
                name: "_sum/TessCharge",
                energyChannel: ChannelAddress.fromString("_sum/TessThermalChargeEnergy"),
            },
            {
                name: "_sum/TessDischarge",
                energyChannel: ChannelAddress.fromString("_sum/TessThermalDischargeEnergy"),
            },
            {
                name: "Soc",
                powerChannel: ChannelAddress.fromString("_sum/TessSoc"),
            },
        ];

        for (const component of tessComponents) {
            input.push({
                name: component.id + "/Temperature",
                powerChannel: new ChannelAddress(component.id, "Temperature"),
            });
        }

        return {
            input: input,
            output: (data: HistoryUtils.ChannelData) => {

                // Positive thermal power is charging (heat input), negative is discharging.
                const output: HistoryUtils.DisplayValue[] = [{
                    name: translate.instant("GENERAL.CHARGE"),
                    converter: () => chartType === "line"
                        ? data["_sum/TessThermalPower"]?.map(value => HistoryUtils.ValueConverter.NEGATIVE_AS_ZERO(value))
                        : data["_sum/TessCharge"],
                    nameSuffix: (energyResponse: QueryHistoricTimeseriesEnergyResponse) => energyResponse.result.data["_sum/TessThermalChargeEnergy"],
                    color: ChartConstants.Colors.GREEN,
                    stack: 0,
                },
                {
                    name: translate.instant("GENERAL.DISCHARGE"),
                    converter: () => chartType === "line"
                        ? data["_sum/TessThermalPower"]?.map(value => HistoryUtils.ValueConverter.POSITIVE_AS_ZERO_AND_INVERT_NEGATIVE(value))
                        : data["_sum/TessDischarge"],
                    nameSuffix: (energyResponse: QueryHistoricTimeseriesEnergyResponse) => energyResponse.result.data["_sum/TessThermalDischargeEnergy"],
                    color: ChartConstants.Colors.RED,
                    stack: 1,
                }];

                if (chartType === "line") {
                    output.push({
                        name: translate.instant("GENERAL.SOC"),
                        converter: () => data["Soc"]?.map(el => Utils.multiplySafely(el, 1000)),
                        color: ChartConstants.Colors.GREY,
                        borderDash: [10, 10],
                        yAxisId: ChartAxis.RIGHT,
                    });

                    output.push(...tessComponents.map((component, i): HistoryUtils.DisplayValue => ({
                        name: tessComponents.length > 1
                            ? component.alias + " " + translate.instant("GENERAL.TEMPERATURE")
                            : translate.instant("GENERAL.TEMPERATURE"),
                        converter: () => data[component.id + "/Temperature"]?.map(el => Utils.multiplySafely(el, 100)),
                        color: ChartConstants.Colors.DEFAULT_PHASES_COLORS[i % ChartConstants.Colors.DEFAULT_PHASES_COLORS.length],
                        borderDash: [3, 3],
                        yAxisId: ChartAxis.RIGHT_2,
                    })));
                }
                return output;
            },
            tooltip: {
                formatNumber: ChartConstants.NumberFormat.ZERO_TO_TWO,
            },
            yAxes: yAxes,
        };
    }

    public override getChartData() {
        return HeatStorageTotalChartComponent.getChartData(this.translate, this.chartType, this.config);
    }

    public ionViewDidEnter() {
        setTimeout(() => {
            this.chart?.chart?.resize();
            this.chart?.update();
        }, 0);
    }
}
