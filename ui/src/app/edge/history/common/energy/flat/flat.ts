// @ts-strict-ignore
import { ChangeDetectionStrategy, Component, ElementRef, Inject, signal, ViewChild, WritableSignal, } from "@angular/core";
import { FormBuilder } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { ModalController } from "@ionic/angular";
import { TranslateService } from "@ngx-translate/core";
import { format, isSameDay, isSameMonth, isSameYear } from "date-fns";
import { saveAs } from "file-saver-es";
import { v4 as uuidv4 } from "uuid";
import { PlatFormService } from "src/app/platform.service";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { NavigationService } from "src/app/shared/components/navigation/service/navigation.service";
import { ViewUtils } from "src/app/shared/components/navigation/view/shared/shared";
import { DataService } from "src/app/shared/components/shared/dataservice";
import { QueryHistoricTimeseriesExportXlxsRequest } from "src/app/shared/jsonrpc/request/queryHistoricTimeseriesExportXlxs";
import { Base64PayloadResponse } from "src/app/shared/jsonrpc/response/base64PayloadResponse";
import { UserService } from "src/app/shared/service/user.service";
import { DateUtils } from "src/app/shared/utils/date/dateutils";
import { ChannelAddress, CurrentData, Service, Utils, Websocket } from "../../../../../shared/shared";

@Component({
    selector: "energy",
    templateUrl: "./flat.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class FlatComponent extends AbstractFlatWidget {
    private static readonly EXCEL_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8";
    private static readonly EXCEL_EXTENSION = ".xlsx";
    private static readonly MIN_CHART_HEIGHT = 300;
    protected spinnerId: string = uuidv4();
    protected autarchyValue: number | null = null;
    protected readonly isSmartphoneResolution = this.service.getIsSmartphoneResolution();
    protected readonly isApp: boolean = PlatFormService.platform !== "web";
    protected chartHeight: number | null = null;
    protected readonly chartHeightOffset: WritableSignal<number | null> = signal(null);
    private resizeObserver: ResizeObserver | null = null;

    constructor(
        @Inject(Websocket) protected override websocket: Websocket,
        @Inject(ActivatedRoute) protected override route: ActivatedRoute,
        @Inject(Service) protected override service: Service,
        @Inject(ModalController) protected override modalController: ModalController,
        @Inject(TranslateService) protected override translate: TranslateService,
        protected override dataService: DataService,
        protected override formBuilder: FormBuilder,
        protected override router: Router,
        protected override userService: UserService,
        private navigationService: NavigationService,
    ) {
        super(websocket, route, service, modalController, translate, dataService, formBuilder, router, userService);
        this.chartHeight = this.getChartHeight();
    }

    @ViewChild("flatWidget", { read: ElementRef })
    private set flatWidget(element: ElementRef<HTMLElement> | undefined) {
        this.resizeObserver?.disconnect();
        const card = element?.nativeElement.querySelector<HTMLElement>("ion-card");
        if (card == null) {
            return;
        }
        this.resizeObserver = new ResizeObserver(() => this.updateChartHeightOffset(card));
        this.resizeObserver.observe(card);
        const row = card.closest<HTMLElement>("ion-row");
        if (row != null) {
            this.resizeObserver.observe(row);
        }
    }

    /**
     * Gets the height of all columns that follow the column of the card in the grid row.
     *
     * @param card The card that contains the chart
     * @returns The height in px, or 0 if no widget follows the card
     */
    private static getWidgetsHeight(card: HTMLElement): number {
        const widgets: DOMRect[] = [];
        let sibling = card.closest("ion-col")?.nextElementSibling ?? null;
        while (sibling != null) {
            if (sibling.tagName === "ION-COL") {
                widgets.push(sibling.getBoundingClientRect());
            }
            sibling = sibling.nextElementSibling;
        }
        if (widgets.length === 0) {
            return 0;
        }
        const top = Math.min(...widgets.map((widget) => widget.top));
        const bottom = Math.max(...widgets.map((widget) => widget.bottom));
        return bottom - top;
    }

    public getChartHeight(): number {
        return ViewUtils.getChartContentHeightInVh(this.navigationService.position());
    }

    public override ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        super.ngOnDestroy();
    }

    protected override onCurrentData(currentData: CurrentData) {
        this.autarchyValue = Utils.calculateAutarchy(
            currentData.allComponents["_sum/GridBuyActiveEnergy"] / 1000,
            currentData.allComponents["_sum/ConsumptionActiveEnergy"] / 1000,
        );
    }

    protected override getChannelAddresses(): ChannelAddress[] {
        return [
            new ChannelAddress("_sum", "GridBuyActiveEnergy"),
            new ChannelAddress("_sum", "ConsumptionActiveEnergy"),
        ];
    }

    /** Export historic data to Excel file. */
    protected exportToXlxs() {
        if (this.isApp) {
            this.service.toast(this.translate.instant("APP.FUNCTIONALITY_TEMPORARILY_NOT_AVAILABLE"), "warning");
            return;
        }

        this.service.getCurrentEdge().then((edge) => {
            this.service.startSpinner(this.spinnerId);
            edge.sendRequest(
                this.websocket,
                new QueryHistoricTimeseriesExportXlxsRequest(
                    DateUtils.maxDate(this.service.historyPeriod.value.from, this.edge?.firstSetupProtocol),
                    this.service.historyPeriod.value.to,
                ),
            )
                .then((response) => {
                    const r = response as Base64PayloadResponse;
                    const binary = atob(r.result.payload.replace(/\s/g, ""));
                    const len = binary.length;
                    const buffer = new ArrayBuffer(len);
                    const view = new Uint8Array(buffer);
                    for (let i = 0; i < len; i++) {
                        view[i] = binary.charCodeAt(i);
                    }
                    const data: Blob = new Blob([view], {
                        type: FlatComponent.EXCEL_TYPE,
                    });

                    let fileName = "Export-" + edge.id + "-";
                    const dateFrom = this.service.historyPeriod.value.from;
                    const dateTo = this.service.historyPeriod.value.to;
                    if (isSameDay(dateFrom, dateTo)) {
                        fileName += format(dateFrom, "dd.MM.yyyy");
                    } else if (isSameMonth(dateFrom, dateTo)) {
                        fileName += format(dateFrom, "dd.") + "-" + format(dateTo, "dd.MM.yyyy");
                    } else if (isSameYear(dateFrom, dateTo)) {
                        fileName += format(dateFrom, "dd.MM.") + "-" + format(dateTo, "dd.MM.yyyy");
                    } else {
                        fileName += format(dateFrom, "dd.MM.yyyy") + "-" + format(dateTo, "dd.MM.yyyy");
                    }
                    fileName += FlatComponent.EXCEL_EXTENSION;
                    saveAs(data, fileName);
                })
                .catch((reason) => {
                    console.warn(reason);
                })
                .finally(() => {
                    this.service.stopSpinner(this.spinnerId);
                });
        });
    }

    /**
     * Measures the space that the view needs besides the chart, so that the card and the widgets below it fit into the
     * view together.
     *
     * The space between the top of the grid and the card is kept below the card as well. All distances are independent
     * of the scroll position and of the current chart height. The chart keeps at least {@link MIN_CHART_HEIGHT}.
     *
     * @param card The card that contains the chart
     */
    private updateChartHeightOffset(card: HTMLElement): void {
        const chartArea = card.querySelector<HTMLElement>("#chart-area");
        const grid = card.closest<HTMLElement>("ion-grid");
        if (chartArea == null || grid == null) {
            return;
        }
        const cardRect = card.getBoundingClientRect();
        const spaceAboveCard = cardRect.top - grid.getBoundingClientRect().top;
        const cardWithoutChart = cardRect.height - chartArea.getBoundingClientRect().height;
        const offset = cardWithoutChart + 2 * spaceAboveCard + FlatComponent.getWidgetsHeight(card);
        const maxOffset =
            ViewUtils.getViewHeightInPx(this.navigationService.position()) - FlatComponent.MIN_CHART_HEIGHT;
        this.chartHeightOffset.set(Math.round(Math.max(0, Math.min(offset, maxOffset))));
    }
}
