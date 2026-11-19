# Security

## Safety model

Java deserialization is a well-known attack surface. Calling `ObjectInputStream.readObject()` on
untrusted bytes can run code through "gadget chains" in classes on the classpath.

`jserial-reader` is built to avoid this:

- It **never** calls `ObjectInputStream.readObject()`, and it never loads, instantiates or runs any class
  named in the stream. It reads the bytes with its own parser and builds plain data structures.
- Every length field (strings, block data, arrays) is checked against the bytes that remain before any
  memory is allocated. A corrupt or hostile stream therefore ends with an error message and exit code 2,
  not a huge allocation.
- Deeply nested input ends with an error message, not a crash.

What it doesn't protect against: a very large (but valid) input still needs memory roughly in proportion
to its size. Use `JAVA_OPTS=-Xmx...` to cap the heap if that matters.

**Output may contain sensitive data.** The tree shows everything in the blob, which can include personal
or financial information. Treat saved output files with the same care as the database they came from.

## Supported versions

Security fixes go into the latest release only.

## Reporting a vulnerability

Please **don't open a public issue** for a security problem. Report it privately through the repository's
**Security** tab ("Report a vulnerability"), which uses GitHub private vulnerability reporting. Include a
synthetic input that reproduces it, never real data. You should get a reply within 14 days.
