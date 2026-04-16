package com.example.securesms.bluetooth

import java.util.UUID

/**
 * Application-defined RFCOMM UUID.  Both host and client advertise/connect
 * using this identifier; it has no bearing on security.
 */
val SECURE_SMS_UUID: UUID = UUID.fromString("c0de5ec5-1a27-4ab9-91cf-3b3d5c25d001")

const val SDP_NAME = "SecureSmsLink"
