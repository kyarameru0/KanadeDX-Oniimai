"""Read-only verification of the supplied KanadeDX APK against this module.

Usage: python scripts/verify_target.py --apk "path/to/KanadeDX.apk"
Selects the 1.60 or 1.65 profile by the ELF build ID, then checks all hashes.
Requires only Python's standard library. ZIP members are read into memory; no
original APK content is extracted, modified, or saved to disk.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile


IL2CPP_MEMBER = "lib/arm64-v8a/libil2cpp.so"
METADATA_MEMBER = "assets/bin/Data/Managed/Metadata/global-metadata.dat"
EXPECTED_FUNCTION_COUNT = 55


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_hash(actual, expected, label):
    require(isinstance(expected, str) and re.fullmatch(r"[0-9a-f]{64}", expected),
            f"Invalid recorded {label} SHA-256")
    require(actual == expected, f"{label} SHA-256 mismatch: expected {expected}, got {actual}")


def parse_bytes(contents):
    parts = [part.strip() for part in contents.split(",") if part.strip()]
    require(parts and all(re.fullmatch(r"0x[0-9a-fA-F]{1,2}", p) for p in parts),
            "Malformed byte array in target profile header")
    return bytes(int(part, 16) for part in parts)


def fingerprint16(data):
    require(len(data) == 16, "Fingerprint requires 16 bytes")
    value = 0xcbf29ce484222325
    for byte in data:
        value = ((value ^ byte) * 0x100000001b3) & 0xffffffffffffffff
    return value


def parse_header(header):
    rva_pairs = re.findall(r"\bRVA_(\w+)\s*=\s*(0x[0-9a-fA-F]+)\s*;", header)
    sig_pairs = re.findall(r"\bSIG_(\w+)\s*=\s*(0x[0-9a-fA-F]+)ULL\s*;", header)
    rvas = {key: int(value, 16) for key, value in rva_pairs}
    signatures = {key: int(value, 16) for key, value in sig_pairs}
    require(len(rvas) == len(rva_pairs) and len(signatures) == len(sig_pairs),
            "Duplicate RVA or signature declarations in target profile header")
    require(rvas.keys() == signatures.keys(), "RVA/signature names do not match")
    require(len(rvas) == EXPECTED_FUNCTION_COUNT,
            f"Expected {EXPECTED_FUNCTION_COUNT} function fingerprints, found {len(rvas)}")
    ids = re.findall(r"\bBUILD_ID\s*\[\s*\]\s*=\s*\{([^}]+)\}\s*;", header)
    require(len(ids) == 1, "Expected one BUILD_ID byte array")
    return rvas, signatures, parse_bytes(ids[0])


def program_headers(binary):
    require(len(binary) >= 64, "Truncated ELF header")
    elf = struct.unpack_from("<16sHHIQQQIHHHHHH", binary)
    ident, kind, machine, version = elf[:4]
    require(ident[:7] == b"\x7fELF\x02\x01\x01", "Expected ELF64, little endian, version 1")
    require(kind == 3 and machine == 183 and version == 1, "Expected an AArch64 shared object")
    offset, entry_size, count = elf[5], elf[9], elf[10]
    require(entry_size == 56 and 0 < count <= 128, "Unexpected ELF program header layout")
    require(offset + count * entry_size <= len(binary), "Truncated ELF program header table")
    headers = [struct.unpack_from("<IIQQQQQQ", binary, offset + i * entry_size)
               for i in range(count)]
    for kind, _, file_offset, _, _, file_size, memory_size, _ in headers:
        require(file_offset + file_size <= len(binary), "ELF segment extends beyond the file")
        if kind == 1:
            require(file_size <= memory_size, "ELF PT_LOAD file size exceeds memory size")
    return headers


def elf_build_id(binary, headers):
    ids = []
    for kind, _, offset, _, _, size, _, _ in headers:
        if kind != 4:  # PT_NOTE
            continue
        end = offset + size
        while offset + 12 <= end:
            name_size, desc_size, note_type = struct.unpack_from("<III", binary, offset)
            offset += 12
            name_padded, desc_padded = (name_size + 3) & ~3, (desc_size + 3) & ~3
            require(offset + name_padded + desc_padded <= end, "Truncated ELF note")
            name = binary[offset:offset + name_size]
            descriptor = binary[offset + name_padded:offset + name_padded + desc_size]
            if note_type == 3 and name == b"GNU\0":
                ids.append(descriptor)
            offset += name_padded + desc_padded
    require(len(ids) == 1, "Expected exactly one GNU ELF build ID")
    return ids[0]


def code_at_rva(binary, headers, rva, size):
    matches = []
    for kind, flags, offset, virtual, _, file_size, _, _ in headers:
        if kind == 1 and virtual <= rva and rva + size <= virtual + file_size:
            require(flags & 1, f"RVA {rva:#x} does not refer to executable code")
            file_offset = offset + rva - virtual
            matches.append(binary[file_offset:file_offset + size])
    require(len(matches) == 1, f"RVA {rva:#x} must map to exactly one file-backed PT_LOAD segment")
    return matches[0]


def profiles(root):
    paths = [root / "target-build.json", *sorted((root / "targets").glob("kanade-*.json"))]
    result = [json.loads(path.read_text(encoding="utf-8")) for path in paths]
    ids = [target["elf_build_id"] for target in result]
    require(len(ids) == len(set(ids)), "Duplicate profile build IDs")
    return result


def verify(apk, root):
    with zipfile.ZipFile(apk, "r") as archive:
        names = archive.namelist()
        for member in (IL2CPP_MEMBER, METADATA_MEMBER):
            require(names.count(member) == 1, f"Expected exactly one ZIP member: {member}")
        binary = archive.read(IL2CPP_MEMBER)
        metadata = archive.read(METADATA_MEMBER)
    headers = program_headers(binary)
    identity = elf_build_id(binary, headers)
    candidates = [target for target in profiles(root) if target["elf_build_id"] == identity.hex()]
    require(len(candidates) == 1, f"Unsupported ELF build ID: {identity.hex()}")
    target = candidates[0]
    header_path = (root / target["profile_header"]).resolve()
    require(header_path.is_relative_to((root / "app/src/main/cpp").resolve()), "Invalid profile header path")
    header = header_path.read_text(encoding="utf-8")
    rvas, signatures, header_id = parse_header(header)
    declared = {}
    for group in ("hooks", "led_hooks", "stats_functions", "ui_functions", "boot_functions", "album_functions", "aime_functions"):
        for key, address in target[group].items():
            name = ("LED_" if group == "led_hooks" else "UI_" if group == "ui_functions" else "BOOT_" if group == "boot_functions" else "ALBUM_" if group == "album_functions" else "AIME_" if group == "aime_functions" else "") + key.upper()
            require(name not in declared, f"Duplicate target manifest RVA: {name}")
            declared[name] = int(address, 16)
    require(declared == rvas, "Target manifest RVAs differ from profile header")
    for key, symbol in (("settings_typeinfo", "DATA_UI_SETTINGS_TYPEINFO"), ("main_group", "FIELD_UI_MAIN_GROUP")):
        values = re.findall(r"\b" + symbol + r"\s*=\s*(0x[0-9a-fA-F]+)\s*;", header)
        require(len(values) == 1 and int(values[0], 16) == int(target["ui_layout"][key], 16),
                f"UI metadata layout mismatch: {symbol}")
    require(header_id.hex() == target["elf_build_id"], "Recorded ELF build IDs disagree")
    for key,symbol in (("start_button","FIELD_BOOT_START_BUTTON"),("start_callback","FIELD_BOOT_START_CALLBACK")):
        values=re.findall(r"\b"+symbol+r"\s*=\s*(0x[0-9a-fA-F]+)\s*;",header)
        require(len(values)==1 and int(values[0],16)==int(target["boot_layout"][key],16),f"Boot field offset mismatch: {symbol}")
    check_hash(sha256_file(apk), target["apk_sha256"], "APK")
    check_hash(hashlib.sha256(binary).hexdigest(), target["il2cpp_sha256"], "libil2cpp.so")
    check_hash(hashlib.sha256(metadata).hexdigest(), target["metadata_sha256"], "IL2CPP metadata")
    require(len(metadata) >= 8, "Truncated IL2CPP metadata header")
    magic, metadata_version = struct.unpack_from("<II", metadata)
    require(magic == 0xFAB11BAF, "Unexpected IL2CPP metadata magic")
    require(metadata_version == target["metadata_version"], "IL2CPP metadata version mismatch")
    require(elf_build_id(binary, headers) == header_id, "ELF build ID mismatch")
    for key, rva in rvas.items():
        require(fingerprint16(code_at_rva(binary, headers, rva, 16)) == signatures[key],
                f"Function fingerprint mismatch: {key} at {rva:#x}")
    print(f"PASS: {target['target']}")
    print("PASS: APK, libil2cpp.so and metadata SHA-256 hashes")
    print(f"PASS: ELF build ID {header_id.hex()}, metadata version {metadata_version}")
    print(f"PASS: {len(rvas)} RVA mappings and hashed 16-byte function fingerprints")
    print("Read-only verification completed; no APK content was saved or changed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="Original supported KanadeDX APK path (1.60 or 1.65)")
    args = parser.parse_args()
    try:
        verify(args.apk, Path(__file__).resolve().parents[1])
    except (OSError, ValueError, KeyError, TypeError, struct.error, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
