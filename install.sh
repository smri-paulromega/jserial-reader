#!/bin/sh
# Puts readblob on your PATH. Usage: ./install.sh [--uninstall]
#
# Linux / macOS: builds the jar if needed and adds bin/ to PATH in ~/.bashrc or ~/.zshrc.
# Git Bash on Windows: runs install.ps1, which sets the Windows user PATH. Git Bash, cmd and
# PowerShell all inherit that, so no ~/.bashrc line is needed.
set -eu
ROOT=$(cd "$(dirname "$0")" && pwd)
MARK="# added by jserial-reader install.sh"

case "$(uname -s)" in
	MINGW* | MSYS* | CYGWIN*)
		if [ "${1:-}" = "--uninstall" ]; then FLAG=-Uninstall; else FLAG=; fi
		# shellcheck disable=SC2086
		exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$ROOT/install.ps1")" $FLAG
		;;
esac

case "${SHELL:-}" in
	*/zsh) RC="$HOME/.zshrc" ;;
	*) RC="$HOME/.bashrc" ;;
esac

if [ "${1:-}" = "--uninstall" ]; then
	if [ -f "$RC" ]; then
		grep -vF "$MARK" "$RC" > "$RC.jserial-tmp" || true
		mv "$RC.jserial-tmp" "$RC"
	fi
	echo "Removed jserial-reader from $RC. Open a new terminal."
	exit 0
fi

if [ ! -f "$ROOT/lib/jserial-reader.jar" ] && [ ! -f "$ROOT/build/jserial-reader.jar" ]; then
	sh "$ROOT/build.sh"
fi
chmod +x "$ROOT/bin/readblob"

if [ -f "$RC" ] && grep -qF "$MARK" "$RC"; then
	echo "Already installed in $RC."
else
	printf 'export PATH="%s/bin:$PATH" %s\n' "$ROOT" "$MARK" >> "$RC"
	echo "Added $ROOT/bin to PATH in $RC."
fi
echo "Open a new terminal and run: readblob --version"
