package io.openems.edge.common.modbusslave;

public class ModbusRecordInt64Reserved extends ModbusRecordInt64 {

	public ModbusRecordInt64Reserved(int offset) {
		super(offset, "Reserved", null);
	}

	@Override
	public String toString() {
		return "ModbusRecordInt64Reserved [type=" + this.getType() + "]";
	}

}
