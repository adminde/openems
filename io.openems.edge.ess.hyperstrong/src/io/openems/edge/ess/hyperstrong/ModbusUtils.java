package io.openems.edge.ess.hyperstrong;

import static io.openems.edge.common.channel.ChannelUtils.setValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongConsumer;

import io.openems.common.function.TriFunction;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;

public class ModbusUtils {

	private static final int MAX_REGISTERS_PER_TASK = 100;

	public static ElementToChannelConverter CONVERT_FLOAT = new ElementToChannelConverter(v -> {
		if (v == null) {
			return null;
		}
		if (v instanceof Number n) {
			return n.floatValue();
		}
		if (v instanceof String s) {
			return Float.valueOf(s);
		}
		throw new IllegalArgumentException(
				"Type [" + v.getClass().getName() + "] not supported by float converter");
	});

	/**
	 * Reads a 32-bit alarm register, stores the raw value as read into the channel
	 * created by {@code addChannel} and passes the word-swapped value (documented
	 * register aligned to bits 0–15) to the decode callback.
	 *
	 * @param component  the component owning the channel (for storing the raw value)
	 * @param number     the alarm number, passed to {@code addChannel}
	 * @param address    the Modbus start address of the 32-bit alarm register
	 * @param addChannel factory creating and registering the raw {@link ChannelId}
	 * @param decode     receives the word-swapped value for per-bit decoding, may be
	 *                   {@code null}
	 * @return the {@link ModbusElement} to add to the read task
	 */
	public static ModbusElement defineModbusAlarmRegister(OpenemsComponent component, int number, int address,
			Function<Integer, ChannelId> addChannel, LongConsumer decode) {
		var channel = addChannel.apply(number);
		return new UnsignedDoublewordElement(address).onUpdateCallback(value -> {
			if (value == null) {
				return;
			}
			setValue(component, channel, value);
			if (decode != null) {
				decode.accept(value);
			}
		});
	}

	/**
	 * Variant of
	 * {@link #defineModbusAlarmRegister(OpenemsComponent, int, int, Function, LongConsumer)}
	 * for alarm registers without documented severity channels: only the raw value
	 * is stored.
	 *
	 * @param component  the component owning the channel
	 * @param number     the alarm number, passed to {@code addChannel}
	 * @param address    the Modbus start address of the 32-bit alarm register
	 * @param addChannel factory creating and registering the raw {@link ChannelId}
	 * @return the {@link ModbusElement} to add to the read task
	 */
	public static ModbusElement defineModbusAlarmRegister(OpenemsComponent component, int number, int address,
			Function<Integer, ChannelId> addChannel) {
		return defineModbusAlarmRegister(component, number, address, addChannel, null);
	}

	public static List<Task> defineModbusSignedWordInputRegistersTasks(int startAddress, int count,
			Function<Integer, ChannelId> addChannel,
			BiFunction<ChannelId, ModbusElement, ModbusElement> mapElement) {
		return defineModbusInputArrayTasks(startAddress, count, (channelNumber, registerAddress) -> {
			return mapElement.apply(addChannel.apply(channelNumber),
					new SignedWordElement(registerAddress));
		});
	}

	public static List<Task> defineModbusSignedWordInputRegistersTasks(int startAddress, int count,
			Function<Integer, ChannelId> addChannel, ElementToChannelConverter converter,
			TriFunction<ChannelId, ModbusElement, ElementToChannelConverter, ModbusElement> mapElement) {
		return defineModbusInputArrayTasks(startAddress, count, (channelNumber, registerAddress) -> {
			return mapElement.apply(addChannel.apply(channelNumber),
					new SignedWordElement(registerAddress), converter);
		});
	}

	public static List<Task> defineModbusUnsignedWordInputRegistersTasks(int startAddress, int count,
			Function<Integer, ChannelId> addChannel,
			BiFunction<ChannelId, ModbusElement, ModbusElement> mapElement) {
		return defineModbusInputArrayTasks(startAddress, count, (channelNumber, registerAddress) -> {
			return mapElement.apply(addChannel.apply(channelNumber),
					new UnsignedWordElement(registerAddress));
		});
	}

	public static List<Task> defineModbusUnsignedWordInputRegistersTasks(int startAddress, int count,
			Function<Integer, ChannelId> addChannel, ElementToChannelConverter converter,
			TriFunction<ChannelId, ModbusElement, ElementToChannelConverter, ModbusElement> mapElement) {
		return defineModbusInputArrayTasks(startAddress, count, (channelNumber, registerAddress) -> {
			return mapElement.apply(addChannel.apply(channelNumber),
					new UnsignedWordElement(registerAddress), converter);
		});
	}

	public static List<Task> defineModbusInputArrayTasks(int startAddress, int count,
			BiFunction<Integer, Integer, ModbusElement> addElement) {
		List<Task> tasks = new ArrayList<Task>();
		int taskIndex = 0;
		int blockSize = (int) Math.ceil((double) count / MAX_REGISTERS_PER_TASK);
		while (taskIndex < count) {
			var blockChunk = Math.min(blockSize, count - taskIndex);
			var blockElements = new ModbusElement[blockChunk];
			for (var i = 0; i < blockChunk; i++) {
				var address = startAddress + taskIndex + i;
				blockElements[i] = addElement.apply(taskIndex + i + 1, address);
			}
			tasks.add(new FC4ReadInputRegistersTask(startAddress + taskIndex, Priority.LOW, blockElements));
			taskIndex += blockChunk;
		}
		return tasks;
	}
}
