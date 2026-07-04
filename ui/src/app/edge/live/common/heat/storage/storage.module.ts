import { NgModule } from "@angular/core";
import { TranslateModule } from "@ngx-translate/core";

import { ComponentsBaseModule } from "src/app/shared/components/components.module";
import { ModalComponentsModule } from "src/app/shared/components/modal/modal.module";
import { PipeModule } from "src/app/shared/pipe/pipe.module";
import { ThermalStorageComponent } from "./storage.component";
import { ThermalStorageModalComponent } from "./modal/modal.component";

@NgModule({
    imports: [
        ComponentsBaseModule,
        ModalComponentsModule,
        PipeModule,
        TranslateModule,
    ],
    declarations: [
        ThermalStorageComponent,
        ThermalStorageModalComponent,
    ],
    exports: [
        ThermalStorageComponent,
        ThermalStorageModalComponent,
    ],
})
export class ThermalStorageLiveModule { }
