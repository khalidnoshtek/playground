# SecureSms Link

A two-phone, read-only companion app: the **host** phone (with the SIM) lets
the **client** phone (without a SIM) read its SMS over an encrypted Bluetooth
RFCOMM link — think Microsoft Phone Link, but phone-to-phone and entirely
offline.

## Design goals

1. **Secure** — no plaintext data leaves the host. All application traffic is
   encrypted with AES-256-GCM, keyed by a fresh per-session ECDH exchange
   (curve P-256) and bound to user-verified numeric comparison pairing.
2. **Ethical** — the protocol is strictly read-only. The client cannot send
   or delete SMS, and the host requires explicit runtime SMS permission plus
   an on-screen confirmation of a 6-digit code before any data is shared.
3. **Offline** — uses Bluetooth Classic RFCOMM; no cloud, no account,
   no internet connectivity required on either device.

## Security model

| Threat                          | Mitigation                                              |
|---------------------------------|---------------------------------------------------------|
| Eavesdropping on BT link        | AES-256-GCM; per-session ECDH shared secret             |
| Replay / nonce reuse            | Directional keys + monotonic 96-bit nonces             |
| Man-in-the-middle during pairing| User-confirmed 6-digit SAS derived from transcript      |
| MITM on reconnect               | Peer identity key pinned in EncryptedSharedPreferences  |
| Impersonation by rogue app      | ECDSA signature over handshake transcript              |
| Key exfiltration at rest        | Keys stored via AndroidX `MasterKey` + Keystore-backed KEK |

The handshake is:

```
client → host : ephem_c || id_c || nonce_c
host  → client: ephem_h || id_h || nonce_h
(both derive: shared = ECDH(ephem); keys, SAS = HKDF(shared, transcript))
both          : ECDSA_id(transcript)    # signed both ways, cross-verified
(first time)  : user confirms SAS on both phones → pin id_peer
(next times)  : id_peer must equal pinned; signature must verify
→ encrypted JSON frames over RFCOMM
```

## Build

Requires Android Studio Hedgehog+ (AGP 8.5, Kotlin 1.9). From the project
root:

```bash
# Generate the Gradle wrapper (one-time) then build:
gradle wrapper
./gradlew :app:assembleDebug
```

Install the resulting APK on both phones. No signing key is bundled.

## Usage

1. Pair the two phones in the system Bluetooth settings (standard SSP).
2. Launch the app on both phones.
3. On the phone with the SIM: choose **Host** and grant SMS permissions.
4. On the other phone: choose **Client**, pick the host from bonded devices.
5. Confirm that the same 6-digit code appears on both phones, then tap
   "Codes match, trust" on each side.
6. The client now sees conversations and messages. New incoming SMS are
   pushed live from host → client.

## Limitations / TODO

- No foreground service yet: if the host app is backgrounded for long the
  connection may drop. A follow-up can add a notification-backed service.
- MMS and RCS are out of scope.
- The client is deliberately read-only. Reply-from-client would require
  default-SMS-app status on the host and an explicit per-message user
  approval flow.
- Multi-client support is partial — the server accepts only the currently
  connected client.
