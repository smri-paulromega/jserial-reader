# jserial-reader

[![CI](https://github.com/smri-paulromega/jserial-reader/actions/workflows/ci.yml/badge.svg)](https://github.com/smri-paulromega/jserial-reader/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Java 8+](https://img.shields.io/badge/Java-8%2B-orange.svg)

`readblob` prints Java-serialized data as a readable tree. It's meant for the blobs that applications
store in database columns (`VARBINARY`, `BLOB`, `bytea`) using `ObjectOutputStream`.

```
$ readblob -f order.hex
HashMap (25 entries)
  AMOUNT = 20.00
  CREATED = 2023-11-14 22:13:20.000 (Date)
  CUSTOMER : Customer  (com.example.Customer)
    name = "Jane Doe"
    code = "C-0001"
    status = Status.PAID
  ITEMS : ArrayList [2]
    [0] : LineItem  (com.example.LineItem)
      quantity = 1
      price = 12.50
      sku = "A-100"
  ...
```

## Why

To read one of these blobs, you usually need the original application's classes, in the same version
that wrote it. `jserial-reader` doesn't need any of that:

- **No classes needed.** It parses the
  [Java Object Serialization Stream Protocol](https://docs.oracle.com/javase/8/docs/platform/serialization/spec/protocol.html)
  directly, so the classes that wrote the blob don't have to be on the classpath.
- **Version-proof.** A `serialVersionUID` mismatch can't stop it.
- **Deep.** It follows nested objects, lists, maps, arrays, and back-references.
- **Safe on untrusted data.** It never calls `ObjectInputStream.readObject()` and never instantiates a
  class from the stream, so deserialization gadget chains can't run. See [SECURITY.md](SECURITY.md).
- **Exact numbers.** `BigDecimal` values keep their scale (`20.00` stays `20.00`), with no rounding.
- **Small.** One source file and no dependencies. It runs on Java 8 and later.

## Install

You need **Java 8 or later** (`java -version`).

### From a release

1. Download `jserial-reader-<version>.zip` (Windows) or `.tar.gz` (Linux/macOS) from
   [Releases](https://github.com/smri-paulromega/jserial-reader/releases), and extract it anywhere.
2. Put `readblob` on your PATH:

   | System | Run | What it does |
   | --- | --- | --- |
   | Windows | `install.cmd` | Adds `bin\` to your **user** PATH. No admin rights needed. Works in cmd, PowerShell and Git Bash. |
   | Linux / macOS | `./install.sh` | Adds `bin/` to PATH in `~/.bashrc` (or `~/.zshrc` if zsh is your shell). |

3. Open a new terminal and run `readblob --version`.

To remove it from PATH, run `install.cmd -Uninstall` or `./install.sh --uninstall`, then delete the folder.

### From source

```sh
git clone https://github.com/smri-paulromega/jserial-reader.git
cd jserial-reader
sh build.sh        # or build.cmd on Windows; needs a JDK 8 or later
sh install.sh      # or install.cmd
```

You can also skip the install step and run `bin/readblob` (or `bin\readblob.cmd`) by its path. On
Linux and macOS, a symlink works too: `ln -s "$PWD/bin/readblob" ~/.local/bin/readblob`.

## Usage

```
readblob [options] <input>
```

| Input (pick one) | |
| --- | --- |
| `--clip` | Read the hex from the clipboard. **Recommended** for values copied from a database tool. |
| `-f <file>` | Read a file holding the hex text, or the raw binary bytes. |
| `-` | Read the hex from standard input. |
| `0xACED0005...` | Hex pasted as an argument. Small values only; see [Limits](#limits). |

| Options | |
| --- | --- |
| `-o, --output <file>` | Write the tree to `<file>` (UTF-8) instead of the console. Replaces an existing file. |
| `--stream-order` | Keep map keys in stored order instead of sorting them A&ndash;Z. |
| `-h, --help` | Show help. |
| `-V, --version` | Show the version and license. |

### Examples

```sh
readblob --clip                         # value copied from a query result
readblob --clip -o order.txt            # same, saved to a file
readblob -f order.hex                   # hex saved to a file
readblob -f order.bin                   # raw bytes exported from the database
psql -Atc "SELECT encode(data, 'hex') FROM my_table WHERE id = 1" | readblob -
```

Input can be with or without `0x`, wrapped in quotes or in `<x>...</x>` tags (see below), and split
across lines. Files can be ANSI, UTF-8, or UTF-16, so Windows PowerShell's `>` output works too.

> **Common mistake:** `readblob blob.txt` without `-f` treats the text `blob.txt` as the hex value
> and fails with `Not a hex digit 'l' at position 1`. A file name always needs `-f`.

### Saving the output

`-o file.txt` writes UTF-8 and is the reliable option. The file is created only after the input
parses, so bad input never leaves a half-written file. Errors and the `Wrote ...` confirmation go to
stderr, never into the file.

`> file.txt` also works. On Windows it uses the console code page, which can garble non-ASCII text.

### Time zones and JVM options

Dates print in the local time zone. Extra JVM options go in `JAVA_OPTS`, and `JAVA_HOME` picks the Java
installation:

```sh
JAVA_OPTS="-Duser.timezone=UTC" readblob -f order.hex       # sh
set JAVA_OPTS=-Duser.timezone=UTC & readblob -f order.hex   # cmd
```

## Getting the hex out of a database

| Database | Query |
| --- | --- |
| SQL Server | `SELECT blob_column FROM my_table WHERE id = 1` (SSMS shows it as `0x...`) |
| PostgreSQL | `SELECT encode(blob_column, 'hex') FROM my_table WHERE id = 1` |
| MySQL / MariaDB | `SELECT HEX(blob_column) FROM my_table WHERE id = 1` |
| Oracle | `SELECT RAWTOHEX(blob_column) FROM my_table WHERE id = 1` (up to 2000 bytes), or `DBMS_LOB` for more |

## Limits

- **SSMS truncates long values.** With *Results to Grid*, SQL Server Management Studio returns at most
  65535 characters of a non-XML column. For a larger blob, select it as XML and copy it from the XML
  window. The `<x>` tags are ignored when reading.

  ```sql
  SELECT CONVERT(XML, '<x>' + CONVERT(VARCHAR(MAX), blob_column, 1) + '</x>')
  FROM my_table WHERE id = 1
  ```

  If a value was cut short anyway, `readblob` reports `The data ends early`.
- **Command-line length.** cmd.exe allows at most 8191 characters per command line, so long hex won't
  fit as an argument. Use `--clip`, `-f`, or `-`.
- **Not supported:** old-format `Externalizable` data (the pre-JDK 1.2 protocol) and streams that hold a
  serialized exception. Both end with an error message, not with wrong output.
- **Custom `writeObject` data** that isn't a known JDK type is shown as raw `<N bytes: ...>` blocks next
  to the object's fields, because its layout is only known to the class that wrote it.

### Types it decodes

`HashMap`, `Hashtable`, `TreeMap`, `LinkedHashMap`, `ConcurrentHashMap`, `IdentityHashMap`,
`WeakHashMap`, `ArrayList`, `LinkedList`, `ArrayDeque`, `Vector`, `Stack`, `HashSet`, `LinkedHashSet`,
`TreeSet`, `PriorityQueue`, `CopyOnWriteArrayList`, `Arrays.asList`, the `Collections` wrappers,
boxed primitives, `BigDecimal`, `BigInteger`, `Date`, `java.sql.Date`, `Timestamp`, enums, and arrays.
Any other class is shown with its fields, including the fields it inherits from its superclasses.

## Exit codes

| Code | Meaning |
| --- | --- |
| 0 | Success |
| 1 | Bad arguments, unreadable input, or the output file could not be written |
| 2 | The input is not a valid Java serialization stream, or it is truncated or corrupt |

## How it works

All the code is in [`src/main/java/jserialreader/JSerialReader.java`](src/main/java/jserialreader/JSerialReader.java).
Each stage hands its result to the next:

```
main(args)
  |- parse options (--clip, -f, -o, ...)
  |- decodeInput / decodeHex      hex text or raw bytes   -> byte[]
  |- StreamParser.parse()         byte[]                  -> JObject / JArray / String ... (raw stream model)
  `- TreePrinter.printRoot()
        |- Simplifier.simplify()  raw model -> MapView / ListView / BeanView / scalars
        `- print()                indented tree -> console or the -o file
```

- **StreamParser** follows the stream grammar (`TC_OBJECT`, `TC_CLASSDESC`, `TC_REFERENCE`, ...) and
  keeps a handle table, so back-references point to the same object. Every length field is checked
  against the remaining data before anything is allocated.
- **Simplifier** recognises JDK types by class name and rebuilds their values from the serialized
  fields. For example, `BigDecimal` is rebuilt from `intVal` and `scale`, and a map from its
  `writeObject` key/value pairs.
- **TreePrinter** sorts map keys (unless you pass `--stream-order`), quotes strings, and escapes `\r`,
  `\n`, `\t`, `\` and `"`, so every value stays on one line.

## Project layout

| Path | Purpose |
| --- | --- |
| `src/main/java/` | The tool (one file) |
| `src/test/` | Generator for the synthetic test blob, the blob itself, and its expected output |
| `bin/readblob`, `bin/readblob.cmd` | Launchers for sh and Windows |
| `build.sh`, `build.cmd` | Build `build/jserial-reader.jar` |
| `test.sh` | Regression tests |
| `install.sh`, `install.cmd`, `install.ps1` | Put `readblob` on PATH |
| `VERSION` | Version number, stamped into the jar |

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). **Never attach a real blob
to an issue.** It may contain personal or financial data. Build a synthetic one that reproduces the
problem instead.

## License

Copyright (C) 2026 smri-paulromega

This program is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License](LICENSE), version 3 or (at your option) any later version. It comes with
NO WARRANTY.
