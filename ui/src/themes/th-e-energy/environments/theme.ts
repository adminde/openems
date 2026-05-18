import { Environment, Theme } from "src/environments";
import { OemMeta } from "./oem-meta";

export const theme: Omit<Environment, "url" | "backend" | "production" | "debugMode"> = {
    theme: "TH-E Energy" as Theme,

    uiTitle: "TH-E EMS",
    uiTitleShort: "TH-E EMS",
    edgeShortName: "TH-E",
    edgeLongName: "Thermal/Electric",
    headerLogo: "th-e-logo.svg",
    headerTitleShow: false,
    footerTextHtml: "TH-E EMS",
    defaultLanguage: "en",

    docsUrlPrefix: "https://github.com/adminde/openems/blob/develop/",
    PRODUCT_TYPES: () => null,
    ...OemMeta,
};
