package io.github.geekdex.subout.data.repository

import io.github.geekdex.subout.data.db.dao.NodeGroupDao
import io.github.geekdex.subout.data.db.entities.NodeGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NodeGroupRepository(
    private val nodeGroupDao: NodeGroupDao
) {
    val nodeGroups: Flow<List<NodeGroup>> = nodeGroupDao.getAll()

    suspend fun getAllGroupsSync(): List<NodeGroup> = withContext(Dispatchers.IO) {
        nodeGroupDao.getAllSync()
    }

    suspend fun getGroupById(id: String): NodeGroup? = withContext(Dispatchers.IO) {
        nodeGroupDao.getById(id)
    }

    suspend fun insertGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.insert(group)
    }

    suspend fun updateGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.update(group)
    }

    suspend fun deleteGroup(group: NodeGroup) = withContext(Dispatchers.IO) {
        nodeGroupDao.delete(group)
    }

    suspend fun deleteGroupById(id: String) = withContext(Dispatchers.IO) {
        nodeGroupDao.deleteById(id)
    }

    suspend fun setGroupEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        nodeGroupDao.updateEnabled(id, enabled)
    }

    suspend fun removeNodesFromAllGroups(nodeTags: Set<String>) = withContext(Dispatchers.IO) {
        if (nodeTags.isEmpty()) return@withContext
        val allGroups = nodeGroupDao.getAllSync()
        for (group in allGroups) {
            val currentTags = group.nodeTags
            val filtered = currentTags.filter { it !in nodeTags }
            if (filtered.size != currentTags.size) {
                nodeGroupDao.update(group.copyWithNodeTags(filtered))
            }
        }
    }
}
