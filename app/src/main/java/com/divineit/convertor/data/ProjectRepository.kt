package com.divineit.convertor.data

import kotlinx.coroutines.flow.Flow

/** Thin repository over the DAOs. Phase 2 uses projects only; the audit
 *  log API is ready for the Phase 4 correction engine. */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val auditLogDao: AuditLogDao
) {
    fun projects(): Flow<List<ProjectEntity>> = projectDao.getAll()

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.getById(id)

    suspend fun saveProject(project: ProjectEntity): Long =
        projectDao.insert(project)

    suspend fun deleteProject(id: Long) = projectDao.deleteById(id)

    fun auditLog(projectId: Long): Flow<List<AuditLogEntity>> =
        auditLogDao.getForProject(projectId)

    suspend fun addAuditEntry(entry: AuditLogEntity): Long =
        auditLogDao.insert(entry)
}
