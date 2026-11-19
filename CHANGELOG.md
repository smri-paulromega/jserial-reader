# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-11-19

### Added

- `readblob` prints Java-serialized data as a readable tree, without the classes that wrote it.
- Input from the clipboard (`--clip`), a file (`-f`), standard input (`-`), or an argument.
  Accepts hex with or without `0x`, quotes, `<x>` XML wrappers, line breaks, ANSI/UTF-8/UTF-16 text,
  or raw binary.
- `-o` / `--output` writes the tree to a UTF-8 file.
- `--stream-order` keeps map keys in stored order.
- `-V` / `--version`.
- Decodes the common JDK collections, maps, boxed primitives, `BigDecimal`, `BigInteger`, dates,
  enums and arrays. Detects back-references and circular references.
- Length checks that turn corrupt or truncated data into a clear error message instead of a crash.
- Launchers for sh and Windows, installers that put `readblob` on the user PATH, and builds that need
  only a JDK.
- Regression tests and CI on Java 8, 11, 17 and 21 (Linux, Windows, macOS).

[Unreleased]: https://github.com/smri-paulromega/jserial-reader/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/smri-paulromega/jserial-reader/releases/tag/v1.0.0
