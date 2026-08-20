import { CommonModule } from "@angular/common";
import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { FormGroup, ReactiveFormsModule } from "@angular/forms";
import { IonicModule } from "@ionic/angular";
import { FormlyModule } from "@ngx-formly/core";
import { TranslateModule } from "@ngx-translate/core";
import { LiveDataService } from "src/app/edge/live/livedataservice";
import { DataService } from "src/app/shared/components/shared/dataservice";
import { AbstractFormlyComponent, OeFormlyView } from "src/app/shared/components/shared/oe-formly-component";
import { RouteService } from "src/app/shared/service/route.service";
import { ChannelAddress, CurrentData, Edge, EdgeConfig, Service, Websocket } from "src/app/shared/shared";
import { AssertionUtils } from "src/app/shared/utils/assertions/assertions.utils";
import { SharedControllerEssLimitTotalDischarge } from "../shared/shared";

type FormModel = SharedControllerEssLimitTotalDischarge.FormModel;

@Component({
    templateUrl: "../../../../../../shared/components/formly/formly-field-modal/template.html",
    standalone: true,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [CommonModule, IonicModule, ReactiveFormsModule, FormlyModule, TranslateModule],
    providers: [{ provide: DataService, useClass: LiveDataService }],
})
export class ControllerEssLimitTotalDischargeSettingsComponent extends AbstractFormlyComponent<FormModel> {

    public component: EdgeConfig.Component | null = null;

    protected override formlyWrapper: "formly-field-modal" | "formly-field-navigation" = "formly-field-navigation";
    protected routeService: RouteService = inject(RouteService);

    protected override generateView(): OeFormlyView<FormModel> {
        const edge = this.service.currentEdge();
        AssertionUtils.assertIsDefined(edge);

        const config = edge.getCurrentConfig();
        AssertionUtils.assertIsDefined(config);

        this.component = config.getComponentSafely(this.routeService.getRouteParam("componentId"));
        AssertionUtils.assertIsDefined(this.component);

        return SharedControllerEssLimitTotalDischarge.getSettingsFormlyView(this.translate, this.component, edge);
    }

    protected override getFormGroup(): FormGroup {
        return SharedControllerEssLimitTotalDischarge.getSettingsFormGroup();
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

        this.setFormControlSafelyWithChannel(
            this.form,
            "minSoc",
            currentData,
            new ChannelAddress(component.id, SharedControllerEssLimitTotalDischarge.PROPERTY_MIN_SOC),
        );
        this.setFormControlSafelyWithChannel(
            this.form,
            "forceChargeSoc",
            currentData,
            new ChannelAddress(component.id, SharedControllerEssLimitTotalDischarge.PROPERTY_FORCE_CHARGE_SOC),
        );
        this.setFormControlSafelyWithChannel(
            this.form,
            "forceChargePower",
            currentData,
            new ChannelAddress(component.id, SharedControllerEssLimitTotalDischarge.PROPERTY_FORCE_CHARGE_POWER),
        );
    }

    protected override applyChanges(
        fg: FormGroup,
        service: Service,
        websocket: Websocket,
        component: EdgeConfig.Component | null,
        edge: Edge | null,
    ): void {
        if (fg.invalid) {
            service.toast(this.translate.instant("GENERAL.INPUT_NOT_VALID"), "danger");
            return;
        }

        const minSoc: number | null = fg.controls["minSoc"].value;
        const forceChargeSoc: number | null = fg.controls["forceChargeSoc"].value;
        if (minSoc != null && forceChargeSoc != null && minSoc < forceChargeSoc) {
            service.toast(
                this.translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.RELATION_ERROR"),
                "danger",
            );
            return;
        }

        super.applyChanges(fg, service, websocket, component, edge);
    }
}
