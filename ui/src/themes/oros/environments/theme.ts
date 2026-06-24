import { Environment, Theme } from "src/environments";
import { OemMeta } from "./oem-meta";

export const theme: Omit<Environment, "url" | "backend" | "production" | "debugMode"> = {
    theme: "OpenEMS" as Theme,

    uiTitle: "OROS ENERGY",
    uiTitleShort: "OROS",
    edgeShortName: "OR/OS EMS",
    edgeLongName: "OR/OS Energy Management System",
    headerLogo: "oros-logo-black-text.svg",
    headerTitleShow: false,
    footerTextHtml: "OR/OS",
    defaultLanguage: "de",

    docsUrlPrefix: "https://github.com/OpenEMS/openems/blob/develop/",
    PRODUCT_TYPES: () => null,
    ...OemMeta,
};
