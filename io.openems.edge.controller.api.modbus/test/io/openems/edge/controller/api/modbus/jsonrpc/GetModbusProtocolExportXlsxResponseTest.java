package io.openems.edge.controller.api.modbus.jsonrpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.edge.bridge.modbus.api.Parity;
import io.openems.edge.bridge.modbus.api.Stopbit;
import io.openems.edge.common.modbusslave.ModbusRecord;
import io.openems.edge.common.modbusslave.ModbusRecordFloat32Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordInt16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordInt64Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordString16;
import io.openems.edge.common.modbusslave.ModbusRecordString16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordUint16;
import io.openems.edge.common.modbusslave.ModbusRecordUint16BlockLength;
import io.openems.edge.common.modbusslave.ModbusRecordUint16Hash;
import io.openems.edge.common.modbusslave.ModbusRecordUint16Reserved;
import io.openems.edge.common.modbusslave.ModbusRecordUint32Reserved;
import io.openems.edge.controller.api.modbus.CommonConfig;
import io.openems.edge.controller.api.modbus.LogVerbosity;

public class GetModbusProtocolExportXlsxResponseTest {

	private static final OpenemsEdgeOem OEM = new DummyOpenemsEdgeOem() {
		@Override
		public String getManufacturerModel() {
			return "Acme EMS";
		}
	};

	private static final CommonConfig TCP_CONFIG = new CommonConfig.Tcp("ctrlApiModbusTcp0", "ctrlApiModbusTcp0",
			true, null, OEM, new String[] { "ess0", "meter0" }, 60, 502, 5, LogVerbosity.NONE);

	private static final CommonConfig RTU_CONFIG = new CommonConfig.Rtu("ctrlApiModbusRtu0", "ctrlApiModbusRtu0",
			true, null, new DummyOpenemsEdgeOem(), new String[] { "ess0", "meter0" }, null, "/dev/ttyUSB0", 19200, 8,
			Stopbit.ONE, Parity.EVEN, 5, LogVerbosity.NONE);

	private static TreeMap<Integer, String> createComponents() {
		var result = new TreeMap<Integer, String>();
		result.put(200, "Storage: North");
		result.put(300, "storage: north");
		result.put(400, "Core.Sum");
		return result;
	}

	private static TreeMap<Integer, ModbusRecord> createRecords() {
		var result = new TreeMap<Integer, ModbusRecord>();
		put(result, 0, null, new ModbusRecordUint16Hash(0, "OpenEMS"));
		put(result, 1, "_meta", new ModbusRecordUint16BlockLength(-1, "_meta", 199));
		put(result, 2, "_meta", new ModbusRecordUint16(0, "OpenEMS Version Major", 2026));

		put(result, 200, "ess0", new ModbusRecordString16(-1, "Component-ID", "ess0"));
		put(result, 216, "ess0", new ModbusRecordUint16BlockLength(-1, "ess0", 100));
		put(result, 220, "ess0", new ModbusRecordUint16Hash(-1, "OpenemsComponent"));
		put(result, 221, "ess0", new ModbusRecordUint16BlockLength(-1, "OpenemsComponent", 80));
		put(result, 222, "ess0", new ModbusRecordUint16(0, "State", 0));
		put(result, 223, "ess0", new ModbusRecordUint16Reserved(1));
		put(result, 224, "ess0", new ModbusRecordUint16Reserved(2));
		put(result, 225, "ess0", new ModbusRecordUint16(3, "Value", 1));
		put(result, 226, "ess0", new ModbusRecordFloat32Reserved(4));
		put(result, 228, "ess0", new ModbusRecordUint32Reserved(6));
		put(result, 230, "ess0", new ModbusRecordString16Reserved(8));
		put(result, 250, "ess0", new ModbusRecordInt64Reserved(28));
		put(result, 260, "ess0", new ModbusRecordInt16Reserved(38));

		put(result, 300, "meter0", new ModbusRecordString16(-1, "Component-ID", "meter0"));
		put(result, 316, "meter0", new ModbusRecordUint16BlockLength(-1, "meter0", 100));

		put(result, 400, "_sum", new ModbusRecordString16(-1, "Component-ID", "_sum"));
		put(result, 416, "_sum", new ModbusRecordUint16BlockLength(-1, "_sum", 300));
		put(result, 420, "_sum", new ModbusRecordUint16Hash(-1, "Sum"));
		put(result, 421, "_sum", new ModbusRecordUint16BlockLength(-1, "Sum", 220));
		return result;
	}

	private static void put(TreeMap<Integer, ModbusRecord> records, int address, String componentId,
			ModbusRecord record) {
		record.setComponentId(componentId);
		records.put(address, record);
	}

	@Test
	public void testGetSheetName() {
		var used = new HashSet<String>();
		assertEquals("ess0", GetModbusProtocolExportXlsxResponse.getSheetName("ess0", used));
		assertEquals("ESS0 (2)", GetModbusProtocolExportXlsxResponse.getSheetName("ESS0", used));
		assertEquals("ess0 (3)", GetModbusProtocolExportXlsxResponse.getSheetName("ess0", used));
		assertEquals("a_b_c_d_e_f_g_h", GetModbusProtocolExportXlsxResponse.getSheetName("a/b\\c?d*e[f]g:h", used));
		assertEquals("quoted", GetModbusProtocolExportXlsxResponse.getSheetName(" 'quoted' ", used));
		assertEquals("Component", GetModbusProtocolExportXlsxResponse.getSheetName("''", used));
		assertEquals("Component (2)", GetModbusProtocolExportXlsxResponse.getSheetName("   ", used));
		assertEquals("x".repeat(31), GetModbusProtocolExportXlsxResponse.getSheetName("x".repeat(40), used));
		assertEquals("x".repeat(27) + " (2)", GetModbusProtocolExportXlsxResponse.getSheetName("x".repeat(40), used));
		assertEquals("y".repeat(30), GetModbusProtocolExportXlsxResponse.getSheetName("y".repeat(30) + "'z", used));
	}

	@Test
	public void testComponentSheets() throws Exception {
		var workbook = readWorkbook(TCP_CONFIG, AccessMode.READ_WRITE);

		assertEquals(List.of("Protocol", "Header", "Storage_ North", "storage_ north (2)", "Summary"),
				List.copyOf(workbook.keySet()));

		var header = workbook.get("Header");
		assertEquals("Header, addresses 0 to 199", header.get(0).get(0));
		assertEquals(5, header.size());
		assertEquals(List.of("0", "Hash of \"OpenEMS\""), cells(findRow(header, "0"), 2));
		assertEquals(List.of("1", "Length of block \"Header\""), cells(findRow(header, "1"), 2));

		var ess = workbook.get("Storage_ North");
		assertEquals("Storage: North, addresses 200 to 299", ess.get(0).get(0));
		assertEquals(List.of("Address", "Name", "Type", "Value/Description", "Unit", "Gain", "Access"),
				cells(ess.get(1), 7));
		assertEquals(List.of("200", "216", "220", "221", "222", "223 to 224", "225", "226 to 245", "250 to 253", "260"),
				ess.subList(2, ess.size()).stream().map(r -> r.get(0)).toList());
		assertEquals(List.of("200", "Component-ID", "STRING16"), cells(findRow(ess, "200"), 3));
		assertEquals(List.of("216", "Length of block \"ess0\""), cells(findRow(ess, "216"), 2));
		assertEquals(List.of("222", "State", "UINT16", "\"0\"", "", "", "RO"), cells(findRow(ess, "222"), 7));
		assertEquals(List.of("223 to 224", "Reserved", "UINT16", "2 Reserved Registers"), cells(findRow(ess, "223 to 224"), 4));
		assertEquals(List.of("226 to 245", "Reserved", "", "20 Reserved Registers"), cells(findRow(ess, "226 to 245"), 4));
		assertEquals(List.of("250 to 253", "Reserved", "INT64", "4 Reserved Registers"), cells(findRow(ess, "250 to 253"), 4));
		assertEquals(List.of("260", "Reserved", "INT16", ""), cells(findRow(ess, "260"), 4));

		var meter = workbook.get("storage_ north (2)");
		assertEquals("storage: north, addresses 300 to 399", meter.get(0).get(0));
		assertEquals(4, meter.size());

		var sum = workbook.get("Summary");
		assertEquals("Summary, addresses 400 to 421", sum.get(0).get(0));
		assertEquals(List.of("400", "Component-ID", "STRING16", "\"_sum\""), cells(findRow(sum, "400"), 4));
		assertEquals(List.of("416", "Length of block \"Summary\""), cells(findRow(sum, "416"), 2));
		assertEquals(List.of("421", "Length of block \"Sum\""), cells(findRow(sum, "421"), 2));
	}

	@Test
	public void testUnitName() {
		assertEquals("Kilowatt [kW]", GetModbusProtocolExportXlsxResponse.getUnitName(Unit.KILOWATT));
		assertEquals("Kilowatt-Hours [kWh]", GetModbusProtocolExportXlsxResponse.getUnitName(Unit.KILOWATT_HOURS));
		assertEquals("Kilovolt-Ampere Reactive [kvar]",
				GetModbusProtocolExportXlsxResponse.getUnitName(Unit.KILOVOLT_AMPERE_REACTIVE));
		assertEquals("Kilovolt-Ampere Reactive Hours [kvarh]",
				GetModbusProtocolExportXlsxResponse.getUnitName(Unit.KILOVOLT_AMPERE_REACTIVE_HOURS));
		assertEquals("Degree Celsius [°C]", GetModbusProtocolExportXlsxResponse.getUnitName(Unit.DEGREE_CELSIUS));
	}

	@Test
	public void testComponentSheetsWithBlankAlias() throws Exception {
		var components = createComponents();
		components.put(300, " ");
		var workbook = readWorkbook(TCP_CONFIG, AccessMode.READ_WRITE, components, createRecords());

		assertEquals(List.of("Protocol", "Header", "Storage_ North", "meter0", "Summary"),
				List.copyOf(workbook.keySet()));
		assertEquals("meter0, addresses 300 to 399", workbook.get("meter0").get(0).get(0));
	}

	@Test
	public void testComponentSheetsWithoutComponents() throws Exception {
		var workbook = readWorkbook(TCP_CONFIG, AccessMode.READ_WRITE, new TreeMap<>(),
				new TreeMap<>(createRecords().headMap(200)));

		assertEquals(List.of("Protocol", "Header"), List.copyOf(workbook.keySet()));
		assertEquals("Header, addresses 0 to 2", workbook.get("Header").get(0).get(0));
	}

	@Test
	public void testProtocolSheetTcp() throws Exception {
		var protocol = readWorkbook(TCP_CONFIG, AccessMode.READ_WRITE).get("Protocol");

		assertEquals("Acme EMS Modbus TCP protocol", protocol.get(0).get(0));
		assertEquals("Acme EMS Modbus TCP", readApplication(TCP_CONFIG));
		var firstCells = protocol.stream().filter(r -> !r.isEmpty()).map(r -> r.get(0)).toList();
		assertEquals("This workbook describes the Modbus TCP registers of Acme EMS, as configured in its Controller Api.",
				firstCells.get(1));
		var titles = List.of("Table structure", "Address ranges", "Columns of the Component sheets", "Access modes",
				"Data types", "Connection", "Data encoding", "Function codes", "Exception codes");
		assertEquals(titles, firstCells.stream().filter(titles::contains).toList());

		assertTrue(findRow(protocol, "Gain").get(1).contains("multiplied by the gain"));
		assertEquals(List.of("RO", "03, 04"), cells(findRow(protocol, "RO"), 2));
		assertEquals(List.of("RW", "03, 04, 06, 16"), cells(findRow(protocol, "RW"), 2));
		assertEquals(List.of("WO", "06, 16"), cells(findRow(protocol, "WO"), 2));

		assertEquals(List.of("Interface", "Modbus TCP"), cells(findRow(protocol, "Interface"), 2));
		assertEquals(List.of("Port", "502"), cells(findRow(protocol, "Port"), 2));
		assertTrue(findRow(protocol, "Unit-ID").get(1).startsWith("1."));
		assertTrue(findRow(protocol, "Api access").get(1).startsWith("Read-write"));
		assertTrue(findRow(protocol, "Write timeout").get(1).contains(" 60 s."));
		assertEquals("Read at most 125 and write at most 123 registers per request.",
				findRow(protocol, "Request size").get(1));

		assertEquals(List.of("UINT16", "1", "Unsigned integer", "0xffff"), cells(findRow(protocol, "UINT16"), 4));
		assertEquals(List.of("INT16", "1", "Signed integer", "0x7fff"), cells(findRow(protocol, "INT16"), 4));
		assertEquals(List.of("FLOAT32", "2", "IEEE 754 single precision", "0x7fc00000"),
				cells(findRow(protocol, "FLOAT32"), 4));
		assertEquals(List.of("FLOAT64", "4", "IEEE 754 double precision", "0x7ff8000000000000"),
				cells(findRow(protocol, "FLOAT64"), 4));
		assertEquals("0x" + "00".repeat(32), findRow(protocol, "STRING16").get(3));

		assertEquals(List.of("Transaction Identifier", "2 bytes"), cells(findRow(protocol, "Transaction Identifier"), 2));
		assertTrue(containsRow(protocol, "MBAP header", "Function code", "Start address",
				"Quantity of registers"));
		assertFalse(containsRow(protocol, "Slave address", "Function code"));
		assertTrue(protocol.stream().noneMatch(r -> !r.isEmpty() && r.get(0).matches("Table [0-9].*")));

		assertEquals(List.of("Sheet", "Start address", "End address"), cells(findRow(protocol, "Sheet"), 3));
		assertEquals(List.of("Header", "0", "199"), cells(findRow(protocol, "Header"), 3));
		assertEquals(List.of("Storage_ North", "200", "299"), cells(findRow(protocol, "Storage_ North"), 3));
		assertEquals(List.of("storage_ north (2)", "300", "399"), cells(findRow(protocol, "storage_ north (2)"), 3));
		assertEquals(List.of("Summary", "400", "421"), cells(findRow(protocol, "Summary"), 3));
	}

	@Test
	public void testProtocolSheetRtu() throws Exception {
		var protocol = readWorkbook(RTU_CONFIG, AccessMode.READ_ONLY).get("Protocol");

		assertEquals("OpenEMS Modbus RTU protocol", protocol.get(0).get(0));
		assertEquals("OpenEMS Modbus RTU", readApplication(RTU_CONFIG));
		assertEquals(List.of("Interface", "Modbus RTU"), cells(findRow(protocol, "Interface"), 2));
		assertEquals(List.of("Baud rate", "19200"), cells(findRow(protocol, "Baud rate"), 2));
		assertEquals(List.of("Data bits", "8"), cells(findRow(protocol, "Data bits"), 2));
		assertEquals(List.of("Parity", "even"), cells(findRow(protocol, "Parity"), 2));
		assertEquals(List.of("Stop bits", "1"), cells(findRow(protocol, "Stop bits"), 2));
		assertTrue(findRow(protocol, "Api access").get(1).startsWith("Read-only"));
		assertFalse(containsRow(protocol, "Port"));
		assertFalse(containsRow(protocol, "Write timeout"));

		assertTrue(containsRow(protocol, "Slave address", "Function code", "Start address", "Quantity of registers",
				"CRC"));
		assertFalse(containsRow(protocol, "MBAP header"));
	}

	/**
	 * Generates the export of the default test data and reads the text of all
	 * cells.
	 *
	 * @param config     the {@link CommonConfig}
	 * @param accessMode the {@link AccessMode}
	 * @return the rows of every sheet by sheet name, in sheet order
	 */
	private static Map<String, List<List<String>>> readWorkbook(CommonConfig config, AccessMode accessMode)
			throws OpenemsException, IOException, ParserConfigurationException, SAXException {
		return readWorkbook(config, accessMode, createComponents(), createRecords());
	}

	/**
	 * Generates the export and reads the text of all cells.
	 *
	 * <p>
	 * The XLSX file is parsed with the JDK only, so the test does not depend on
	 * the runtime dependencies of fastexcel-reader.
	 *
	 * @param config     the {@link CommonConfig}
	 * @param accessMode the {@link AccessMode}
	 * @param components the Component aliases by start address
	 * @param records    the {@link ModbusRecord}s by address
	 * @return the rows of every sheet by sheet name, in sheet order
	 */
	private static Map<String, List<List<String>>> readWorkbook(CommonConfig config, AccessMode accessMode,
			TreeMap<Integer, String> components, TreeMap<Integer, ModbusRecord> records)
			throws OpenemsException, IOException, ParserConfigurationException, SAXException {
		var entries = readEntries(config, accessMode, components, records);
		var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

		var sharedStrings = new ArrayList<String>();
		if (entries.containsKey("xl/sharedStrings.xml")) {
			var items = parse(builder, entries.get("xl/sharedStrings.xml")).getElementsByTagName("si");
			for (var i = 0; i < items.getLength(); i++) {
				sharedStrings.add(items.item(i).getTextContent());
			}
		}

		var targets = new HashMap<String, String>();
		var relationships = parse(builder, entries.get("xl/_rels/workbook.xml.rels"))
				.getElementsByTagName("Relationship");
		for (var i = 0; i < relationships.getLength(); i++) {
			var relationship = (Element) relationships.item(i);
			targets.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
		}

		var result = new LinkedHashMap<String, List<List<String>>>();
		var sheets = parse(builder, entries.get("xl/workbook.xml")).getElementsByTagName("sheet");
		for (var i = 0; i < sheets.getLength(); i++) {
			var sheet = (Element) sheets.item(i);
			var cells = parse(builder, entries.get("xl/" + targets.get(sheet.getAttribute("r:id"))))
					.getElementsByTagName("c");
			var rows = new ArrayList<List<String>>();
			for (var j = 0; j < cells.getLength(); j++) {
				var cell = (Element) cells.item(j);
				var reference = cell.getAttribute("r");
				var letters = reference.replaceAll("[0-9]", "").length();
				var column = reference.substring(0, letters).chars().reduce(0, (c, ch) -> c * 26 + ch - 'A' + 1) - 1;
				var rowIndex = Integer.parseInt(reference.substring(letters)) - 1;
				while (rows.size() <= rowIndex) {
					rows.add(new ArrayList<>());
				}
				var row = rows.get(rowIndex);
				while (row.size() <= column) {
					row.add("");
				}
				var values = cell.getElementsByTagName("v");
				var text = values.getLength() == 0 ? cell.getTextContent() : values.item(0).getTextContent();
				row.set(column, "s".equals(cell.getAttribute("t")) ? sharedStrings.get(Integer.parseInt(text)) : text);
			}
			result.put(sheet.getAttribute("name"), rows);
		}
		return result;
	}

	/**
	 * Generates the export and reads the application name of the document
	 * properties.
	 *
	 * @param config the {@link CommonConfig}
	 * @return the application name
	 */
	private static String readApplication(CommonConfig config)
			throws OpenemsException, IOException, ParserConfigurationException, SAXException {
		var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		return parse(builder, readEntries(config, AccessMode.READ_WRITE, createComponents(), createRecords())
				.get("docProps/app.xml")) //
				.getElementsByTagName("Application").item(0).getTextContent();
	}

	/**
	 * Generates the export and reads all entries of the XLSX file.
	 *
	 * @param config     the {@link CommonConfig}
	 * @param accessMode the {@link AccessMode}
	 * @param components the Component aliases by start address
	 * @param records    the {@link ModbusRecord}s by address
	 * @return the content of every entry by entry name
	 */
	private static Map<String, byte[]> readEntries(CommonConfig config, AccessMode accessMode,
			TreeMap<Integer, String> components, TreeMap<Integer, ModbusRecord> records)
			throws OpenemsException, IOException {
		var response = new GetModbusProtocolExportXlsxResponse(UUID.randomUUID(), config, accessMode, components,
				records);
		var entries = new HashMap<String, byte[]>();
		var file = Files.createTempFile("modbus-protocol", ".xlsx");
		try {
			Files.write(file, Base64.getDecoder().decode(response.getPayload()));
			// ZipInputStream rejects the entries written by fastexcel, because their
			// local headers announce a size of zero. ZipFile reads the central
			// directory instead.
			try (var zip = new ZipFile(file.toFile())) {
				for (var entry : Collections.list(zip.entries())) {
					try (var is = zip.getInputStream(entry)) {
						entries.put(entry.getName(), is.readAllBytes());
					}
				}
			}
		} finally {
			Files.delete(file);
		}
		return entries;
	}

	private static Document parse(DocumentBuilder builder, byte[] xml) throws SAXException, IOException {
		return builder.parse(new ByteArrayInputStream(xml));
	}

	private static List<String> findRow(List<List<String>> rows, String firstCell) {
		return rows.stream() //
				.filter(r -> !r.isEmpty() && r.get(0).equals(firstCell)) //
				.findFirst() //
				.orElseThrow(() -> new AssertionError("Row [" + firstCell + "] not found"));
	}

	private static boolean containsRow(List<List<String>> rows, String... firstCells) {
		var expected = List.of(firstCells);
		return rows.stream().anyMatch(r -> r.size() >= expected.size() && cells(r, expected.size()).equals(expected));
	}

	private static List<String> cells(List<String> row, int count) {
		return IntStream.range(0, count) //
				.mapToObj(i -> i < row.size() ? row.get(i) : "") //
				.toList();
	}

}
