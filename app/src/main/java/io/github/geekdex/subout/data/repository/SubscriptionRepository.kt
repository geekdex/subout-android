package io.github.geekdex.subout.data.repository

import io.github.geekdex.subout.data.db.dao.NodeDao
import io.github.geekdex.subout.data.db.dao.SubscriptionDao
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.Subscription
import io.github.geekdex.subout.data.network.SubscriptionFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
    private val nodeDao: NodeDao,
    private val fetcher: SubscriptionFetcher
) {
    val subscriptions: Flow<List<Subscription>> = subscriptionDao.getAll()

    suspend fun addSubscription(name: String, url: String): Long = withContext(Dispatchers.IO) {
        val sub = Subscription(
            name = name.ifBlank { "订阅源" },
            url = url.trim()
        )
        subscriptionDao.insert(sub)
    }

    suspend fun updateSubscription(subscription: Subscription) = withContext(Dispatchers.IO) {
        subscriptionDao.update(subscription)
    }

    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) {
        nodeDao.deleteBySubscriptionId(id)
        subscriptionDao.deleteById(id)
    }

    suspend fun setSubscriptionEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        subscriptionDao.updateEnabled(id, enabled)
    }

    suspend fun syncSubscription(id: Long): Result<Int> = withContext(Dispatchers.IO) {
        val sub = subscriptionDao.getById(id) ?: return@withContext Result.failure(Exception("订阅源不存在"))
        val fetchResult = fetcher.fetch(sub.url)

        val now = System.currentTimeMillis()
        if (fetchResult.isSuccess) {
            val (parsedNodes, skipped) = fetchResult.getOrThrow()
            nodeDao.deleteBySubscriptionId(id)

            val dbNodes = parsedNodes.map { proxyNode ->
                Node(
                    subscriptionId = id,
                    tag = proxyNode.tag,
                    protocol = proxyNode.protocol,
                    server = proxyNode.server,
                    serverPort = proxyNode.serverPort,
                    rawJson = proxyNode.rawJson,
                    enabled = true
                )
            }
            nodeDao.insertAll(dbNodes)

            val statusMsg = "同步成功 (解析: ${dbNodes.size}, 跳过公告: ${skipped.size})"
            subscriptionDao.updateStatus(id, statusMsg, now, dbNodes.size)
            Result.success(dbNodes.size)
        } else {
            val errorMsg = "同步失败: ${fetchResult.exceptionOrNull()?.message}"
            subscriptionDao.updateStatus(id, errorMsg, now, sub.nodeCount)
            Result.failure(fetchResult.exceptionOrNull() ?: Exception("未知错误"))
        }
    }

    suspend fun syncAll(): Map<Long, Result<Int>> = withContext(Dispatchers.IO) {
        val allSubs = subscriptionDao.getAllSync()
        val results = mutableMapOf<Long, Result<Int>>()
        for (sub in allSubs) {
            if (sub.enabled) {
                results[sub.id] = syncSubscription(sub.id)
            }
        }
        results
    }
}
