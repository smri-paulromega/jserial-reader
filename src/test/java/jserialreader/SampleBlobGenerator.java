/*
 * jserial-reader - prints Java-serialized data as a readable tree.
 * Copyright (C) 2026 smri-paulromega
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jserialreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the test fixture src/test/resources/sample.hex to standard output. Every value is made up;
 * the fixture exists to cover each type the reader decodes. See CONTRIBUTING.md for how to regenerate it.
 */
public final class SampleBlobGenerator {

	enum Status {
		OPEN, PAID
	}

	static class Party implements Serializable {
		private static final long serialVersionUID = 1L;
		String name;
	}

	/** Subclass, so the fixture covers fields spread over a class hierarchy. */
	static final class Customer extends Party {
		private static final long serialVersionUID = 1L;
		String code;
		Status status;
	}

	static final class LineItem implements Serializable {
		private static final long serialVersionUID = 1L;
		String sku;
		BigDecimal price;
		int quantity;
		boolean taxable;

		LineItem(String sku, String price, int quantity, boolean taxable) {
			this.sku = sku;
			this.price = new BigDecimal(price);
			this.quantity = quantity;
			this.taxable = taxable;
		}
	}

	private SampleBlobGenerator() {
	}

	public static void main(String[] args) throws IOException {
		Customer customer = new Customer();
		customer.name = "Jane Doe";
		customer.code = "C-0001";
		customer.status = Status.PAID;

		List<LineItem> items = new ArrayList<LineItem>();
		items.add(new LineItem("A-100", "12.50", 1, true));
		items.add(new LineItem("B-200", "3.75", 2, false));

		Map<String, Object> meta = new TreeMap<String, Object>();
		meta.put("source", "unit-test");
		meta.put("version", Integer.valueOf(2));

		List<Object> self = new ArrayList<Object>();
		self.add("first");
		self.add(self);

		Map<String, Object> m = new HashMap<String, Object>();
		m.put("ORDER_NO", "ORD-0001");
		m.put("AMOUNT", new BigDecimal("20.00"));
		m.put("DISCOUNT", new BigDecimal("-1.255"));
		m.put("BIG_NUMBER", new BigInteger("123456789012345678901234567890"));
		m.put("COUNT", Integer.valueOf(3));
		m.put("LONG_ID", Long.valueOf(12345678901L));
		m.put("RATE", Double.valueOf(0.075));
		m.put("ACTIVE", Boolean.TRUE);
		m.put("GRADE", Character.valueOf('B'));
		m.put("CREATED", new Date(1700000000000L));
		m.put("UPDATED", new Timestamp(1700000000123L));
		m.put("DUE", new java.sql.Date(1700000000000L));
		m.put("NOTE", "line 1\nline 2\t\"quoted\" \\ end");
		m.put("EMPTY", "");
		m.put("NOTHING", null);
		m.put("BYTES", new byte[] { 0x01, 0x02, (byte) 0xFF });
		m.put("CODES", new int[] { 7, 8, 9 });
		m.put("TAGS", new String[] { "red", "blue" });
		m.put("FIXED_LIST", Arrays.asList("x", "y"));
		m.put("SET", new LinkedHashSet<String>(Arrays.asList("one", "two")));
		m.put("META", meta);
		m.put("ITEMS", items);
		m.put("CUSTOMER", customer);
		m.put("BILL_TO", customer); // the same object twice, written as a back-reference
		m.put("SELF_LIST", self); // a list that contains itself

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bytes);
		out.writeObject(m);
		out.close();

		StringBuilder hex = new StringBuilder("0x");
		for (byte b : bytes.toByteArray()) {
			hex.append(String.format("%02X", b));
		}
		System.out.println(hex);
	}
}
