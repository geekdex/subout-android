package io.github.geekdex.subout

import android.app.Application
import io.github.geekdex.subout.data.db.AppDatabase
import io.github.geekdex.subout.data.network.SubscriptionFetcher
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.server.ConfigServer

class SuboutApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val fetcher by lazy { SubscriptionFetcher() }
    val subscriptionRepository by lazy {
        SubscriptionRepository(database.subscriptionDao(), database.nodeDao(), fetcher)
    }
    val nodeRepository by lazy {
        NodeRepository(database.nodeDao())
    }
    val configRepository by lazy {
        ConfigRepository(this)
    }
    val configExporter by lazy {
        ConfigExporter(this)
    }
    val configServer by lazy {
        ConfigServer()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SuboutApplication
            private set
    }
}
