import { Environment, Theme } from "src/environments";
import { OemMeta } from "./oem-meta";

export const theme: Omit<
    Environment,
    "url" | "backend" | "production" | "debugMode"
> = {
    theme: "OpenEMS" as Theme,

    uiTitle: "OROS ENERGY",
    uiTitleShort: "OROS",
    footerTextHtml: "OR/OS",
    edgeShortName: "OR/OS",
    edgeLongName: "OROS Energy Operating System",

    docsUrlPrefix: "https://github.com/OpenEMS/openems/blob/develop/",
    PRODUCT_TYPES: () => null,
    ...OemMeta,
};
