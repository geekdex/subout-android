package io.github.geekdex.subout.domain.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.geekdex.subout.MainActivity
import io.github.geekdex.subout.R
import io.github.geekdex.subout.SuboutApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 维持配置服务前台运行的服务，防止用户切换到 SFA 导入或手动同步时后台进程被系统挂起冻结导致请求超时。
 */
class ConfigServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val server = SuboutApplication.instance.configServer

        when (action) {
            ACTION_STOP -> {
                server.stop()
                _isRunningState.value = false
                _serverUrlState.value = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8888)
                val config = intent.getStringExtra(EXTRA_CONFIG) ?: server.configContent
                val result = server.start(config, port)
                if (result.isSuccess) {
                    val url = result.getOrThrow()
                    _isRunningState.value = true
                    _serverUrlState.value = url
                    val notification = buildNotification(url)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    _isRunningState.value = false
                    _serverUrlState.value = ""
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val server = SuboutApplication.instance.configServer
        server.stop()
        _isRunningState.value = false
        _serverUrlState.value = ""
        super.onDestroy()
    }

    private fun buildNotification(url: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ConfigServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Subout 本地配置服务正在运行")
            .setContentText("服务地址: $url (点击返回应用)")
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "本地配置服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台配置服务运行，避免切换至 SFA 时连接超时或挂起"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "subout_config_server"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "io.github.geekdex.subout.action.START_CONFIG_SERVER"
        const val ACTION_STOP = "io.github.geekdex.subout.action.STOP_CONFIG_SERVER"
        const val EXTRA_PORT = "port"
        const val EXTRA_CONFIG = "config"

        private val _isRunningState = MutableStateFlow(false)
        val isRunningState: StateFlow<Boolean> = _isRunningState.asStateFlow()

        private val _serverUrlState = MutableStateFlow("")
        val serverUrlState: StateFlow<String> = _serverUrlState.asStateFlow()

        fun start(context: Context, port: Int = 8888, config: String) {
            val intent = Intent(context, ConfigServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ConfigServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
