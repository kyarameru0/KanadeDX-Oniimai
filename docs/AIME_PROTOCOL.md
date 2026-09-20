# Controller Aime reader protocol

The separate `onii-mai NFC` CDC interface is used for card reading and its own reader LED. This document summarizes the current implementation; it does not describe a card writer or a replacement account server. [Implementation](../app/src/main/java/io/oniimai/kanade/AimeChannel.java)

## Framing and initialization

115200 baud, 8N1. NFC uses DTR ON / RTS OFF; other interfaces keep their own line state. Requests and replies are serialized and matched by address, sequence, command and lengths.

```text
request:  E0 length address sequence command payload_length payload... checksum
response: E0 length address sequence command status payload_length payload... checksum
```

Length includes its own byte and excludes sync/checksum. Checksum is the modulo-256 sum after sync and before checksum. Escape each E0/D0 byte as D0 followed by that byte minus one. Fragmented/coalesced USB reads are handled by the parser. Malformed frames are not accepted as card data.

Initialization uses normal mode/reset62, firmware30, hardware32, documented reader-key loading54/50 and RF stop41. Keys are loaded into reader memory; card contents and firmware are not written. An authorized game scan enables RF40 and detects with42. Multiple cards report an issue instead of picking an account.

## RF sequencing

The USB path preserves the sequence established during local Windows/Android comparison: command turnaround of at least25ms, approximately300ms RF settling before detect, RF-off spacing and a separately managed held-card cache. MIFARE keeps RF active through select/authenticate/block reads. FeliCa uses polling and read-without-encryption operations with strict response/identity validation. The observed one-byte FeliCa `01` response can trigger one delayed repeat of the same polling request. A second short/invalid response fails; it does not fabricate card data.

The detect deadline is longer than ordinary command deadlines. Missing responses close the channel rather than allowing a late reply to satisfy a new transaction. Reconnect and per-scan RF cooldown are independent. These are tested compatibility choices for the observed setup, not a proof of identical behavior in every firmware revision.

## Card data

- MIFARE: select/authenticate with the documented reader keys, read blocks1 and2, reject the NBGIC marker and invalid/empty decimal BCD. Custom block1 data is accepted. The submitted number comes from the card and passes format checks; these checks do not prove issuer origin or account ownership.
- FeliCa Amusement IC: check NFC-F system88B4 and SEGA DFC0078, read SPAD0, decode through the attributed MPL implementation, and validate allowed issuer prefixes500/501.
- Card identity and access-code buffers are transient. Diagnostic events retain command/status/length/timing categories, not UIDs, access codes or block contents.

No MIFARE write53, FeliCa write08, registration, balance modification, firmware flashing or direct AimeDB request is implemented here.

The source embeds fixed authentication constants and decoding tables. Read-only protocol behavior is distinct from card/service permission; see [Card behavior and permission boundaries](CARD_RIGHTS.md).

## Reader LED and feedback

Reader lighting uses NFC bus address08. Normal modeF5 is acknowledged; RGB81 sends red/green/blue without requiring a matching response. Unrelated lighting replies cannot complete card requests. A lighting change is separated from subsequent detection to preserve transport ordering. Ring/button/ceiling lighting use separate paths.

Game scan generation gates both success and failure delivery. Unsupported, multiple, unreadable/invalid cards and transport failures have distinct issue categories. An empty RF field is not an error. The original game's error flow is used; no synthetic access code represents failure. A completed error at the start screen may advance into the existing card-error screen through the adapter-owned read event.

## References and licensing

- [Arduino-Aime-Reader](https://github.com/Sucareto/Arduino-Aime-Reader/tree/934484db83773477f77fee330ea79ca1c5d59e98): wire commands and examples.
- [TeamTofuShop/segatools](https://gitea.tendokyu.moe/TeamTofuShop/segatools/src/commit/8a966b2454185699e8b7d256f4ab43384c274ba0): NFC/card format reference. Its default emulator is not evidence for physical USB timing.
- [Arcade Docs](https://sega.bsnk.me/allnet/amusement_ic/): Amusement IC format.
- [PN532-Aime-Reader decoder](https://github.com/zhicheng233/PN532-Aime-Reader/blob/8feaf84860a17f10ebd12c3827a17cc7deaa01c3/src/NfcAime.Dll/FeliCaDecryptor.cs): adapted code/tables, MPL-2.0. [License](../licenses/PN532-Aime-Reader-MPL-2.0.txt).

Tests use synthetic identities/codes. They check framing, corruption, cancellation, read-only command flow, deduplication and stale scans. Physical compatibility and legal permission are separate questions. [Validation](VALIDATION.md) · [Legal review](LEGAL_REVIEW.md)
