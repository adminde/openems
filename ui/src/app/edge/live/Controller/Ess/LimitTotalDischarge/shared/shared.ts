import { FormControl, FormGroup, Validators } from "@angular/forms";
import { TranslateService } from "@ngx-translate/core";
import { NavigationConstants, NavigationTree } from "src/app/shared/components/navigation/shared";
import { Converter } from "src/app/shared/components/shared/converter";
import { Name } from "src/app/shared/components/shared/name";
import type { OeFormlyField, OeFormlyView } from "src/app/shared/components/shared/oe-formly-component";
import type { RouteService } from "src/app/shared/service/route.service";
import { ChannelAddress, Utils } from "src/app/shared/shared";
import type { Edge, EdgeConfig, Service } from "src/app/shared/shared";
import { Role } from "src/app/shared/type/role";
import { AssertionUtils } from "src/app/shared/utils/assertions/assertions.utils";

export namespace SharedControllerEssLimitTotalDischarge {

    export const PROPERTY_MIN_SOC = "_PropertyMinSoc";
    export const PROPERTY_FORCE_CHARGE_SOC = "_PropertyForceChargeSoc";
    export const PROPERTY_FORCE_CHARGE_POWER = "_PropertyForceChargePower";

    export type FormModel = {
        minSoc: number;
        forceChargeSoc: number;
        forceChargePower: number;
    };

    /**
     * Gets the view for the navigation home page.
     *
     * Mirrors the flat widget of the classic live view: state of charge,
     * Force-Charge values as read only information and the Min-SoC slider.
     *
     * @param translate The translate service
     * @param component The controller component
     * @param edge The current edge
     * @returns The formly view
     */
    export function getHomeFormlyView(
        translate: TranslateService,
        component: EdgeConfig.Component,
        edge: Edge,
    ): OeFormlyView<FormModel> {
        return {
            title: component.alias,
            icon: { name: "battery-dead-outline", color: "normal", size: "large" },
            lines: [
                ...getSocLines(translate, component),
                {
                    type: "value-from-form-control-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.FORCE_CHARGE_SOC"),
                    controlName: "forceChargeSoc",
                    converter: Converter.STATE_IN_PERCENT,
                },
                {
                    type: "channel-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.FORCE_CHARGE_POWER"),
                    channel: new ChannelAddress(component.id, PROPERTY_FORCE_CHARGE_POWER).toString(),
                    converter: Utils.CONVERT_WATT_TO_KILOWATT,
                },
                { type: "horizontal-line" },
                {
                    type: "value-from-form-control-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.MIN_SOC"),
                    controlName: "minSoc",
                    converter: Converter.STATE_IN_PERCENT,
                },
                {
                    type: "range-button-from-form-control-line",
                    controlName: "minSoc",
                    properties: {
                        tickMin: 0,
                        tickMax: 100,
                        step: 1,
                        tickFormatter: (value) => Converter.STATE_IN_PERCENT(value),
                        pinFormatter: (value) => Converter.STATE_IN_PERCENT(value),
                    },
                },
            ],
            component: component,
            edge: edge,
        };
    }

    /**
     * Gets the view for the settings sub page.
     *
     * Mirrors the modal of the classic live view: state of charge as read only
     * information and the three properties as editable inputs for owners.
     *
     * @param translate The translate service
     * @param component The controller component
     * @param edge The current edge
     * @returns The formly view
     */
    export function getSettingsFormlyView(
        translate: TranslateService,
        component: EdgeConfig.Component,
        edge: Edge,
    ): OeFormlyView<FormModel> {
        const inputLines: OeFormlyField<FormModel>[] = edge.roleIsAtLeast(Role.OWNER)
            ? [
                {
                    type: "input-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.FORCE_CHARGE_POWER"),
                    controlName: "forceChargePower",
                    properties: { unit: "W", type: "number" },
                },
                {
                    type: "input-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.FORCE_CHARGE_SOC"),
                    controlName: "forceChargeSoc",
                    properties: { unit: "%", type: "number" },
                },
                {
                    type: "input-line",
                    name: translate.instant("EDGE.INDEX.WIDGETS.LIMIT_TOTAL_DISCHARGE.MIN_SOC"),
                    controlName: "minSoc",
                    properties: { unit: "%", type: "number" },
                },
            ]
            : [];

        return {
            title: component.alias,
            icon: { name: "battery-dead-outline", color: "normal", size: "large" },
            lines: [
                ...getSocLines(translate, component),
                ...inputLines,
            ],
            component: component,
            edge: edge,
        };
    }

    function getSocLines(
        translate: TranslateService,
        component: EdgeConfig.Component,
    ): OeFormlyField<FormModel>[] {
        const essId = component.getPropertyFromComponent<string>("ess.id");
        if (essId == null) {
            return [];
        }
        return [
            {
                type: "channel-line",
                name: translate.instant("GENERAL.SOC"),
                channel: new ChannelAddress(essId, "Soc").toString(),
                converter: Converter.STATE_IN_PERCENT,
            },
            { type: "horizontal-line" },
        ];
    }

    export function getHomeFormGroup(): FormGroup {
        return new FormGroup({
            minSoc: new FormControl(null),
            forceChargeSoc: new FormControl(null),
        });
    }

    export function getSettingsFormGroup(): FormGroup {
        return new FormGroup({
            minSoc: new FormControl(null, [
                Validators.required,
                Validators.pattern("^(?:[1-9][0-9]*|0)$"),
                Validators.min(0),
                Validators.max(100),
            ]),
            forceChargeSoc: new FormControl(null, [
                Validators.required,
                Validators.pattern("^(?:[1-9][0-9]*|0)$"),
                Validators.min(0),
                Validators.max(100),
            ]),
            forceChargePower: new FormControl(null, [
                Validators.pattern("^(?:[1-9][0-9]*|0)?$"),
            ]),
        });
    }

    export function getChannelAddresses(
        service: Service,
        routeService: RouteService,
        component: EdgeConfig.Component | null = null,
    ): Promise<ChannelAddress[]> {
        const edge = service.currentEdge();
        const config = edge.getCurrentConfig();
        AssertionUtils.assertIsDefined(config);

        const limitTotalDischargeComponent =
            component ?? config.getComponentSafely(routeService.getRouteParam("componentId"));
        AssertionUtils.assertIsDefined(limitTotalDischargeComponent);

        const channelAddresses = [
            new ChannelAddress(limitTotalDischargeComponent.id, PROPERTY_MIN_SOC),
            new ChannelAddress(limitTotalDischargeComponent.id, PROPERTY_FORCE_CHARGE_SOC),
            new ChannelAddress(limitTotalDischargeComponent.id, PROPERTY_FORCE_CHARGE_POWER),
        ];

        const essId = limitTotalDischargeComponent.getPropertyFromComponent<string>("ess.id");
        if (essId != null) {
            channelAddresses.push(new ChannelAddress(essId, "Soc"));
        }

        return Promise.resolve(channelAddresses);
    }

    export function getNavigationTree(
        translate: TranslateService,
        component: EdgeConfig.Component,
    ): ConstructorParameters<typeof NavigationTree> {
        return new NavigationTree(
            component.id,
            { baseString: "controller/limit-total-discharge/" + component.id },
            { name: "battery-dead-outline", color: "normal" },
            Name.METER_ALIAS_OR_ID(component),
            "label",
            [
                NavigationConstants.CommonNodes.SETTINGS(translate),
                NavigationConstants.CommonNodes.INFO(translate, { source: component.id }),
            ],
            null,
        ).toConstructorParams();
    }
}
