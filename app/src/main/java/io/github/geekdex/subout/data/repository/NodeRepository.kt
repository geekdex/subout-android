package io.github.geekdex.subout.data.repository

import io.github.geekdex.subout.data.db.dao.NodeDao
import io.github.geekdex.subout.data.db.entities.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NodeRepository(
    private val nodeDao: NodeDao
) {
    val nodes: Flow<List<Node>> = nodeDao.getAll()

    suspend fun getEnabledNodes(): List<Node> = withContext(Dispatchers.IO) {
        nodeDao.getEnabledNodesSync()
    }

    suspend fun getAllNodesSync(): List<Node> = withContext(Dispatchers.IO) {
        nodeDao.getAllSync()
    }

    suspend fun setNodeEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        nodeDao.updateEnabled(id, enabled)
    }

    suspend fun setAllNodesEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        nodeDao.enableAll(enabled)
    }

    suspend fun updateNodeLatency(id: Long, latency: Int?) = withContext(Dispatchers.IO) {
        nodeDao.updateLatency(id, latency, System.currentTimeMillis())
    }

    suspend fun clearAllLatencies() = withContext(Dispatchers.IO) {
        nodeDao.clearAllLatencies()
    }

    suspend fun deleteNode(node: Node) = withContext(Dispatchers.IO) {
        nodeDao.delete(node)
    }

    suspend fun deleteTimeoutNodes(): Int = withContext(Dispatchers.IO) {
        nodeDao.deleteTimeoutNodes()
    }

    suspend fun deleteNodesByIds(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) 0 else nodeDao.deleteNodesByIds(ids)
    }

    suspend fun deleteDisabledNodes(): Int = withContext(Dispatchers.IO) {
        nodeDao.deleteDisabledNodes()
    }

    suspend fun setNodesEnabled(ids: List<Long>, enabled: Boolean): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) 0 else nodeDao.updateNodesEnabled(ids, enabled)
    }
}
