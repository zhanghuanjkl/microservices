package com.github.sawied.microservice.commons.license;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 
 * @author sawied
 *
 */
public class Feature {

	private static final String[] DATE_FORMAT = { "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
			"yyyy-MM-dd HH", "yyyy-MM-dd" };
	private static final int VARIABLE_LENGTH = -1;
	private final String name;
	private final Type type;
	private final byte[] value;

	private Feature(String name, Type type, byte[] value) {
		this.name = name;
		this.type = type;
		this.value = value;
	}
	
	

	private static String dateFormat(Object date) {
		return new SimpleDateFormat(DATE_FORMAT[0]).format(date);
	}

	private static Date dateParse(String date) {
		for (String format : DATE_FORMAT) {
			try {
				return new SimpleDateFormat(format).parse(date);
			} catch (ParseException ignored) {
			}
		}
		throw new IllegalArgumentException("Can not parse " + date);
	}

	static String[] splitString(String s) {
		int nameEnd = s.indexOf(":");
		final int typeEnd = s.indexOf("=", nameEnd + 1);
		if (nameEnd > typeEnd) {
			nameEnd = -1;
		}
		if (typeEnd == -1) {
			throw new IllegalArgumentException("Feature string representation needs '=' after the type");
		}
		final String name;
		final String typeString;
		if (nameEnd > 0) {
			name = s.substring(0, nameEnd).trim();
			typeString = s.substring(nameEnd + 1, typeEnd).trim();
		} else {
			name = s.substring(0, typeEnd).trim();
			typeString = "STRING";
		}
		final String valueString = s.substring(typeEnd + 1);
		return new String[] { name, typeString, valueString };
	}

	static Feature getFeature(String name, String typeString, String valueString) {
		final Type type = Type.valueOf(typeString);
		final Object value = type.unstringer.apply(valueString);
		return type.factory.apply(name, value);
	}

	/**
	 * @return the name of the feature.
	 */
	public String name() {
		return name;
	}

	@Override
	public String toString() {
		return toStringWith(type.stringer.apply(type.objecter.apply(this)));
	}

	String toStringWith(String value) {
		return name + (type == Type.STRING ? "" : ":" + type.toString()) + "=" + value;
	}

	/**
	 * @return the string representation of the value of the feature.
	 */
	public String valueString() {
		return type.stringer.apply(type.objecter.apply(this));
	}

	/**
	 * Convert a feature to byte array. The bytes will have the following structure
	 *
	 * <pre>
	 *      [4-byte type][4-byte name length][4-byte value length][name][value]
	 * </pre>
	 *
	 * or
	 *
	 * <pre>
	 *      [4-byte type][4-byte name length][name][value]
	 * </pre>
	 *
	 * if the length of the value can be determined from the type (some types have
	 * fixed length values).
	 *
	 * @return the byte array representation of the feature
	 */
	public byte[] serialized() {
		final byte[] nameBuffer = name.getBytes(StandardCharsets.UTF_8);
		final int typeLength = Integer.BYTES;
		final int nameLength = Integer.BYTES + nameBuffer.length;
		final int valueLength = Integer.BYTES + value.length;
		final ByteBuffer buffer = ByteBuffer.allocate(typeLength + nameLength + valueLength).putInt(type.serialized)
				.putInt(nameBuffer.length);
		if (type.fixedSize == VARIABLE_LENGTH) {
			buffer.putInt(value.length);
		}
		buffer.put(nameBuffer).put(value);
		return buffer.array();
	}

	public boolean isBinary() {
		return type == Type.BINARY;
	}

	public boolean isString() {
		return type == Type.STRING;
	}

	public boolean isByte() {
		return type == Type.BYTE;
	}

	public boolean isShort() {
		return type == Type.SHORT;
	}

	public boolean isInt() {
		return type == Type.INT;
	}

	public boolean isLong() {
		return type == Type.LONG;
	}

	public boolean isFloat() {
		return type == Type.FLOAT;
	}

	public boolean isDouble() {
		return type == Type.DOUBLE;
	}

	public boolean isBigInteger() {
		return type == Type.BIGINTEGER;
	}

	public boolean isBigDecimal() {
		return type == Type.BIGDECIMAL;
	}

	public boolean isDate() {
		return type == Type.DATE;
	}

	public boolean isUUID() {
		return type == Type.UUID;
	}

	public byte[] getBinary() {
		if (type != Type.BINARY) {
			throw new IllegalArgumentException("Feature is not BINARY");
		}
		return value;
	}

	public String getString() {
		if (type != Type.STRING) {
			throw new IllegalArgumentException("Feature is not STRING");
		}
		return new String(value, StandardCharsets.UTF_8);
	}

	public byte getByte() {
		if (type != Type.BYTE) {
			throw new IllegalArgumentException("Feature is not BYTE");
		}
		return value[0];
	}

	public short getShort() {
		if (type != Type.SHORT) {
			throw new IllegalArgumentException("Feature is not SHORT");
		}
		return ByteBuffer.wrap(value).getShort();
	}

	public int getInt() {
		if (type != Type.INT) {
			throw new IllegalArgumentException("Feature is not INT");
		}
		return ByteBuffer.wrap(value).getInt();
	}

	public long getLong() {
		if (type != Type.LONG) {
			throw new IllegalArgumentException("Feature is not LONG");
		}
		return ByteBuffer.wrap(value).getLong();
	}

	public float getFloat() {
		if (type != Type.FLOAT) {
			throw new IllegalArgumentException("Feature is not FLOAT");
		}
		return ByteBuffer.wrap(value).getFloat();
	}

	public double getDouble() {
		if (type != Type.DOUBLE) {
			throw new IllegalArgumentException("Feature is not DOUBLE");
		}
		return ByteBuffer.wrap(value).getDouble();
	}

	public BigInteger getBigInteger() {
		if (type != Type.BIGINTEGER) {
			throw new IllegalArgumentException("Feature is not BIGINTEGER");
		}
		return new BigInteger(value);
	}

	public BigDecimal getBigDecimal() {
		if (type != Type.BIGDECIMAL) {
			throw new IllegalArgumentException("Feature is not BIGDECIMAL");
		}
		ByteBuffer bb = ByteBuffer.wrap(value);
		int scale = bb.getInt(value.length - Integer.BYTES);

		return new BigDecimal(new BigInteger(Arrays.copyOf(value, value.length - Integer.BYTES)), scale);
	}

	public java.util.UUID getUUID() {
		if (type != Type.UUID) {
			throw new IllegalArgumentException("Feature is not UUID");
		}
		ByteBuffer bb = ByteBuffer.wrap(value);
		final Long ls = bb.getLong();
		final Long ms = bb.getLong();
		return new java.util.UUID(ms, ls);
	}

	public Date getDate() {
		if (type != Type.DATE) {
			throw new IllegalArgumentException("Feature is not DATE");
		}
		return new Date(ByteBuffer.wrap(value).getLong());
	}

	private enum Type {
		BINARY(1, VARIABLE_LENGTH, Feature::getBinary, (name, value) -> Create.binaryFeature(name, (byte[]) value),
				ba -> Base64.getEncoder().encodeToString((byte[]) ba), enc -> Base64.getDecoder().decode(enc)),
		STRING(2, VARIABLE_LENGTH, Feature::getString, (name, value) -> Create.stringFeature(name, (String) value),
				Object::toString, s -> s),
		BYTE(3, Byte.BYTES, Feature::getByte, (name, value) -> Create.byteFeature(name, (Byte) value),
				b -> String.format("0x%02X", (byte) (Byte) b), NumericParser.Byte::parse),
		SHORT(4, Short.BYTES, Feature::getShort, (name, value) -> Create.shortFeature(name, (Short) value),
				Object::toString, NumericParser.Short::parse),
		INT(5, Integer.BYTES, Feature::getInt, (name, value) -> Create.intFeature(name, (Integer) value),
				Object::toString, NumericParser.Int::parse),
		LONG(6, Long.BYTES, Feature::getLong, (name, value) -> Create.longFeature(name, (Long) value), Object::toString,
				NumericParser.Long::parse),
		FLOAT(7, Float.BYTES, Feature::getFloat, (name, value) -> Create.floatFeature(name, (Float) value),
				Object::toString, Float::parseFloat),
		DOUBLE(8, Double.BYTES, Feature::getDouble, (name, value) -> Create.doubleFeature(name, (Double) value),
				Object::toString, Double::parseDouble),

		BIGINTEGER(9, VARIABLE_LENGTH, Feature::getBigInteger,
				(name, value) -> Create.bigIntegerFeature(name, (BigInteger) value), Object::toString, BigInteger::new),
		BIGDECIMAL(10, VARIABLE_LENGTH, Feature::getBigDecimal,
				(name, value) -> Create.bigDecimalFeature(name, (BigDecimal) value), Object::toString, BigDecimal::new),

		DATE(11, Long.BYTES, Feature::getDate, (name, value) -> Create.dateFeature(name, (Date) value),
				Feature::dateFormat, Feature::dateParse),

		UUID(12, 2 * Long.BYTES, Feature::getUUID, (name, value) -> Create.uuidFeature(name, (java.util.UUID) value),
				Object::toString, java.util.UUID::fromString);

		final int fixedSize;
		final int serialized;
		final Function<Object, String> stringer;
		final Function<Feature, Object> objecter;
		final Function<String, Object> unstringer;
		final BiFunction<String, Object, Feature> factory;

		Type(int serialized, int fixedSize, Function<Feature, Object> objecter,
				BiFunction<String, Object, Feature> factory, Function<Object, String> toStringer,
				Function<String, Object> unstringer) {
			this.serialized = serialized;
			this.fixedSize = fixedSize;
			this.stringer = toStringer;
			this.objecter = objecter;
			this.unstringer = unstringer;
			this.factory = factory;
		}
	}

	public static class Create {
		private Create() {
		}

		private static void notNull(Object value) {
			if (value == null) {
				throw new IllegalArgumentException("Cannot create a feature from null value.");
			}
		}

		public static Feature binaryFeature(String name, byte[] value) {
			notNull(value);
			return new Feature(name, Type.BINARY, value);
		}

		public static Feature stringFeature(String name, String value) {
			notNull(value);
			return new Feature(name, Type.STRING, value.getBytes(StandardCharsets.UTF_8));
		}

		public static Feature byteFeature(String name, Byte value) {
			notNull(value);
			return new Feature(name, Type.BYTE, new byte[] { value });
		}

		public static Feature shortFeature(String name, Short value) {
			notNull(value);
			return new Feature(name, Type.SHORT, ByteBuffer.allocate(Short.BYTES).putShort(value).array());
		}

		public static Feature intFeature(String name, Integer value) {
			notNull(value);
			return new Feature(name, Type.INT, ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
		}

		public static Feature longFeature(String name, Long value) {
			notNull(value);
			return new Feature(name, Type.LONG, ByteBuffer.allocate(Long.BYTES).putLong(value).array());
		}

		public static Feature floatFeature(String name, Float value) {
			notNull(value);
			return new Feature(name, Type.FLOAT, ByteBuffer.allocate(Float.BYTES).putFloat(value).array());
		}

		public static Feature doubleFeature(String name, Double value) {
			notNull(value);
			return new Feature(name, Type.DOUBLE, ByteBuffer.allocate(Double.BYTES).putDouble(value).array());
		}

		public static Feature bigIntegerFeature(String name, BigInteger value) {
			notNull(value);
			return new Feature(name, Type.BIGINTEGER, value.toByteArray());
		}

		public static Feature bigDecimalFeature(String name, BigDecimal value) {
			notNull(value);
			byte[] b = value.unscaledValue().toByteArray();
			return new Feature(name, Type.BIGDECIMAL,
					ByteBuffer.allocate(Integer.BYTES + b.length).put(b).putInt(value.scale()).array());
		}

		public static Feature uuidFeature(String name, java.util.UUID value) {
			notNull(value);
			return new Feature(name, Type.UUID, ByteBuffer.allocate(2 * Long.BYTES)
					.putLong(value.getLeastSignificantBits()).putLong(value.getMostSignificantBits()).array());
		}

		public static Feature dateFeature(String name, Date value) {
			notNull(value);
			return new Feature(name, Type.DATE, ByteBuffer.allocate(Long.BYTES).putLong(value.getTime()).array());
		}

		/**
		 * Create a feature from a string representation of the feature. The feature has
		 * to have the following format
		 * 
		 * <pre>
		 *     name:TYPE=value
		 * </pre>
		 * 
		 * the {@code :TYPE} part may be missing in case the feature type is
		 * {@code STRING}. The value has to be the string representation of the value
		 * that is different for each type.
		 *
		 * @param s the feature as string
		 * @return the new object created from the string
		 */
		public static Feature from(String s) {
			final String[] parts = Feature.splitString(s);
			return getFeature(parts[0], parts[1], parts[2]);
		}

		/**
		 * Create the feature from the binary serialized format. The format is defined
		 * in the documentation of the method {@link #serialized()}.
		 *
		 * @param serialized the serialized format.
		 * @return a new feature object
		 */
		public static Feature from(byte[] serialized) {
			if (serialized.length < Integer.BYTES * 3) {
				throw new IllegalArgumentException("Cannot load feature from a byte array that has " + serialized.length
						+ " bytes which is < " + (3 * Integer.BYTES));
			}
			ByteBuffer bb = ByteBuffer.wrap(serialized);
			int typeSerialized = bb.getInt();
			final Type type = typeFrom(typeSerialized);
			final int nameLength = bb.getInt();
			final int valueLength = type.fixedSize == VARIABLE_LENGTH ? bb.getInt() : type.fixedSize;
			final int expectedLength = Integer.BYTES * 3 + valueLength + nameLength;
			if (serialized.length != expectedLength) {
				throw new IllegalArgumentException("Cannot load feature from a byte array that has " + serialized.length
						+ " bytes which is != " + expectedLength);
			}
			final byte[] nameBuffer = new byte[nameLength];
			bb.get(nameBuffer);
			final byte[] value = new byte[valueLength];
			if (valueLength > 0) {
				bb.get(value);
			}
			final String name = new String(nameBuffer, StandardCharsets.UTF_8);
			return new Feature(name, type, value);
		}

		private static Type typeFrom(int typeSerialized) {
			for (final Type type : Type.values()) {
				if (type.serialized == typeSerialized) {
					return type;
				}
			}
			throw new IllegalArgumentException(
					"The deserialized form has a type value " + typeSerialized + " which is not valid.");
		}
	}

}
