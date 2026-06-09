package io.openems.edge.common.modbusslave;

public class ModbusRecordInt32Reserved extends ModbusRecordInt32 {

	public ModbusRecordInt32Reserved(int offset) {
		super(offset, "Reserved", null);
	}

	@Override
	public String toString() {
		return "ModbusRecordInt32Reserved [type=" + this.getType() + "]";
	}

}
