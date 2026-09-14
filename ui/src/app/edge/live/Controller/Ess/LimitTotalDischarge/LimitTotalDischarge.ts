// @ts-strict-ignore
import { ChangeDetectionStrategy, Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";

import { Modal } from "src/app/shared/components/flat/flat";
import { ChannelAddress, CurrentData, Utils } from "../../../../../shared/shared";
import { ControllerEssLimitTotalDischargeModalComponent } from "./modal/modal.component";
import { ControllerEssLimitTotalDischargeUtils } from "./shared/limit-total-discharge.utils";

@Component({
    selector: "Controller_Ess_LimitTotalDischarge",
    templateUrl: "./LimitTotalDischarge.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class ControllerEssLimitTotalDischargeComponent extends AbstractFlatWidget {

    public soc: number;
    public minSocSlider: number;
    public forceChargeSoc: number;
    public forceChargePower: number;
    public readonly CONVERT_WATT_TO_KILOWATT = Utils.CONVERT_WATT_TO_KILOWATT;

    protected isSliderActive: boolean = false;

    protected modalComponent: Modal | null = null;

    protected override afterIsInitialized(): void {
        this.modalComponent = this.getModalComponent();
        this.minSocSlider = this.component.properties["minSoc"];
        this.forceChargeSoc = this.component.properties["forceChargeSoc"];
        this.forceChargePower = this.component.properties["forceChargePower"];
    }
    protected getModalComponent(): Modal {
        return {
            component: ControllerEssLimitTotalDischargeModalComponent,
            componentProps: {
                component: this.component,
                edge: this.edge,
            },
        };
    };

    protected override getChannelAddresses() {
        return [
            new ChannelAddress(this.component.properties["ess.id"], "Soc"),
        ];
    }

    protected override onCurrentData(currentData: CurrentData) {
        this.soc = currentData.allComponents[this.component.properties["ess.id"] + "/Soc"];
        this.forceChargeSoc = this.component.properties["forceChargeSoc"];
        this.forceChargePower = this.component.properties["forceChargePower"];
        if (!this.isSliderActive) {
            this.minSocSlider = this.component.properties["minSoc"];
        }
    }

    protected onMinSocChange(value: number) {
        this.isSliderActive = false;
        const minSoc: number = this.component.properties["minSoc"];
        if (value === minSoc) {
            return;
        }

        const forceChargeSoc: number = this.component.properties["forceChargeSoc"];
        const newForceChargeSoc: number = ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(
            minSoc, forceChargeSoc, value);

        const properties: { name: string, value: number }[] = [{ name: "minSoc", value: value }];
        if (newForceChargeSoc !== forceChargeSoc) {
            properties.push({ name: "forceChargeSoc", value: newForceChargeSoc });
        }

        this.edge.updateComponentConfig(this.websocket, this.component.id, properties).then(() => {
            this.component.properties["minSoc"] = value;
            this.component.properties["forceChargeSoc"] = newForceChargeSoc;
            this.forceChargeSoc = newForceChargeSoc;
            this.service.toast(this.translate.instant("GENERAL.CHANGE_ACCEPTED"), "success");
        }).catch(reason => {
            this.minSocSlider = this.component.properties["minSoc"];
            this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED") + "\n" + reason.error.message, "danger");
            console.warn(reason);
        });
    }

}
