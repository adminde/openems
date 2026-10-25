// @ts-strict-ignore
import { format } from "date-fns";
import { JsonrpcRequest } from "../base";

/**
 * Queries historic timeseries data; exports to Xlsx (Excel) file.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "queryHistoricTimeseriesExportXlsx",
 *   "params": {
 *     "timezone": Number,
 *     "fromDate": YYYY-MM-DD,
 *     "toDate": YYYY-MM-DD
 *   }
 * }
 * </pre>
 */
export class QueryHistoricTimeseriesExportXlsxRequest extends JsonrpcRequest {

    private static METHOD: string = "queryHistoricTimeseriesExportXlsx";

    public constructor(
        private fromDate: Date,
        private toDate: Date,
    ) {
        super(QueryHistoricTimeseriesExportXlsxRequest.METHOD, {
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
            fromDate: format(fromDate, "yyyy-MM-dd"),
            toDate: format(toDate, "yyyy-MM-dd"),
        });
        // delete local fields, otherwise they are sent with the JSON-RPC Request
        delete this.fromDate;
        delete this.toDate;
    }

}
