import { Environment, Theme } from "src/environments";
import { OemMeta } from "./oem-meta";

export const theme: Omit<Environment, "url" | "backend" | "production" | "debugMode"> = {
    theme: "OpenEMS" as Theme,

    uiTitle: "OROS EMS",
    uiTitleShort: "OR:EMS",
    edgeShortName: "OROS EMS",
    edgeLongName: "OROS Energy Management System",
    defaultLanguage: "de",

    docsUrlPrefix: "https://github.com/OpenEMS/openems/blob/develop/",
    PRODUCT_TYPES: () => null,
    ...OemMeta,
};
