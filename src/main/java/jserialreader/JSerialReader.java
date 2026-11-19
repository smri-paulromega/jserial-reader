/*
 * jserial-reader - prints Java-serialized data as a readable tree.
 * Copyright (C) 2026 smri-paulromega
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jserialreader;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prints the contents of a Java-serialized object, such as one stored in a VARBINARY database column,
 * as a readable tree.
 *
 * The stream is parsed directly (Java Object Serialization Stream Protocol), so the classes that
 * wrote it do not need to be on the classpath, and a serialVersionUID mismatch cannot stop it.
 * Nested objects and lists are followed, not just top-level String keys and values.
 */
public final class JSerialReader {

	private static final String USAGE =
			"Usage: readblob [options] <input>\n"
			+ "\n"
			+ "Input (pick one):\n"
			+ "  0xACED0005...        the hex value pasted as an argument (small blobs only, see below)\n"
			+ "  --clip               read the hex from the clipboard (recommended)\n"
			+ "  -f <file>            read a file holding the hex text, or the raw binary bytes\n"
			+ "  -                    read the hex from standard input\n"
			+ "\n"
			+ "Options:\n"
			+ "  -o, --output <file>  write the tree to <file> (UTF-8, overwritten) instead of the console\n"
			+ "  --stream-order       keep map keys in stored order instead of sorting them\n"
			+ "  -h, --help           show this help\n"
			+ "  -V, --version        show the version and license\n"
			+ "\n"
			+ "Notes:\n"
			+ "  * cmd.exe caps a command line at 8191 characters, so a hex value longer than ~8000\n"
			+ "    characters will not fit as an argument. Copy it and use --clip, or save it and use -f.\n"
			+ "  * SSMS 'Results to Grid' returns at most 65535 characters of a non-XML column. For a\n"
			+ "    larger blob, select it as XML and copy it from the XML window:\n"
			+ "      SELECT CONVERT(XML, '<x>' + CONVERT(VARCHAR(MAX), blob_column, 1) + '</x>')\n"
			+ "      FROM my_table WHERE id = 123\n"
			+ "    The <x> tags, 0x prefix, quotes and whitespace are ignored when reading.\n";

	private static final String VERSION_NOTICE =
			"Copyright (C) 2026 smri-paulromega\n"
			+ "License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>\n"
			+ "This is free software: you are free to change and redistribute it.\n"
			+ "There is NO WARRANTY, to the extent permitted by law.\n";

	private JSerialReader() {
	}

	/** The version stamped into the jar manifest by the build, or "dev" when run from loose classes. */
	static String version() {
		String v = JSerialReader.class.getPackage().getImplementationVersion();
		return v == null ? "dev" : v;
	}

	public static void main(String[] args) {
		boolean sortKeys = true;
		boolean fromClipboard = false;
		boolean fromStdin = false;
		String file = null;
		String output = null;
		StringBuilder hexArg = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			if ("-h".equals(a) || "--help".equals(a) || "/?".equals(a)) {
				System.out.print(USAGE);
				return;
			} else if ("-V".equals(a) || "--version".equals(a)) {
				System.out.print("jserial-reader " + version() + "\n" + VERSION_NOTICE);
				return;
			} else if ("--stream-order".equals(a)) {
				sortKeys = false;
			} else if ("--clip".equals(a)) {
				fromClipboard = true;
			} else if ("-".equals(a)) {
				fromStdin = true;
			} else if ("-f".equals(a)) {
				if (++i >= args.length) {
					fail(1, "-f needs a file name");
				}
				file = args[i];
			} else if ("-o".equals(a) || "--output".equals(a)) {
				if (++i >= args.length) {
					fail(1, a + " needs a file name");
				}
				output = args[i];
			} else {
				hexArg.append(a);
			}
		}

		byte[] data = null;
		try {
			if (file != null) {
				data = decodeInput(Files.readAllBytes(Paths.get(file)));
			} else if (fromClipboard) {
				data = decodeHex(readClipboard());
			} else if (fromStdin) {
				data = decodeInput(readAll(System.in));
			} else if (hexArg.length() > 0) {
				data = decodeHex(hexArg.toString());
			} else {
				System.err.print(USAGE);
				System.exit(1);
			}
		} catch (IOException e) {
			fail(1, e.getMessage());
		} catch (IllegalArgumentException e) {
			fail(1, e.getMessage());
		}

		List<Object> contents;
		try {
			contents = new StreamParser(data).parse();
		} catch (EOFException e) {
			fail(2, "The data ends early (" + data.length + " bytes read). The hex looks truncated; "
					+ "SSMS cuts long values, see --help for the XML workaround.");
			return;
		} catch (IOException e) {
			fail(2, "Cannot read the serialization stream: " + e.getMessage());
			return;
		} catch (RuntimeException e) {
			fail(2, "Cannot read the serialization stream, its structure is invalid: " + e);
			return;
		} catch (StackOverflowError e) {
			fail(2, "Cannot read the serialization stream: objects are nested too deeply");
			return;
		}

		// render the whole tree first, so a failure part way never leaves half-written output
		String tree;
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(buf, false, "UTF-8");
			TreePrinter printer = new TreePrinter(ps, sortKeys);
			for (Object o : contents) {
				printer.printRoot(o);
			}
			ps.flush();
			tree = buf.toString("UTF-8");
		} catch (IOException e) {
			fail(1, e.getMessage());
			return;
		} catch (RuntimeException e) {
			fail(2, "Cannot show the data, its structure is invalid: " + e);
			return;
		} catch (StackOverflowError e) {
			fail(2, "Cannot show the data: objects are nested too deeply");
			return;
		}

		if (output == null) {
			System.out.print(tree);
			System.out.flush();
			return;
		}
		Writer fileOut = null;
		try {
			fileOut = new OutputStreamWriter(new FileOutputStream(output), "UTF-8");
			fileOut.write(tree);
			fileOut.close();
			fileOut = null;
		} catch (IOException e) {
			fail(1, "Cannot write " + output + ": " + e.getMessage());
		} finally {
			if (fileOut != null) {
				try {
					fileOut.close();
				} catch (IOException ignored) {
					// already failing
				}
			}
		}
		System.err.println("Wrote " + Paths.get(output).toAbsolutePath());
	}

	private static void fail(int code, String message) {
		System.err.println("ERROR: " + message);
		System.exit(code);
	}

	// ---------------------------------------------------------------- input

	/** Raw serialized bytes are used as they are; anything else is treated as hex text. */
	static byte[] decodeInput(byte[] b) {
		if (b.length >= 2 && (b[0] & 0xFF) == 0xAC && (b[1] & 0xFF) == 0xED) {
			return b;
		}
		String text;
		if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
			text = new String(b, 2, b.length - 2, Charset.forName("UTF-16LE")); // PowerShell 5.1 '>' output
		} else if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
			text = new String(b, 2, b.length - 2, Charset.forName("UTF-16BE"));
		} else if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
			text = new String(b, 3, b.length - 3, Charset.forName("UTF-8"));
		} else {
			text = new String(b, Charset.forName("ISO-8859-1"));
		}
		return decodeHex(text);
	}

	static byte[] decodeHex(String raw) {
		String s = raw.trim();
		// tolerate the XML wrapper from the SSMS workaround
		if (s.startsWith("<x>")) {
			s = s.substring(3);
		}
		if (s.endsWith("</x>")) {
			s = s.substring(0, s.length() - 4);
		}
		StringBuilder hex = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == ',' || c == ';') {
				continue;
			}
			hex.append(c);
		}
		if (hex.length() >= 2 && hex.charAt(0) == '0' && (hex.charAt(1) == 'x' || hex.charAt(1) == 'X')) {
			hex.delete(0, 2);
		}
		if (hex.length() == 0) {
			throw new IllegalArgumentException("No hex value found in the input");
		}
		if (hex.length() % 2 != 0) {
			throw new IllegalArgumentException("The hex value has an odd number of digits ("
					+ hex.length() + "); part of it is missing");
		}
		byte[] out = new byte[hex.length() / 2];
		for (int i = 0; i < out.length; i++) {
			int hi = Character.digit(hex.charAt(2 * i), 16);
			int lo = Character.digit(hex.charAt(2 * i + 1), 16);
			if (hi < 0 || lo < 0) {
				int at = hi < 0 ? 2 * i : 2 * i + 1;
				throw new IllegalArgumentException("Not a hex digit '" + hex.charAt(at) + "' at position " + at);
			}
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}

	private static String readClipboard() throws IOException {
		try {
			Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			return o == null ? "" : o.toString();
		} catch (Exception e) {
			throw new IOException("Cannot read the clipboard as text: " + e.getMessage());
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	// ---------------------------------------------------------------- stream model

	static final class FieldDesc {
		char type;
		String name;
		String className;
	}

	static final class ClassDesc {
		String name;
		int flags;
		final List<FieldDesc> fields = new ArrayList<FieldDesc>();
		ClassDesc superDesc;
	}

	/** Field values and writeObject() data that one class in the hierarchy contributed. */
	static final class ClassData {
		final ClassDesc desc;
		final LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
		List<Object> annotation = Collections.emptyList();

		ClassData(ClassDesc desc) {
			this.desc = desc;
		}
	}

	static final class JObject {
		final ClassDesc desc;
		/** Superclass first, as written in the stream. */
		final List<ClassData> data = new ArrayList<ClassData>();

		JObject(ClassDesc desc) {
			this.desc = desc;
		}

		ClassData dataFor(String className) {
			for (ClassData cd : data) {
				if (cd.desc.name.equals(className)) {
					return cd;
				}
			}
			return null;
		}

		Object field(String name) {
			for (int i = data.size() - 1; i >= 0; i--) {
				if (data.get(i).values.containsKey(name)) {
					return data.get(i).values.get(name);
				}
			}
			return null;
		}
	}

	static final class JArray {
		final ClassDesc desc;
		Object[] values;

		JArray(ClassDesc desc) {
			this.desc = desc;
		}
	}

	static final class JEnum {
		final ClassDesc desc;
		String constant;

		JEnum(ClassDesc desc) {
			this.desc = desc;
		}
	}

	static final class JClassRef {
		final ClassDesc desc;

		JClassRef(ClassDesc desc) {
			this.desc = desc;
		}
	}

	static final class BlockData {
		final byte[] bytes;

		BlockData(byte[] bytes) {
			this.bytes = bytes;
		}
	}

	// ---------------------------------------------------------------- parser

	static final class StreamParser {
		private static final int TC_NULL = 0x70;
		private static final int TC_REFERENCE = 0x71;
		private static final int TC_CLASSDESC = 0x72;
		private static final int TC_OBJECT = 0x73;
		private static final int TC_STRING = 0x74;
		private static final int TC_ARRAY = 0x75;
		private static final int TC_CLASS = 0x76;
		private static final int TC_BLOCKDATA = 0x77;
		private static final int TC_ENDBLOCKDATA = 0x78;
		private static final int TC_RESET = 0x79;
		private static final int TC_BLOCKDATALONG = 0x7A;
		private static final int TC_EXCEPTION = 0x7B;
		private static final int TC_LONGSTRING = 0x7C;
		private static final int TC_PROXYCLASSDESC = 0x7D;
		private static final int TC_ENUM = 0x7E;
		private static final int BASE_HANDLE = 0x7E0000;

		private static final int SC_WRITE_METHOD = 0x01;
		private static final int SC_BLOCK_DATA = 0x08;
		private static final int SC_SERIALIZABLE = 0x02;
		private static final int SC_EXTERNALIZABLE = 0x04;

		private final int length;
		private final ByteArrayInputStream bytes;
		private final DataInputStream in;
		private final List<Object> handles = new ArrayList<Object>();

		StreamParser(byte[] data) {
			this.length = data.length;
			this.bytes = new ByteArrayInputStream(data);
			this.in = new DataInputStream(bytes);
		}

		private int offset() {
			return length - bytes.available();
		}

		List<Object> parse() throws IOException {
			int magic = in.readUnsignedShort();
			if (magic != 0xACED) {
				throw new IOException(String.format("does not start with 0xACED (found 0x%04X), "
						+ "so it is not a Java serialization stream", magic));
			}
			in.readUnsignedShort(); // stream version, 5
			List<Object> contents = new ArrayList<Object>();
			while (bytes.available() > 0) {
				contents.add(readContent(in.readUnsignedByte()));
			}
			return contents;
		}

		private Object readContent(int tc) throws IOException {
			if (tc == TC_BLOCKDATA) {
				return new BlockData(readBytes(in.readUnsignedByte()));
			}
			if (tc == TC_BLOCKDATALONG) {
				return new BlockData(readBytes(in.readInt()));
			}
			return readObject(tc);
		}

		/** Reads n bytes, failing cleanly when a corrupt length points past the end of the data. */
		private byte[] readBytes(long n) throws IOException {
			if (n < 0) {
				throw new IOException("negative length " + n + " before byte " + offset());
			}
			if (n > bytes.available()) {
				throw new EOFException();
			}
			byte[] b = new byte[(int) n];
			in.readFully(b);
			return b;
		}

		private Object readObject() throws IOException {
			return readObject(in.readUnsignedByte());
		}

		private Object readObject(int tc) throws IOException {
			switch (tc) {
			case TC_NULL:
				return null;
			case TC_REFERENCE: {
				int index = in.readInt() - BASE_HANDLE;
				if (index < 0 || index >= handles.size()) {
					throw new IOException("bad back-reference at byte " + (offset() - 5));
				}
				return handles.get(index);
			}
			case TC_STRING: {
				String s = in.readUTF();
				handles.add(s);
				return s;
			}
			case TC_LONGSTRING: {
				String s = decodeModifiedUtf8(readBytes(in.readLong()));
				handles.add(s);
				return s;
			}
			case TC_CLASSDESC:
			case TC_PROXYCLASSDESC:
				return readClassDescBody(tc);
			case TC_CLASS: {
				JClassRef ref = new JClassRef(readClassDesc());
				handles.add(ref);
				return ref;
			}
			case TC_ARRAY:
				return readArray();
			case TC_ENUM: {
				JEnum e = new JEnum(readClassDesc());
				handles.add(e);
				e.constant = (String) readObject();
				return e;
			}
			case TC_OBJECT:
				return readNewObject();
			case TC_RESET:
				handles.clear();
				return readObject();
			case TC_EXCEPTION:
				throw new IOException("the stream holds a serialized exception, not data");
			default:
				throw new IOException(String.format("unexpected type code 0x%02X at byte %d", tc, offset() - 1));
			}
		}

		private ClassDesc readClassDesc() throws IOException {
			int at = offset();
			Object o = readObject();
			if (o != null && !(o instanceof ClassDesc)) {
				throw new IOException("expected a class descriptor at byte " + at);
			}
			return (ClassDesc) o;
		}

		private ClassDesc readClassDescBody(int tc) throws IOException {
			ClassDesc d = new ClassDesc();
			if (tc == TC_CLASSDESC) {
				d.name = in.readUTF();
				in.readLong(); // serialVersionUID
				handles.add(d);
				d.flags = in.readUnsignedByte();
				int count = in.readUnsignedShort();
				for (int i = 0; i < count; i++) {
					FieldDesc f = new FieldDesc();
					f.type = (char) in.readUnsignedByte();
					f.name = in.readUTF();
					if (f.type == 'L' || f.type == '[') {
						f.className = (String) readObject();
					}
					d.fields.add(f);
				}
			} else {
				handles.add(d);
				int count = in.readInt();
				StringBuilder names = new StringBuilder("Proxy");
				for (int i = 0; i < count; i++) {
					names.append(i == 0 ? "(" : ", ").append(in.readUTF());
				}
				d.name = count > 0 ? names.append(')').toString() : "Proxy";
				d.flags = SC_SERIALIZABLE;
			}
			readAnnotation(); // classAnnotation, normally empty
			d.superDesc = readClassDesc();
			return d;
		}

		private List<Object> readAnnotation() throws IOException {
			List<Object> items = new ArrayList<Object>();
			int tc;
			while ((tc = in.readUnsignedByte()) != TC_ENDBLOCKDATA) {
				items.add(readContent(tc));
			}
			return items;
		}

		private JObject readNewObject() throws IOException {
			JObject o = new JObject(readClassDesc());
			handles.add(o);
			List<ClassDesc> hierarchy = new ArrayList<ClassDesc>();
			for (ClassDesc c = o.desc; c != null; c = c.superDesc) {
				hierarchy.add(0, c);
			}
			for (ClassDesc c : hierarchy) {
				ClassData cd = new ClassData(c);
				if ((c.flags & SC_SERIALIZABLE) != 0) {
					for (FieldDesc f : c.fields) {
						cd.values.put(f.name, readValue(f.type));
					}
					if ((c.flags & SC_WRITE_METHOD) != 0) {
						cd.annotation = readAnnotation();
					}
				} else if ((c.flags & SC_EXTERNALIZABLE) != 0) {
					if ((c.flags & SC_BLOCK_DATA) == 0) {
						throw new IOException(c.name + " uses the old Externalizable format, which cannot be "
								+ "read without the class");
					}
					cd.annotation = readAnnotation();
				}
				o.data.add(cd);
			}
			return o;
		}

		private JArray readArray() throws IOException {
			JArray a = new JArray(readClassDesc());
			handles.add(a);
			int size = in.readInt();
			if (size < 0) {
				throw new IOException("negative array size " + size + " before byte " + offset());
			}
			if (size > bytes.available()) {
				throw new EOFException(); // every element takes at least one byte
			}
			char type = a.desc.name.charAt(1);
			a.values = new Object[size];
			for (int i = 0; i < size; i++) {
				a.values[i] = readValue(type);
			}
			return a;
		}

		private Object readValue(char type) throws IOException {
			switch (type) {
			case 'B':
				return in.readByte();
			case 'C':
				return in.readChar();
			case 'D':
				return in.readDouble();
			case 'F':
				return in.readFloat();
			case 'I':
				return in.readInt();
			case 'J':
				return in.readLong();
			case 'S':
				return in.readShort();
			case 'Z':
				return in.readBoolean();
			case 'L':
			case '[':
				return readObject();
			default:
				throw new IOException("unknown field type '" + type + "' at byte " + offset());
			}
		}

		private static String decodeModifiedUtf8(byte[] b) {
			StringBuilder sb = new StringBuilder(b.length);
			for (int i = 0; i < b.length;) {
				int c = b[i++] & 0xFF;
				if (c < 0x80) {
					sb.append((char) c);
				} else if ((c & 0xE0) == 0xC0 && i < b.length) {
					sb.append((char) (((c & 0x1F) << 6) | (b[i++] & 0x3F)));
				} else if (i + 1 < b.length) {
					sb.append((char) (((c & 0x0F) << 12) | ((b[i++] & 0x3F) << 6) | (b[i++] & 0x3F)));
				} else {
					break;
				}
			}
			return sb.toString();
		}
	}

	// ---------------------------------------------------------------- readable view

	/** Text that is printed as it is, never quoted (dates, enums, binary data). */
	static final class Raw {
		final String text;

		Raw(String text) {
			this.text = text;
		}
	}

	/** A plain object, shown as its fields. */
	static final class BeanView {
		final String className;
		final LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();

		BeanView(String className) {
			this.className = className;
		}
	}

	static final class MapView {
		final String className;
		final List<Object[]> entries = new ArrayList<Object[]>();

		MapView(String className) {
			this.className = className;
		}
	}

	static final class ListView {
		final String className;
		final List<Object> items = new ArrayList<Object>();

		ListView(String className) {
			this.className = className;
		}
	}

	/** Turns the raw stream model into maps, lists, beans and scalars, decoding well-known JDK types. */
	static final class Simplifier {
		private static final Set<String> MAPS = new HashSet<String>(Arrays.asList(
				"java.util.HashMap", "java.util.Hashtable", "java.util.TreeMap", "java.util.IdentityHashMap",
				"java.util.concurrent.ConcurrentHashMap", "java.util.WeakHashMap"));
		private static final Set<String> COLLECTIONS = new HashSet<String>(Arrays.asList(
				"java.util.ArrayList", "java.util.LinkedList", "java.util.ArrayDeque", "java.util.HashSet",
				"java.util.PriorityQueue", "java.util.concurrent.CopyOnWriteArrayList"));
		private static final Set<String> BOXES = new HashSet<String>(Arrays.asList(
				"java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Double",
				"java.lang.Float", "java.lang.Boolean", "java.lang.Character"));

		private final IdentityHashMap<Object, Boolean> inProgress = new IdentityHashMap<Object, Boolean>();

		Object simplify(Object v) {
			if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean
					|| v instanceof Character) {
				return v;
			}
			if (v instanceof BlockData) {
				return new Raw("<" + ((BlockData) v).bytes.length + " bytes: " + toHex(((BlockData) v).bytes) + ">");
			}
			if (v instanceof JEnum) {
				return new Raw(simpleName(((JEnum) v).desc.name) + "." + ((JEnum) v).constant);
			}
			if (v instanceof JClassRef) {
				return new Raw("class " + ((JClassRef) v).desc.name);
			}
			if (v instanceof ClassDesc) {
				return new Raw("class descriptor " + ((ClassDesc) v).name);
			}
			if (inProgress.containsKey(v)) {
				return new Raw("<circular reference>");
			}
			inProgress.put(v, Boolean.TRUE);
			try {
				if (v instanceof JArray) {
					return simplifyArray((JArray) v);
				}
				return simplifyObject((JObject) v);
			} finally {
				inProgress.remove(v);
			}
		}

		private Object simplifyArray(JArray a) {
			if ("[B".equals(a.desc.name)) {
				byte[] b = new byte[a.values.length];
				for (int i = 0; i < b.length; i++) {
					b[i] = (Byte) a.values[i];
				}
				return new Raw("<byte[" + b.length + "]: " + toHex(b) + ">");
			}
			ListView list = new ListView(arrayTypeName(a.desc.name));
			for (Object item : a.values) {
				list.items.add(simplify(item));
			}
			return list;
		}

		private Object simplifyObject(JObject o) {
			String name = o.desc.name;
			if (BOXES.contains(name)) {
				return o.field("value");
			}
			if ("java.math.BigInteger".equals(name)) {
				return toBigInteger(o);
			}
			if ("java.math.BigDecimal".equals(name)) {
				Object intVal = o.field("intVal");
				BigInteger unscaled = intVal instanceof JObject ? toBigInteger((JObject) intVal) : BigInteger.ZERO;
				return new BigDecimal(unscaled, (Integer) o.field("scale"));
			}
			ClassData date = o.dataFor("java.util.Date");
			if (date != null) {
				return formatDate(o, date);
			}
			for (ClassData cd : o.data) {
				if (MAPS.contains(cd.desc.name)) {
					return toMap(name, cd.annotation);
				}
			}
			for (ClassData cd : o.data) {
				if (COLLECTIONS.contains(cd.desc.name) || "java.util.TreeSet".equals(cd.desc.name)) {
					List<Object> items = objectsOnly(cd.annotation);
					if ("java.util.TreeSet".equals(cd.desc.name) && !items.isEmpty()) {
						items.remove(0); // the comparator
					}
					return toList(name, items);
				}
			}
			if ("java.util.Vector".equals(name) || "java.util.Stack".equals(name)) {
				JArray elements = (JArray) o.field("elementData");
				int count = (Integer) o.field("elementCount");
				List<Object> items = new ArrayList<Object>();
				for (int i = 0; elements != null && i < count; i++) {
					items.add(elements.values[i]);
				}
				return toList(name, items);
			}
			if ("java.util.Arrays$ArrayList".equals(name)) {
				JArray a = (JArray) o.field("a");
				return toList(name, a == null ? new ArrayList<Object>() : Arrays.asList(a.values));
			}
			if (name.startsWith("java.util.Collections$")) {
				if (name.startsWith("java.util.Collections$Empty")) {
					return name.endsWith("Map") ? new MapView(name) : new ListView(name);
				}
				for (String wrapped : new String[] { "m", "c", "list", "element" }) {
					Object inner = o.field(wrapped);
					if (inner != null) {
						return simplify(inner);
					}
				}
			}
			return toBean(o);
		}

		private Object toMap(String className, List<Object> annotation) {
			MapView map = new MapView(className);
			List<Object> items = objectsOnly(annotation);
			for (int i = 0; i + 1 < items.size(); i += 2) {
				if (items.get(i) == null && items.get(i + 1) == null) {
					continue; // ConcurrentHashMap end marker
				}
				map.entries.add(new Object[] { simplify(items.get(i)), simplify(items.get(i + 1)) });
			}
			return map;
		}

		private Object toList(String className, List<Object> items) {
			ListView list = new ListView(className);
			for (Object item : items) {
				list.items.add(simplify(item));
			}
			return list;
		}

		private Object toBean(JObject o) {
			BeanView bean = new BeanView(o.desc.name);
			for (ClassData cd : o.data) {
				for (Map.Entry<String, Object> e : cd.values.entrySet()) {
					String key = e.getKey();
					if (bean.fields.containsKey(key)) {
						key = simpleName(cd.desc.name) + "." + key; // shadowed field of a subclass
					}
					bean.fields.put(key, simplify(e.getValue()));
				}
				if (!cd.annotation.isEmpty()) {
					ListView extra = new ListView("writeObject data");
					for (Object item : cd.annotation) {
						extra.items.add(simplify(item));
					}
					bean.fields.put("(" + simpleName(cd.desc.name) + " writeObject data)", extra);
				}
			}
			return bean;
		}

		private static List<Object> objectsOnly(List<Object> annotation) {
			List<Object> items = new ArrayList<Object>();
			for (Object item : annotation) {
				if (!(item instanceof BlockData)) {
					items.add(item);
				}
			}
			return items;
		}

		private static BigInteger toBigInteger(JObject o) {
			int signum = (Integer) o.field("signum");
			JArray magnitude = (JArray) o.field("magnitude");
			byte[] b = new byte[magnitude == null ? 0 : magnitude.values.length];
			for (int i = 0; i < b.length; i++) {
				b[i] = (Byte) magnitude.values[i];
			}
			return b.length == 0 ? BigInteger.ZERO : new BigInteger(signum, b);
		}

		private static Object formatDate(JObject o, ClassData date) {
			for (Object item : date.annotation) {
				if (item instanceof BlockData && ((BlockData) item).bytes.length >= 8) {
					byte[] b = ((BlockData) item).bytes;
					long millis = 0;
					for (int i = 0; i < 8; i++) {
						millis = (millis << 8) | (b[i] & 0xFF);
					}
					Object nanos = o.field("nanos");
					String pattern = "java.sql.Date".equals(o.desc.name) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss.SSS";
					if (nanos instanceof Integer) {
						millis += ((Integer) nanos) / 1000000; // java.sql.Timestamp keeps the fraction apart
					}
					return new Raw(new SimpleDateFormat(pattern).format(new Date(millis))
							+ " (" + simpleName(o.desc.name) + ")");
				}
			}
			return new Raw(simpleName(o.desc.name) + " (no value)");
		}

		static String simpleName(String className) {
			int cut = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
			return cut >= 0 ? className.substring(cut + 1) : className;
		}

		private static String arrayTypeName(String descriptor) {
			int dims = 0;
			while (dims < descriptor.length() && descriptor.charAt(dims) == '[') {
				dims++;
			}
			String component = descriptor.substring(dims);
			if (component.startsWith("L") && component.endsWith(";")) {
				component = simpleName(component.substring(1, component.length() - 1));
			} else {
				switch (component.charAt(0)) {
				case 'I': component = "int"; break;
				case 'J': component = "long"; break;
				case 'D': component = "double"; break;
				case 'F': component = "float"; break;
				case 'S': component = "short"; break;
				case 'C': component = "char"; break;
				case 'Z': component = "boolean"; break;
				default: component = "byte"; break;
				}
			}
			StringBuilder sb = new StringBuilder(component);
			for (int i = 0; i < dims; i++) {
				sb.append("[]");
			}
			return sb.toString();
		}

		private static String toHex(byte[] b) {
			StringBuilder sb = new StringBuilder(b.length * 2);
			for (byte x : b) {
				sb.append(String.format("%02X", x));
			}
			return sb.toString();
		}
	}

	// ---------------------------------------------------------------- output

	static final class TreePrinter {
		private final PrintStream out;
		private final boolean sortKeys;

		TreePrinter(PrintStream out, boolean sortKeys) {
			this.out = out;
			this.sortKeys = sortKeys;
		}

		void printRoot(Object raw) {
			print("", new Simplifier().simplify(raw), 0);
			out.println();
		}

		private void print(String label, Object v, int depth) {
			StringBuilder line = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				line.append("  ");
			}
			if (label.length() > 0) {
				line.append(label).append(v instanceof BeanView || v instanceof MapView || v instanceof ListView
						? " : " : " = ");
			}
			if (v instanceof MapView) {
				MapView m = (MapView) v;
				out.println(line + Simplifier.simpleName(m.className) + " (" + m.entries.size()
						+ (m.entries.size() == 1 ? " entry)" : " entries)"));
				List<Object[]> entries = new ArrayList<Object[]>(m.entries);
				if (sortKeys) {
					Collections.sort(entries, new Comparator<Object[]>() {
						public int compare(Object[] a, Object[] b) {
							return keyText(a[0]).compareTo(keyText(b[0]));
						}
					});
				}
				for (Object[] e : entries) {
					print(keyText(e[0]), e[1], depth + 1);
				}
			} else if (v instanceof ListView) {
				ListView l = (ListView) v;
				out.println(line + Simplifier.simpleName(l.className) + " [" + l.items.size() + "]");
				for (int i = 0; i < l.items.size(); i++) {
					print("[" + i + "]", l.items.get(i), depth + 1);
				}
			} else if (v instanceof BeanView) {
				BeanView b = (BeanView) v;
				out.println(line + Simplifier.simpleName(b.className) + "  (" + b.className + ")");
				for (Map.Entry<String, Object> e : b.fields.entrySet()) {
					print(e.getKey(), e.getValue(), depth + 1);
				}
			} else {
				out.println(line + scalar(v));
			}
		}

		private static String keyText(Object key) {
			return key instanceof String ? (String) key : scalar(key);
		}

		private static String scalar(Object v) {
			if (v == null) {
				return "null";
			}
			if (v instanceof Raw) {
				return ((Raw) v).text;
			}
			if (v instanceof String) {
				String s = (String) v;
				return "\"" + s.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
						.replace("\t", "\\t").replace("\"", "\\\"") + "\"";
			}
			if (v instanceof BigDecimal) {
				return ((BigDecimal) v).toPlainString();
			}
			if (v instanceof Character) {
				return "'" + v + "'";
			}
			return String.valueOf(v);
		}
	}
}
