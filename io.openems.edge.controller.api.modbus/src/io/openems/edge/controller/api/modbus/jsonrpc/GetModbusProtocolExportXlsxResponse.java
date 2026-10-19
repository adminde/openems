package io.openems.edge.controller.api.modbus.jsonrpc;

import static io.openems.edge.controller.api.modbus.CommonConfig.DEFAULT_UNIT_ID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.dhatim.fastexcel.Color;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.response.Base64PayloadResponse;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.common.modbusslave.ModbusRecord;
import io.openems.edge.common.modbusslave.ModbusRecordFloat32;
import io.openems.edge.common.modbusslave.ModbusRecordFloat32Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordFloat64;
import io.openems.edge.common.modbusslave.ModbusRecordFloat64Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordInt16;
import io.openems.edge.common.modbusslave.ModbusRecordInt16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordInt32;
import io.openems.edge.common.modbusslave.ModbusRecordInt32Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordInt64;
import io.openems.edge.common.modbusslave.ModbusRecordInt64Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordString16;
import io.openems.edge.common.modbusslave.ModbusRecordString16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordUint16;
import io.openems.edge.common.modbusslave.ModbusRecordUint16BlockLength;
import io.openems.edge.common.modbusslave.ModbusRecordUint16Hash;
import io.openems.edge.common.modbusslave.ModbusRecordUint16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordUint32;
import io.openems.edge.common.modbusslave.ModbusRecordUint32Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordUint64;
import io.openems.edge.common.modbusslave.ModbusRecordUint64Reserved;
import io.openems.edge.common.modbusslave.ModbusType;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.modbus.CommonConfig;

/**
 * Represents a JSON-RPC Response for 'getModbusProtocolExportXlsx'.
 *
 * <p>
 * The workbook starts with a general protocol description, followed by one
 * sheet for the generic header and one sheet per Component.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "payload": Base64-String
 *   }
 * }
 * </pre>
 */
public class GetModbusProtocolExportXlsxResponse extends Base64PayloadResponse {

	public GetModbusProtocolExportXlsxResponse(UUID id, CommonConfig config, AccessMode accessMode,
			TreeMap<Integer, String> components, TreeMap<Integer, ModbusRecord> records) throws OpenemsException {
		super(id, generatePayload(config, accessMode, components, records));
	}

	private static final String SHEET_PROTOCOL = "Protocol";
	private static final String SHEET_HEADER = "Header";
	private static final String DATA_TYPES = "Data types";
	private static final String ACCESS_MODES = "Access modes";

	/**
	 * Fixed titles of singleton Components, whose aliases are technical service
	 * PIDs like "Core.Sum".
	 */
	private static final Map<String, String> SINGLETON_TITLES = Map.of(//
			Meta.SINGLETON_COMPONENT_ID, SHEET_HEADER, //
			Sum.SINGLETON_COMPONENT_ID, "Summary");

	private static final int COL_ADDRESS = 0;
	private static final int COL_NAME = 1;
	private static final int COL_TYPE = 2;
	private static final int COL_VALUE_DESCRIPTION = 3;
	private static final int COL_UNIT = 4;
	private static final int COL_GAIN = 5;
	private static final int COL_ACCESS = 6;

	private static final int ROW_TITLE = 0;
	private static final int ROW_HEADER = 1;
	private static final int ROW_FIRST_RECORD = 2;

	private static final String HEADER_FILL = Color.GRAY2;
	private static final String SUBHEADER_FILL = Color.GRAY1;

	private static final double ROW_HEIGHT = 22;
	private static final double ROW_HEIGHT_TITLE = 32;
	private static final double ROW_HEIGHT_TABLE_TITLE = 30;
	private static final double LINE_HEIGHT = 15;
	private static final int DESCRIPTION_WIDTH = 80;
	private static final int DESCRIPTION_CHARACTERS_PER_LINE = 75;

	private static final int PROTOCOL_LAST_COLUMN = 6;
	private static final int MAX_REGISTERS_READ = 125;
	private static final int MAX_REGISTERS_WRITE = 123;
	private static final int MAX_SHEET_NAME_LENGTH = 31;
	private static final Pattern INVALID_SHEET_NAME_CHARACTERS = Pattern.compile("[\\[\\]:*?/\\\\]");
	private static final Pattern SHEET_NAME_BOUNDARY = Pattern.compile("^[\\s']+|[\\s']+$");

	/**
	 * The address range of a Component sheet.
	 *
	 * @param sheetName    the name of the sheet
	 * @param firstAddress the first address
	 * @param lastAddress  the last address
	 */
	private record AddressRange(String sheetName, int firstAddress, int lastAddress) {
	}

	private static byte[] generatePayload(CommonConfig config, AccessMode accessMode,
			TreeMap<Integer, String> components, TreeMap<Integer, ModbusRecord> records) throws OpenemsException {
		try {
			try (var os = new ByteArrayOutputStream()) {
				Workbook wb = null;
				try {
					wb = new Workbook(os, getProductName(config) + " " + getInterfaceName(config), "1.0");
					var protocolSheet = wb.newWorksheet(SHEET_PROTOCOL);
					var addressRanges = addComponentSheets(wb, components, records);
					addProtocolSheet(protocolSheet, config, accessMode, addressRanges);
				} finally {
					if (wb != null) {
						wb.finish();
					}
				}
				os.flush();
				return os.toByteArray();
			}
		} catch (IOException e) {
			throw new OpenemsException("Unable to generate Xlsx payload: " + e.getMessage());
		}
	}

	/**
	 * Adds one sheet per block of the Modbus table.
	 *
	 * <p>
	 * The first block covers the generic header and the Meta Component. Every
	 * Component start address in the given components map starts a new block,
	 * which ends right before the next block. Singleton Components get a fixed
	 * title. All other Components are titled by their alias with the
	 * Component-ID as fallback.
	 *
	 * @param wb         the {@link Workbook}
	 * @param components the Component aliases by start address
	 * @param records    the {@link ModbusRecord}s by address
	 * @return the {@link AddressRange}s of the sheets in address order
	 */
	private static List<AddressRange> addComponentSheets(Workbook wb, TreeMap<Integer, String> components,
			TreeMap<Integer, ModbusRecord> records) {
		var result = new ArrayList<AddressRange>();
		if (records.isEmpty()) {
			return result;
		}
		var starts = new TreeSet<Integer>(components.keySet());
		starts.add(records.firstKey());
		var usedSheetNames = new HashSet<String>();
		usedSheetNames.add(SHEET_PROTOCOL.toLowerCase(Locale.ROOT));
		for (var start : starts) {
			var next = starts.higher(start);
			var sheetRecords = next == null //
					? records.tailMap(start, true) //
					: records.subMap(start, next);
			var lastAddress = next == null //
					? getLastAddress(sheetRecords.lastKey(), sheetRecords.get(sheetRecords.lastKey())) //
					: next - 1;
			var componentId = sheetRecords.values().stream() //
					.map(ModbusRecord::getComponentId) //
					.filter(Objects::nonNull) //
					.findFirst() //
					.orElse(null);
			var title = componentId == null ? SHEET_HEADER : SINGLETON_TITLES.get(componentId);
			var blockLengthName = title == null ? null : "Length of block \"" + title + "\"";
			if (title == null) {
				var alias = components.get(start);
				title = alias != null && !alias.isBlank() ? alias : componentId;
			}
			var sheetName = getSheetName(title, usedSheetNames);
			addComponentSheet(wb.newWorksheet(sheetName), title + ", addresses " + start + " to " + lastAddress,
					sheetRecords, blockLengthName);
			result.add(new AddressRange(sheetName, start, lastAddress));
		}
		return result;
	}

	/**
	 * Adds the records of one block to a sheet. Reserved records with
	 * consecutive addresses are combined to one row.
	 *
	 * @param ws              the {@link Worksheet}
	 * @param title           the sheet title
	 * @param records         the {@link ModbusRecord}s by address
	 * @param blockLengthName the name of the first block length record, or null
	 *                        to keep its own name
	 */
	private static void addComponentSheet(Worksheet ws, String title, SortedMap<Integer, ModbusRecord> records,
			String blockLengthName) {
		ws.width(COL_ADDRESS, 18);
		ws.width(COL_NAME, 50);
		ws.width(COL_TYPE, 12);
		ws.width(COL_VALUE_DESCRIPTION, DESCRIPTION_WIDTH);
		ws.width(COL_UNIT, 30);
		ws.width(COL_GAIN, 10);
		ws.width(COL_ACCESS, 10);

		addSheetTitle(ws, title);
		addSheetHeader(ws);
		var entries = List.copyOf(records.entrySet());
		var blockLengthPending = blockLengthName != null;
		var nextRow = ROW_FIRST_RECORD;
		var i = 0;
		while (i < entries.size()) {
			int address = entries.get(i).getKey();
			var record = entries.get(i).getValue();
			i++;
			if (isReserved(record)) {
				var lastAddress = getLastAddress(address, record);
				var type = record.getType();
				while (i < entries.size() && isReserved(entries.get(i).getValue())
						&& entries.get(i).getKey() == lastAddress + 1) {
					var nextRecord = entries.get(i).getValue();
					lastAddress = getLastAddress(entries.get(i).getKey(), nextRecord);
					type = nextRecord.getType() == type ? type : null;
					i++;
				}
				addReservedRange(ws, address, lastAddress, type, nextRow);
			} else {
				var name = record.getName();
				if (blockLengthPending && record instanceof ModbusRecordUint16BlockLength) {
					name = blockLengthName;
					blockLengthPending = false;
				}
				addRecord(ws, address, record, name, nextRow);
			}
			nextRow++;
		}
		if (nextRow > ROW_FIRST_RECORD) {
			styleTableBody(ws, ROW_FIRST_RECORD, nextRow - 1, COL_ACCESS);
			ws.range(ROW_FIRST_RECORD, COL_VALUE_DESCRIPTION, nextRow - 1, COL_VALUE_DESCRIPTION).style() //
					.wrapText(true).set();
		}
		ws.freezePane(0, ROW_FIRST_RECORD);
		ws.setAutoFilter(ROW_HEADER, COL_ADDRESS, COL_ACCESS);
	}

	private static void addSheetTitle(Worksheet sheet, String title) {
		sheet.value(ROW_TITLE, 0, title);
		sheet.style(ROW_TITLE, 0).bold().fontSize(14).horizontalAlignment("left").verticalAlignment("center")
				.indent(1).set();
		sheet.rowHeight(ROW_TITLE, ROW_HEIGHT_TITLE);
	}

	private static void addSheetHeader(Worksheet sheet) {
		sheet.value(ROW_HEADER, COL_ADDRESS, "Address");
		sheet.value(ROW_HEADER, COL_NAME, "Name");
		sheet.value(ROW_HEADER, COL_TYPE, "Type");
		sheet.value(ROW_HEADER, COL_VALUE_DESCRIPTION, "Value/Description");
		sheet.value(ROW_HEADER, COL_UNIT, "Unit");
		sheet.value(ROW_HEADER, COL_GAIN, "Gain");
		sheet.value(ROW_HEADER, COL_ACCESS, "Access");
		styleTableHeader(sheet, ROW_HEADER, COL_ACCESS);
	}

	private static void addRecord(Worksheet sheet, int address, ModbusRecord record, String name, int rowCount) {
		sheet.value(rowCount, COL_ADDRESS, address);
		sheet.value(rowCount, COL_NAME, name);
		sheet.value(rowCount, COL_TYPE, record.getType().name());
		sheet.value(rowCount, COL_VALUE_DESCRIPTION, record.getValueDescription());
		var unit = record.getUnit();
		if (unit != Unit.NONE) {
			sheet.value(rowCount, COL_UNIT, unit.toString());
		}
		var gain = record.getGain();
		if (unit != Unit.NONE || gain != 1) {
			sheet.value(rowCount, COL_GAIN, gain);
		}
		sheet.value(rowCount, COL_ACCESS, record.getAccessMode().getAbbreviation());
		if (record instanceof ModbusRecordUint16Hash) {
			// A hash marks the start of a Nature table or of the generic header.
			sheet.range(rowCount, COL_ADDRESS, rowCount, COL_ACCESS).style().bold().set();
		}
		if (record instanceof ModbusRecordUint16Hash || record instanceof ModbusRecordUint16BlockLength) {
			// Hash and length records describe the structure of the table.
			sheet.range(rowCount, COL_ADDRESS, rowCount, COL_ACCESS).style().fillColor(SUBHEADER_FILL).set();
		}
		sheet.rowHeight(rowCount, getRowHeight(record.getValueDescription()));
	}

	private static void addReservedRange(Worksheet sheet, int firstAddress, int lastAddress, ModbusType type,
			int rowCount) {
		var length = lastAddress - firstAddress + 1;
		if (length == 1) {
			sheet.value(rowCount, COL_ADDRESS, firstAddress);
		} else {
			sheet.value(rowCount, COL_ADDRESS, firstAddress + " to " + lastAddress);
			sheet.value(rowCount, COL_VALUE_DESCRIPTION, length + " registers");
		}
		sheet.value(rowCount, COL_NAME, "Reserved");
		if (type != null) {
			sheet.value(rowCount, COL_TYPE, type.name());
		}
		sheet.range(rowCount, COL_ADDRESS, rowCount, COL_ACCESS).style().italic().fontColor(Color.GRAY8).set();
		sheet.rowHeight(rowCount, ROW_HEIGHT);
	}

	/**
	 * Adds the general protocol description.
	 *
	 * @param ws            the {@link Worksheet}
	 * @param config        the {@link CommonConfig} of the Modbus Api
	 * @param accessMode    the {@link AccessMode} of the Modbus Api
	 * @param addressRanges the {@link AddressRange}s of the Component sheets
	 */
	private static void addProtocolSheet(Worksheet ws, CommonConfig config, AccessMode accessMode,
			List<AddressRange> addressRanges) {
		ws.width(0, 34);
		ws.width(1, 24);
		ws.width(2, 32);
		for (var col = 3; col <= PROTOCOL_LAST_COLUMN; col++) {
			ws.width(col, 24);
		}
		addSheetTitle(ws, getProductName(config) + " " + getInterfaceName(config) + " protocol");

		var row = addIntroduction(ws, ROW_TITLE + 1, config);
		row = addTableStructure(ws, row);
		row = addAddressRanges(ws, row, addressRanges);
		row = addColumns(ws, row);
		row = addAccessModes(ws, row);
		row = addDataTypes(ws, row);
		row = addConnection(ws, row, config, accessMode);
		row = addDataEncoding(ws, row);
		row = addFunctionCodes(ws, row);
		row = addExceptionCodes(ws, row);
		addFrameFormats(ws, row, config);
	}

	private static int addIntroduction(Worksheet ws, int row, CommonConfig config) {
		var lines = List.of(//
				"This workbook describes the " + getInterfaceName(config) + " registers of "
						+ getProductName(config) + ", as configured in its Controller Api.", //
				"This sheet explains the protocol. Every other sheet lists the registers of one Component.");
		var nextRow = row;
		for (var line : lines) {
			ws.value(nextRow, 0, line);
			ws.range(nextRow, 0, nextRow, PROTOCOL_LAST_COLUMN).merge();
			ws.style(nextRow, 0).horizontalAlignment("left").verticalAlignment("center").indent(1).set();
			ws.rowHeight(nextRow, ROW_HEIGHT);
			nextRow++;
		}
		return nextRow + 1;
	}

	private static int addTableStructure(Worksheet ws, int row) {
		return addTable(ws, row, "Table structure", //
				new Object[] { "Address", "Content" }, //
				new Object[] { "0", "Hash of \"OpenEMS\"" }, //
				new Object[] { "1", "Length of block \"" + SHEET_HEADER + "\"" }, //
				new Object[] { "2 and following",
						"Meta information like OpenEMS version and manufacturer, see sheet \"" + SHEET_HEADER + "\"" }, //
				new Object[] { "Component start + 0", "Component-ID (" + ModbusType.STRING16.name() + ")" }, //
				new Object[] { "Component start + 16", "Length of the Component block" }, //
				new Object[] { "Component start + 20", "First Nature table, further Nature tables follow" }, //
				new Object[] { "Nature start + 0", "Hash of the Nature name" }, //
				new Object[] { "Nature start + 1", "Length of the Nature table" }, //
				new Object[] { "Nature start + 2 and following", "Registers of the Nature, see the Component sheets" });
	}

	private static int addAddressRanges(Worksheet ws, int row, List<AddressRange> addressRanges) {
		var rows = new ArrayList<Object[]>();
		rows.add(new Object[] { "Sheet", "Start address", "End address" });
		for (var addressRange : addressRanges) {
			rows.add(new Object[] { addressRange.sheetName(), addressRange.firstAddress(),
					addressRange.lastAddress() });
		}
		return addTable(ws, row, "Address ranges", rows.toArray(Object[][]::new));
	}

	private static int addColumns(Worksheet ws, int row) {
		return addTable(ws, row, "Columns of the Component sheets", //
				new Object[] { "Column", "Meaning" }, //
				new Object[] { "Address",
						"Zero based register address. A range like \"1000 to 1099\" combines consecutive reserved "
								+ "registers." }, //
				new Object[] { "Name",
						"Name of the register, or the Channel address in the form Component-ID/Channel" }, //
				new Object[] { "Type", "Data type of the value, see \"" + DATA_TYPES + "\"" }, //
				new Object[] { "Value/Description",
						"Value of a fixed register, or description of the Channel with the options of an enumeration" }, //
				new Object[] { "Unit", "Unit of the value" }, //
				new Object[] { "Gain",
						"The register holds the value in its unit multiplied by the gain. Example: 12.34 kW with gain "
								+ "100 is transmitted as 1234. Written values are divided by the gain." }, //
				new Object[] { "Access", "RO, RW or WO, see \"" + ACCESS_MODES + "\"" });
	}

	private static int addAccessModes(Worksheet ws, int row) {
		return addTable(ws, row, ACCESS_MODES, //
				new Object[] { "Access", "Function codes", "Behavior" }, //
				new Object[] { "RO", "03, 04", "Readable. Write requests are acknowledged but ignored." }, //
				new Object[] { "RW", "03, 04, 06, 16", "Readable and writable" }, //
				new Object[] { "WO", "06, 16", "Writable. Reading returns the undefined value of the type." });
	}

	private static int addConnection(Worksheet ws, int row, CommonConfig config, AccessMode accessMode) {
		var rows = new ArrayList<Object[]>();
		rows.add(new Object[] { "Parameter", "Value" });
		rows.add(new Object[] { "Interface", getInterfaceName(config) });
		switch (config) {
		case CommonConfig.Tcp tcp -> {
			rows.add(new Object[] { "Port", String.valueOf(tcp.port()) });
			rows.add(new Object[] { "Max. concurrent connections", String.valueOf(tcp.maxConcurrentConnections()) });
			rows.add(new Object[] { "Unit-ID",
					DEFAULT_UNIT_ID + ". Requests with another Unit-ID are answered with exception code 02." });
		}
		case CommonConfig.Rtu rtu -> {
			rows.add(new Object[] { "Baud rate", String.valueOf(rtu.baudRate()) });
			rows.add(new Object[] { "Data bits", String.valueOf(rtu.databits()) });
			rows.add(new Object[] { "Parity", rtu.parity().name().toLowerCase(Locale.ROOT) });
			rows.add(new Object[] { "Stop bits", switch (rtu.stopbits()) {
			case ONE -> "1";
			case ONE_POINT_FIVE -> "1.5";
			case TWO -> "2";
			} });
			rows.add(new Object[] { "Slave address",
					DEFAULT_UNIT_ID + ". Requests for another slave address are not answered." });
		}
		}
		rows.add(new Object[] { "Api access", switch (accessMode) {
		case READ_ONLY -> "Read-only. All registers behave like RO.";
		case READ_WRITE, WRITE_ONLY -> "Read-write. Registers behave as listed in their Access column.";
		} });
		var apiTimeout = config.apiTimeout();
		if (apiTimeout != null) {
			rows.add(new Object[] { "Write timeout", apiTimeout > 0 //
					? "A written value is applied in every cycle for " + apiTimeout
							+ " s. Repeat the write to keep it active." //
					: "Disabled. A written value is applied in every cycle until it is overwritten." });
		}
		return addTable(ws, row, "Connection", rows.toArray(Object[][]::new));
	}

	private static int addDataEncoding(Worksheet ws, int row) {
		return addTable(ws, row, "Data encoding", //
				new Object[] { "Topic", "Rule" }, //
				new Object[] { "Register addresses",
						"Zero based, as listed in the Component sheets. Holding and input registers are identical." }, //
				new Object[] { "Byte order",
						"Big endian, most significant register first. Example: " + ModbusType.FLOAT32.name()
								+ " 1.0 (0x3F800000) is sent as 0x3F80, 0x0000." }, //
				new Object[] { "Request size", "Read at most " + MAX_REGISTERS_READ + " and write at most "
						+ MAX_REGISTERS_WRITE + " registers per request." }, //
				new Object[] { "Multi register values",
						"Values spanning several registers have to be read and written within one request." }, //
				new Object[] { "Undefined values",
						"A value that is not available is read as the undefined value of its type, see \"" + DATA_TYPES
								+ "\"." });
	}

	private static int addFunctionCodes(Worksheet ws, int row) {
		return addTable(ws, row, "Function codes", //
				new Object[] { "Function code", "Function", "Comment" }, //
				new Object[] { "03 (0x03)", "Read Holding Registers", "" }, //
				new Object[] { "04 (0x04)", "Read Input Registers", "Same registers as function code 03" }, //
				new Object[] { "06 (0x06)", "Write Single Register", "Only for values of one register" }, //
				new Object[] { "16 (0x10)", "Write Multiple Registers", "" });
	}

	private static int addExceptionCodes(Worksheet ws, int row) {
		return addTable(ws, row, "Exception codes", //
				new Object[] { "Exception code", "Name", "Comment" }, //
				new Object[] { "01 (0x01)", "Illegal Function", "The function code is not supported" }, //
				new Object[] { "02 (0x02)", "Illegal Data Address",
						"The address is not available or the request ends within a value" });
	}

	private static int addDataTypes(Worksheet ws, int row) {
		var rows = new ArrayList<Object[]>();
		rows.add(new Object[] { "Type", "Registers", "Description", "Undefined value" });
		for (var modbusType : ModbusType.values()) {
			var description = switch (modbusType) {
			case ENUM16 -> "Enumeration, see Value/Description";
			case UINT16, UINT32, UINT64 -> "Unsigned integer";
			case INT16, INT32, INT64 -> "Signed integer";
			case FLOAT32 -> "IEEE 754 single precision";
			case FLOAT64 -> "IEEE 754 double precision";
			case STRING16 -> "ASCII text, 32 characters";
			};
			rows.add(new Object[] { modbusType.name(), String.valueOf(modbusType.getWords()), description,
					byteArrayToString(getUndefinedValue(modbusType)) });
		}
		return addTable(ws, row, DATA_TYPES, rows.toArray(Object[][]::new));
	}

	private static int addFrameFormats(Worksheet ws, int row, CommonConfig config) {
		var isRtu = config instanceof CommonConfig.Rtu;
		int nextRow;
		if (isRtu) {
			nextRow = addTable(ws, row, "Modbus RTU frame", //
					new Object[] { "Field", "Length", "Description" }, //
					new Object[] { "Slave address", "1 byte", "Unit-ID of the addressed slave" }, //
					new Object[] { "Protocol data unit", "Up to 253 bytes", "Function code and data, see below" }, //
					new Object[] { "CRC", "2 bytes", "CRC-16 of all previous bytes, low byte first" });
		} else {
			nextRow = addTable(ws, row, "Modbus TCP header (MBAP, Modbus Application Protocol header)", //
					new Object[] { "Field", "Length", "Description", "Client", "Server" }, //
					new Object[] { "Transaction Identifier", "2 bytes", "Matches a response to its request",
							"Set by the client", "Copied from the request" }, //
					new Object[] { "Protocol Identifier", "2 bytes", "0 = Modbus protocol", "Set by the client",
							"Copied from the request" }, //
					new Object[] { "Length", "2 bytes", "Number of following bytes", "Set by the client",
							"Set by the server" }, //
					new Object[] { "Unit Identifier", "1 byte", "Unit-ID", "Set by the client",
							"Copied from the request" });
		}
		nextRow = addFrame(ws, nextRow, "Read request (function code 03 or 04)", isRtu, //
				new String[] { "Function code", "Start address", "Quantity of registers" }, //
				new String[] { "1 byte", "2 bytes", "2 bytes" });
		nextRow = addFrame(ws, nextRow, "Read response (function code 03 or 04)", isRtu, //
				new String[] { "Function code", "Byte count", "Register values" }, //
				new String[] { "1 byte", "1 byte", "2 * N bytes" });
		nextRow = addFrame(ws, nextRow, "Write single register request and response (function code 06)", isRtu, //
				new String[] { "Function code", "Register address", "Register value" }, //
				new String[] { "1 byte", "2 bytes", "2 bytes" });
		nextRow = addFrame(ws, nextRow, "Write multiple registers request (function code 16)", isRtu, //
				new String[] { "Function code", "Start address", "Quantity of registers", "Byte count",
						"Register values" }, //
				new String[] { "1 byte", "2 bytes", "2 bytes", "1 byte", "2 * N bytes" });
		nextRow = addFrame(ws, nextRow, "Write multiple registers response (function code 16)", isRtu, //
				new String[] { "Function code", "Start address", "Quantity of registers" }, //
				new String[] { "1 byte", "2 bytes", "2 bytes" });
		return addFrame(ws, nextRow, "Exception response", isRtu, //
				new String[] { "Function code + 0x80", "Exception code" }, //
				new String[] { "1 byte", "1 byte" });
	}

	/**
	 * Adds a frame layout. The MBAP header of Modbus TCP or the slave address and
	 * CRC of Modbus RTU are added around the given fields. The row of field names
	 * is styled as a sub header.
	 *
	 * @param ws      the {@link Worksheet}
	 * @param row     the row of the table title
	 * @param title   the table title
	 * @param isRtu   true for Modbus RTU, false for Modbus TCP
	 * @param fields  the fields of the protocol data unit
	 * @param lengths the lengths of the fields
	 * @return the first row after the table and an empty row
	 */
	private static int addFrame(Worksheet ws, int row, String title, boolean isRtu, String[] fields,
			String[] lengths) {
		var names = new ArrayList<Object>();
		var sizes = new ArrayList<Object>();
		names.add(isRtu ? "Slave address" : "MBAP header");
		sizes.add(isRtu ? "1 byte" : "7 bytes");
		names.addAll(List.of(fields));
		sizes.addAll(List.of(lengths));
		if (isRtu) {
			names.add("CRC");
			sizes.add("2 bytes");
		}
		var nextRow = addTable(ws, row, title, names.toArray(), sizes.toArray());
		ws.range(row + 1, 0, row + 1, PROTOCOL_LAST_COLUMN).style().fillColor(SUBHEADER_FILL).set();
		return nextRow;
	}

	/**
	 * Adds a table to the protocol sheet.
	 *
	 * <p>
	 * The first row is the table header. The last cell of every row is merged
	 * up to the last column of the sheet.
	 *
	 * @param ws    the {@link Worksheet}
	 * @param row   the row of the table title
	 * @param title the table title
	 * @param rows  the rows, starting with the table header
	 * @return the first row after the table and an empty row
	 */
	private static int addTable(Worksheet ws, int row, String title, Object[]... rows) {
		ws.value(row, 0, title);
		ws.style(row, 0).bold().horizontalAlignment("left").indent(1).set();
		ws.rowHeight(row, ROW_HEIGHT_TABLE_TITLE);
		var headerRow = row + 1;
		var nextRow = headerRow;
		for (var cells : rows) {
			for (var col = 0; col < cells.length; col++) {
				switch (cells[col]) {
				case null -> {
				}
				case Number n -> ws.value(nextRow, col, n);
				default -> ws.value(nextRow, col, cells[col].toString());
				}
			}
			if (cells.length - 1 < PROTOCOL_LAST_COLUMN) {
				ws.range(nextRow, cells.length - 1, nextRow, PROTOCOL_LAST_COLUMN).merge();
			}
			ws.rowHeight(nextRow, ROW_HEIGHT);
			nextRow++;
		}
		styleTableHeader(ws, headerRow, PROTOCOL_LAST_COLUMN);
		if (nextRow - 1 > headerRow) {
			styleTableBody(ws, headerRow + 1, nextRow - 1, PROTOCOL_LAST_COLUMN);
		}
		return nextRow + 1;
	}

	private static void styleTableHeader(Worksheet sheet, int row, int lastColumn) {
		sheet.range(row, 0, row, lastColumn).style() //
				.bold() //
				.fillColor(HEADER_FILL) //
				.borderStyle("thin") //
				.horizontalAlignment("left") //
				.verticalAlignment("center") //
				.indent(1) //
				.set();
		sheet.rowHeight(row, ROW_HEIGHT);
	}

	private static void styleTableBody(Worksheet sheet, int firstRow, int lastRow, int lastColumn) {
		sheet.range(firstRow, 0, lastRow, lastColumn).style() //
				.borderStyle("thin") //
				.horizontalAlignment("left") //
				.verticalAlignment("center") //
				.indent(1) //
				.set();
	}

	/**
	 * Builds a valid and unique Excel sheet name.
	 *
	 * <p>
	 * Excel limits sheet names to 31 characters, forbids some characters and
	 * apostrophes at the start or end, and compares names case insensitively.
	 *
	 * @param title          the preferred name
	 * @param usedSheetNames the lower case names that are already in use, which
	 *                       receives the new name
	 * @return the sheet name
	 */
	protected static String getSheetName(String title, Set<String> usedSheetNames) {
		var base = SHEET_NAME_BOUNDARY.matcher(INVALID_SHEET_NAME_CHARACTERS.matcher(title).replaceAll("_"))
				.replaceAll("");
		if (base.isEmpty()) {
			base = "Component";
		}
		var name = fitSheetName(base, "");
		for (var i = 2; !usedSheetNames.add(name.toLowerCase(Locale.ROOT)); i++) {
			name = fitSheetName(base, " (" + i + ")");
		}
		return name;
	}

	private static String fitSheetName(String base, String suffix) {
		var maxLength = MAX_SHEET_NAME_LENGTH - suffix.length();
		var name = base.length() > maxLength ? base.substring(0, maxLength) : base;
		return SHEET_NAME_BOUNDARY.matcher(name).replaceAll("") + suffix;
	}

	private static String getProductName(CommonConfig config) {
		var oem = config.oem();
		if (oem == null || oem.getManufacturerModel() == null || oem.getManufacturerModel().isBlank()) {
			return "OpenEMS";
		}
		return oem.getManufacturerModel();
	}

	private static String getInterfaceName(CommonConfig config) {
		return switch (config) {
		case CommonConfig.Tcp tcp -> "Modbus TCP";
		case CommonConfig.Rtu rtu -> "Modbus RTU";
		};
	}

	/**
	 * Estimates the row height for a wrapped description. Excel does not adjust
	 * the height of rows with an explicit height.
	 *
	 * @param description the description
	 * @return the row height in points
	 */
	private static double getRowHeight(String description) {
		var lines = 0;
		for (var line : Objects.toString(description, "").split("\n", -1)) {
			lines += Math.max(1, (line.length() + DESCRIPTION_CHARACTERS_PER_LINE - 1) / DESCRIPTION_CHARACTERS_PER_LINE);
		}
		return ROW_HEIGHT + (lines - 1) * LINE_HEIGHT;
	}

	private static int getLastAddress(int address, ModbusRecord record) {
		return address + record.getType().getWords() - 1;
	}

	private static boolean isReserved(ModbusRecord record) {
		return record instanceof ModbusRecordUint16Reserved //
				|| record instanceof ModbusRecordUint32Reserved //
				|| record instanceof ModbusRecordUint64Reserved //
				|| record instanceof ModbusRecordInt16Reserved //
				|| record instanceof ModbusRecordInt32Reserved //
				|| record instanceof ModbusRecordInt64Reserved //
				|| record instanceof ModbusRecordFloat32Reserved //
				|| record instanceof ModbusRecordFloat64Reserved //
				|| record instanceof ModbusRecordString16Reserved;
	}

	private static byte[] getUndefinedValue(ModbusType modbusType) {
		return switch (modbusType) {
		case ENUM16, UINT16 -> ModbusRecordUint16.UNDEFINED_BYTE_ARRAY;
		case UINT32 -> ModbusRecordUint32.UNDEFINED_BYTE_ARRAY;
		case UINT64 -> ModbusRecordUint64.UNDEFINED_BYTE_ARRAY;
		case INT16 -> ModbusRecordInt16.UNDEFINED_BYTE_ARRAY;
		case INT32 -> ModbusRecordInt32.UNDEFINED_BYTE_ARRAY;
		case INT64 -> ModbusRecordInt64.UNDEFINED_BYTE_ARRAY;
		case FLOAT32 -> ModbusRecordFloat32.UNDEFINED_BYTE_ARRAY;
		case FLOAT64 -> ModbusRecordFloat64.UNDEFINED_BYTE_ARRAY;
		case STRING16 -> ModbusRecordString16.UNDEFINED_BYTE_ARRAY;
		};
	}

	private static String byteArrayToString(byte[] value) {
		if (value.length == 0) {
			return "";
		}
		return "0x" + HexFormat.of().formatHex(value);
	}

}
