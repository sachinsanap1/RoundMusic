package com.roundmusic.player.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Which permission lets us read music files on this Android version. */
object AudioPermission {

    val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO        // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE   // Android 12 and older
        }

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
