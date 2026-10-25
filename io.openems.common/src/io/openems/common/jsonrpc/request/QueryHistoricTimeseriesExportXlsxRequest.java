package io.openems.common.jsonrpc.request;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Request for 'queryHistoricTimeseriesExportXlsx'.
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
public class QueryHistoricTimeseriesExportXlsxRequest extends JsonrpcRequest {

	public static final String METHOD = "queryHistoricTimeseriesExportXlsx";

	/**
	 * Misspelled method name ("Xlxs"). Kept as an alias for older UIs to keep working.
	 * Handled identically to {@link #METHOD}.
	 *
	 * @deprecated use {@link #METHOD}
	 */
	@Deprecated
	public static final String METHOD_ALIAS = "queryHistoricTimeseriesExportXlxs";

	/**
	 * Create {@link QueryHistoricTimeseriesExportXlsxRequest} from a template
	 * {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link QueryHistoricTimeseriesExportXlsxRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static QueryHistoricTimeseriesExportXlsxRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		var p = r.getParams();
		var jTimezone = JsonUtils.getAsPrimitive(p, "timezone");
		final ZoneId timezone;
		if (jTimezone.isNumber()) {
			// For UI version before 2022.4.0
			timezone = ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(JsonUtils.getAsInt(jTimezone) * -1));
		} else {
			timezone = TimeZone.getTimeZone(JsonUtils.getAsString(jTimezone)).toZoneId();
		}

		var fromDate = JsonUtils.getAsZonedDateWithZeroTime(p, "fromDate", timezone);
		var toDate = JsonUtils.getAsZonedDateWithZeroTime(p, "toDate", timezone).plusDays(1);
		return new QueryHistoricTimeseriesExportXlsxRequest(r, fromDate, toDate);

	}

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

	private final ZonedDateTime fromDate;
	private final ZonedDateTime toDate;

	private QueryHistoricTimeseriesExportXlsxRequest(JsonrpcRequest request, ZonedDateTime fromDate,
			ZonedDateTime toDate) throws OpenemsNamedException {
		super(request, QueryHistoricTimeseriesExportXlsxRequest.METHOD);

		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	public QueryHistoricTimeseriesExportXlsxRequest(ZonedDateTime fromDate, ZonedDateTime toDate)
			throws OpenemsNamedException {
		super(QueryHistoricTimeseriesExportXlsxRequest.METHOD);

		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	@Override
	public JsonObject getParams() {
		return JsonUtils.buildJsonObject() //
				.addProperty("fromDate", QueryHistoricTimeseriesExportXlsxRequest.FORMAT.format(this.fromDate)) //
				.addProperty("toDate", QueryHistoricTimeseriesExportXlsxRequest.FORMAT.format(this.toDate)) //
				.build();
	}

	/**
	 * Gets the From-Date.
	 *
	 * @return From-Date
	 */
	public ZonedDateTime getFromDate() {
		return this.fromDate;
	}

	/**
	 * Gets the To-Date.
	 *
	 * @return To-Date
	 */
	public ZonedDateTime getToDate() {
		return this.toDate;
	}

}
