#!/bin/sh
# Regression tests for build/jserial-reader.jar. Run build.sh first.
# Works on Linux, macOS and Git Bash on Windows; CI runs it on each supported Java version.
set -u
cd "$(dirname "$0")"

JAR=build/jserial-reader.jar
FIXTURE=src/test/resources/sample.hex
EXPECTED=src/test/resources/sample.expected.txt
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
if [ ! -f "$JAR" ]; then
	echo "test.sh: $JAR not found; run build.sh first" >&2
	exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
FAILURES=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; FAILURES=$((FAILURES + 1)); }

# dates print in the local time zone, so pin it for repeatable output
run() { "$JAVA" -Duser.timezone=UTC -jar "$JAR" "$@"; }

# same_text <name> <expected file> <actual file>: compare, ignoring CRLF vs LF
same_text() {
	tr -d '\r' < "$2" > "$TMP/a"
	tr -d '\r' < "$3" > "$TMP/b"
	if cmp -s "$TMP/a" "$TMP/b"; then pass "$1"; else fail "$1"; diff "$TMP/a" "$TMP/b" | head -20; fi
}

# exits <name> <code> <expected stderr text> -- <args...>
exits() {
	name=$1 code=$2 text=$3
	shift 4
	run "$@" > /dev/null 2> "$TMP/err"
	got=$?
	if [ "$got" -eq "$code" ] && grep -qF -- "$text" "$TMP/err"; then
		pass "$name"
	else
		fail "$name (exit $got, stderr: $(cat "$TMP/err"))"
	fi
}

run -f "$FIXTURE" > "$TMP/tree.txt"
same_text "-f prints the expected tree" "$EXPECTED" "$TMP/tree.txt"

run - < "$FIXTURE" > "$TMP/stdin.txt"
same_text "- reads standard input" "$EXPECTED" "$TMP/stdin.txt"

run "$(cat "$FIXTURE")" > "$TMP/arg.txt"
same_text "hex as an argument" "$EXPECTED" "$TMP/arg.txt"

run -f "$FIXTURE" -o "$TMP/out.txt" 2> "$TMP/wrote.txt"
same_text "-o writes the same tree to a file" "$EXPECTED" "$TMP/out.txt"
if grep -q '^Wrote ' "$TMP/wrote.txt"; then pass "-o reports the file on stderr"; else fail "-o reports the file on stderr"; fi

if run -f "$FIXTURE" --stream-order > "$TMP/order.txt" && [ -s "$TMP/order.txt" ]; then
	pass "--stream-order"
else
	fail "--stream-order"
fi

if run --version | head -1 | grep -qx "jserial-reader $(tr -d '\r\n' < VERSION)"; then
	pass "--version matches VERSION"
else
	fail "--version matches VERSION"
fi

if sh bin/readblob --version > /dev/null; then pass "bin/readblob launcher"; else fail "bin/readblob launcher"; fi

head -c 200 "$FIXTURE" > "$TMP/truncated.hex"
exits "a file name without -f" 1 "Not a hex digit 'l' at position 1" -- blob.txt
exits "-o without a file name" 1 "-o needs a file name" -- -f "$FIXTURE" -o
exits "-f without a file name" 1 "-f needs a file name" -- -f
exits "odd number of digits" 1 "odd number of digits" -- 0xACE
exits "truncated data" 2 "ends early" -- -f "$TMP/truncated.hex"
exits "not a serialization stream" 2 "does not start with 0xACED" -- 0x12345678
# a byte[] whose length field claims 0x7FFFFFF0 elements, then one that is negative
exits "corrupt array length" 2 "ends early" -- 0xACED0005757200025B42ACF317F8060854E002000078707FFFFFF0
exits "negative array length" 2 "negative array size" -- 0xACED0005757200025B42ACF317F8060854E00200007870FFFFFFF0

# Object[] nested 50000 deep: the first level defines the class, the rest refer back to it
awk 'BEGIN {
	printf "0xACED000575720013%s90CE589F1073296C020000787000000001", "5B4C6A6176612E6C616E672E4F626A6563743B"
	for (i = 1; i < 50000; i++) printf "7571007E000000000001"
	print "70"
}' > "$TMP/deep.hex"
exits "deeply nested data" 2 "nested too deeply" -- -f "$TMP/deep.hex"

run 0xZZ -o "$TMP/never.txt" 2> /dev/null
if [ ! -e "$TMP/never.txt" ]; then pass "bad input leaves no -o file"; else fail "bad input leaves no -o file"; fi

if [ "$FAILURES" -eq 0 ]; then
	echo "All tests passed."
else
	echo "$FAILURES test(s) failed."
	exit 1
fi
