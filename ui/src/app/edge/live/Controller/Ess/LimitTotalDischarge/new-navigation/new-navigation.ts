import { CommonModule } from "@angular/common";
import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { FormGroup, ReactiveFormsModule } from "@angular/forms";
import { IonicModule } from "@ionic/angular";
import { FormlyModule } from "@ngx-formly/core";
import { TranslateModule } from "@ngx-translate/core";
import { takeUntil } from "rxjs/operators";
import { LiveDataService } from "src/app/edge/live/livedataservice";
import { DataService } from "src/app/shared/components/shared/dataservice";
import { AbstractFormlyComponent, OeFormlyView } from "src/app/shared/components/shared/oe-formly-component";
import { RouteService } from "src/app/shared/service/route.service";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { AssertionUtils } from "src/app/shared/utils/assertions/assertions.utils";
import { ControllerEssLimitTotalDischargeUtils } from "../shared/limit-total-discharge.utils";
import { SharedControllerEssLimitTotalDischarge } from "../shared/shared";

type FormModel = SharedControllerEssLimitTotalDischarge.FormModel;

@Component({
    selector: "oe-controller-ess-limit-total-discharge",
    templateUrl: "../../../../../../shared/components/formly/formly-field-modal/template.html",
    standalone: true,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [CommonModule, IonicModule, ReactiveFormsModule, FormlyModule, TranslateModule],
    providers: [{ provide: DataService, useClass: LiveDataService }],
})
export class ControllerEssLimitTotalDischargeHomeComponent extends AbstractFormlyComponent<FormModel> {

    public component: EdgeConfig.Component | null = null;

    protected override formlyWrapper: "formly-field-modal" | "formly-field-navigation" = "formly-field-navigation";
    protected routeService: RouteService = inject(RouteService);

    /** The Min-SoC and Force-Charge-SoC currently active on the Edge. */
    private appliedMinSoc: number | null = null;
    private appliedForceChargeSoc: number | null = null;

    protected override generateView(): OeFormlyView<FormModel> {
        const edge = this.service.currentEdge();
        AssertionUtils.assertIsDefined(edge);

        const config = edge.getCurrentConfig();
        AssertionUtils.assertIsDefined(config);

        this.component = config.getComponentSafely(this.routeService.getRouteParam("componentId"));
        AssertionUtils.assertIsDefined(this.component);

        return SharedControllerEssLimitTotalDischarge.getHomeFormlyView(this.translate, this.component, edge);
    }

    protected override getFormGroup(): FormGroup {
        const formGroup = SharedControllerEssLimitTotalDischarge.getHomeFormGroup();

        formGroup.controls["minSoc"].valueChanges
            .pipe(takeUntil(this.stopOnDestroy))
            .subscribe((minSoc: number | null) => this.pullForceChargeSoc(formGroup, minSoc));

        return formGroup;
    }

    protected override async getChannelAddresses(): Promise<ChannelAddress[]> {
        return SharedControllerEssLimitTotalDischarge.getChannelAddresses(
            this.service,
            this.routeService,
            this.component,
        );
    }

    protected override onCurrentData(currentData: CurrentData): void {
        const component = this.component;
        AssertionUtils.assertIsDefined(component);

        const minSocChannel = new ChannelAddress(
            component.id,
            SharedControllerEssLimitTotalDischarge.PROPERTY_MIN_SOC,
        );
        const forceChargeSocChannel = new ChannelAddress(
            component.id,
            SharedControllerEssLimitTotalDischarge.PROPERTY_FORCE_CHARGE_SOC,
        );

        const minSoc: number | null = currentData.allComponents[minSocChannel.toString()] ?? null;
        const forceChargeSoc: number | null = currentData.allComponents[forceChargeSocChannel.toString()] ?? null;
        if (minSoc != null && forceChargeSoc != null) {
            this.appliedMinSoc = minSoc;
            this.appliedForceChargeSoc = forceChargeSoc;
        }

        this.setFormControlSafelyWithChannel(this.form, "minSoc", currentData, minSocChannel);
        this.setFormControlSafelyWithChannel(this.form, "forceChargeSoc", currentData, forceChargeSocChannel);
    }

    /**
     * Moves the Force-Charge-SoC along with a Min-SoC selected by the user.
     *
     * Values written by an incoming channel update leave the control pristine
     * and must not trigger the coupling, otherwise the form would appear
     * modified without any user interaction.
     */
    private pullForceChargeSoc(formGroup: FormGroup, minSoc: number | null): void {
        const minSocControl = formGroup.controls["minSoc"];
        const forceChargeSocControl = formGroup.controls["forceChargeSoc"];

        if (
            minSoc == null ||
            minSocControl.pristine ||
            this.appliedMinSoc == null ||
            this.appliedForceChargeSoc == null
        ) {
            return;
        }

        const forceChargeSoc = ControllerEssLimitTotalDischargeUtils.deriveForceChargeSoc(
            this.appliedMinSoc,
            this.appliedForceChargeSoc,
            minSoc,
        );
        if (forceChargeSocControl.value === forceChargeSoc) {
            return;
        }

        forceChargeSocControl.setValue(forceChargeSoc);
        forceChargeSocControl.markAsDirty();
    }
}
