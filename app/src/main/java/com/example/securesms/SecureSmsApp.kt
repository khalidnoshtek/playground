package com.example.securesms

import android.app.Application
import com.example.securesms.crypto.IdentityKeys
import com.example.securesms.crypto.TrustedDeviceStore
import com.example.securesms.sms.SmsRepository

/**
 * Small DI-less container for the objects we want to share between
 * MainActivity and background sessions.  Identity keys and the trust store
 * are created lazily on first access.
 */
class SecureSmsApp : Application() {

    val identity: IdentityKeys by lazy { IdentityKeys.loadOrCreate(this) }
    val trustedDevices: TrustedDeviceStore by lazy { TrustedDeviceStore.get(this) }
    val smsRepository: SmsRepository by lazy { SmsRepository(this) }
}
