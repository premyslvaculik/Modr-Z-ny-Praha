/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.UserSubscriptionEntity

/**
 * Helper for creating the Android notification channel and posting local reminders
 * when a user's Prague parking permit is approaching expiration.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "prague_parking_expiration_channel"

    /**
     * Registers the system notification channel on Android 8.0 (API 26) and higher.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Upozornění na vypršení zóny"
            val descriptionText = "Upozorňuje na blížící se konec platnosti parkovacího oprávnění v modrých zónách."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts an expiration warning notification for a given [UserSubscriptionEntity].
     */
    fun notifyExpiration(context: Context, subscription: UserSubscriptionEntity, daysLeft: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val title = "Blíží se konec platnosti: ${subscription.displayName}"
        val message = if (daysLeft <= 0) {
            "Platnost vašeho parkování v zóně ${subscription.displayName} dnes vypršela! Prodlužte si své oprávnění."
        } else {
            "Zbývá už jen $daysLeft dní do vypršení předplatného v zóně ${subscription.displayName}. Nezapomeňte na prodloužení."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(subscription.id.toInt(), builder.build())
            }
        } catch (_: SecurityException) {
            // Ignored if permission was revoked in runtime
        }
    }
}
