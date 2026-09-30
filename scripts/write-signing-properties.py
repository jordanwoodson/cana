#!/usr/bin/env python3
"""Write Gradle signing properties from CI environment values without shell interpolation."""
import os


def escape(value: str) -> str:
    # Properties.load(InputStream) uses Latin-1 plus Java UTF-16 escape sequences.
    encoded = value.encode("utf-16-be")
    result = []
    for offset in range(0, len(encoded), 2):
        code = int.from_bytes(encoded[offset:offset + 2], "big")
        char = chr(code)
        if char in "\\ =:#!":
            result.append("\\" + char)
        elif code < 32 or code >= 127:
            result.append(f"\\u{code:04x}")
        else:
            result.append(char)
    return "".join(result)


def main() -> None:
    fields = {
        "storeFile": "KEYSTORE_FILE",
        "keyAlias": "KEYSTORE_ALIAS",
        "storePassword": "KEYSTORE_PASSWORD",
        "keyPassword": "KEY_PASSWORD",
    }
    content = "".join(f"{key}={escape(os.environ[env])}\n" for key, env in fields.items())
    descriptor = os.open(os.environ.get("KEYSTORE_PROPERTIES_PATH", "key.properties"),
                         os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="ascii") as output:
        os.fchmod(output.fileno(), 0o600)
        output.write(content)


if __name__ == "__main__":
    main()
