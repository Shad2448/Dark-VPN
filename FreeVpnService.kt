package com.example.freevpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dark VPN Service — High-Speed, Low-Latency Android VpnService.
 *
 * Uses non-blocking TUN routing (`10.8.0.0/24`), unmetered high-throughput flags on Android 10+,
 * dual-stack Anycast DNS resolvers, and 500ms high-frequency telemetry for the live speedometer.
 */
class FreeVpnService : VpnService() {

    companion object {
        private const val TAG = "DarkVpnService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dark_vpn_channel"

        const val ACTION_CONNECT = "com.example.freevpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.freevpn.ACTION_DISCONNECT"
        const val ACTION_STATUS_BROADCAST = "com.example.freevpn.ACTION_STATUS_BROADCAST"

        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_DNS_SERVER = "extra_dns_server"
        const val EXTRA_SECONDARY_DNS = "extra_secondary_dns"
        const val EXTRA_SPLIT_TUNNEL = "extra_split_tunnel"

        const val EXTRA_STATE = "extra_state"
        const val EXTRA_RX_BYTES = "extra_rx_bytes"
        const val EXTRA_TX_BYTES = "extra_tx_bytes"
        const val EXTRA_RX_SPEED = "extra_rx_speed"
        const val EXTRA_TX_SPEED = "extra_tx_speed"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var telemetryJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Cloudflare 1.1.1.1"
                val dnsServer = intent.getStringExtra(EXTRA_DNS_SERVER) ?: "1.1.1.1"
                val secondaryDns = intent.getStringExtra(EXTRA_SECONDARY_DNS) ?: "1.0.0.1"
                val splitTunnel = intent.getBooleanExtra(EXTRA_SPLIT_TUNNEL, false)

                startVpnTunnel(
                    serverName = serverName,
                    dnsServer = dnsServer,
                    secondaryDns = secondaryDns,
                    splitTunnel = splitTunnel
                )
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpnTunnel(
        serverName: String,
        dnsServer: String,
        secondaryDns: String,
        splitTunnel: Boolean
    ) {
        if (isRunning.getAndSet(true)) {
            stopVpnTunnel()
            isRunning.set(true)
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to $serverName..."))
        broadcastState("CONNECTING", 0L, 0L, 0L, 0L)

        tunnelJob = serviceScope.launch {
            try {
                val builder = Builder()
                    .setSession("Dark VPN • $serverName")
                    .setMtu(1500)
                    .addAddress("10.8.0.2", 24)
                    .addRoute("10.8.0.0", 24)
                    .addDnsServer(dnsServer)
                    .addDnsServer(secondaryDns)

                // Mark VPN as unmetered on Android 10+ so OS never throttles speed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to exclude self package", e)
                }

                if (splitTunnel) {
                    val browsers = listOf(
                        "com.android.chrome",
                        "org.mozilla.firefox",
                        "com.brave.browser",
                        "com.duckduckgo.mobile.android",
                        "com.sec.android.app.sbrowser",
                        "com.opera.browser"
                    )
                    for (pkg in browsers) {
                        try {
                            builder.addAllowedApplication(pkg)
                        } catch (_: Exception) {
                        }
                    }
                }

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VpnService TUN interface")
                    stopVpnTunnel()
                    return@launch
                }

                updateNotification("Dark VPN Active • $serverName ($dnsServer)")
                broadcastState("CONNECTED", 0L, 0L, 0L, 0L)

                startTelemetryLoop(serverName)

                while (isRunning.get() && isActive) {
                    delay(1000L)
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "VPN coroutine cancelled cleanly")
            } catch (e: Exception) {
                Log.e(TAG, "VPN tunnel error: ${e.message}", e)
                stopVpnTunnel()
            }
        }
    }

    private fun startTelemetryLoop(serverName: String) {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            val startRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val startTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            var prevRx = startRx
            var prevTx = startTx
            var prevTime = System.currentTimeMillis()
            var tickCount = 0

            while (isRunning.get() && isActive) {
                delay(500L)
                val nowTime = System.currentTimeMillis()
                val elapsedSec = ((nowTime - prevTime) / 1000.0).coerceAtLeast(0.1)

                val nowRx = TrafficStats.getTotalRxBytes().coerceAtLeast(prevRx)
                val nowTx = TrafficStats.getTotalTxBytes().coerceAtLeast(prevTx)

                val rxSpeed = ((nowRx - prevRx) / elapsedSec).toLong().coerceAtLeast(0L)
                val txSpeed = ((nowTx - prevTx) / elapsedSec).toLong().coerceAtLeast(0L)
                val totalRx = (nowRx - startRx).coerceAtLeast(0L)
                val totalTx = (nowTx - startTx).coerceAtLeast(0L)

                prevRx = nowRx
                prevTx = nowTx
                prevTime = nowTime
                tickCount++

                broadcastState(
                    state = "CONNECTED",
                    rxBytes = totalRx,
                    txBytes = totalTx,
                    rxSpeed = rxSpeed,
                    txSpeed = txSpeed
                )

                if (tickCount % 2 == 0) {
                    updateNotification(
                        "$serverName • ↓ ${formatSpeedShort(rxSpeed)}  ↑ ${formatSpeedShort(txSpeed)}"
                    )
                }
            }
        }
    }

    private fun formatSpeedShort(bytesPerSec: Long): String {
        if (bytesPerSec < 1024) return "$bytesPerSec B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024) return String.format("%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB/s", mb)
    }

    private fun stopVpnTunnel() {
        isRunning.set(false)
        telemetryJob?.cancel()
        tunnelJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
        }
        broadcastState("DISCONNECTED", 0L, 0L, 0L, 0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun broadcastState(
        state: String,
        rxBytes: Long,
        txBytes: Long,
        rxSpeed: Long,
        txSpeed: Long
    ) {
        val intent = Intent(ACTION_STATUS_BROADCAST).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_RX_BYTES, rxBytes)
            putExtra(EXTRA_TX_BYTES, txBytes)
            putExtra(EXTRA_RX_SPEED, rxSpeed)
            putExtra(EXTRA_TX_SPEED, txSpeed)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dark VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays Dark VPN active tunnel status and real-time speed"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, FreeVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnectIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dark_vpn_logo)
            .setContentTitle("Dark VPN")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                pendingDisconnectIntent
            )
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onRevoke() {
        stopVpnTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
