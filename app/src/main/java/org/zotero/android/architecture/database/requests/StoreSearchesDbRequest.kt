package org.zotero.android.architecture.database.requests

import io.realm.Realm
import io.realm.kotlin.where
import org.zotero.android.api.pojo.sync.ConditionResponse
import org.zotero.android.api.pojo.sync.SearchResponse
import org.zotero.android.architecture.database.DbError
import org.zotero.android.architecture.database.DbResponseRequest
import org.zotero.android.architecture.database.objects.ObjectSyncState
import org.zotero.android.architecture.database.objects.RCondition
import org.zotero.android.architecture.database.objects.RSearch
import org.zotero.android.architecture.database.objects.UpdatableChangeType
import org.zotero.android.sync.LibraryIdentifier
import java.util.Date
import kotlin.reflect.KClass

class StoreSearchesDbRequest(
    val response: List<SearchResponse>,
) : DbResponseRequest<Unit, Unit> {

    override val needsWrite: Boolean
        get() = true

    override fun process(database: Realm, clazz: KClass<Unit>?) {
        for (data in this.response) {
            store(data = data, database)
        }
    }

    private fun store(data: SearchResponse, database: Realm) {
        database.executeTransaction {
            val libraryId = data.library.libraryId ?: throw DbError.primaryKeyUnavailable

            val search: RSearch
            val existing =
                database.where<RSearch>().key(data.key, libraryId = libraryId).findFirst()
            if (existing != null) {
                search = existing
            } else {
                search = RSearch()
                database.copyToRealm(search)
            }

            search.deleted = false
            search.deleteAllChanges(database = database)

            // Update local instance with remote values
            StoreSearchesDbRequest.update(
                search = search, response = data, libraryId = libraryId, database = database
            )
        }
    }

    companion object {

        fun update(
            search: RSearch,
            response: SearchResponse,
            libraryId: LibraryIdentifier,
            database: Realm
        ) {
            search.key = response.key
            search.name = response.data.name
            search.version = response.version
            search.syncState = ObjectSyncState.synced.name
            search.syncRetries = 0
            search.lastSyncDate = Date()
            search.changeType = UpdatableChangeType.sync.name
            search.libraryId = libraryId
            search.trash = response.data.isTrash

            sync(conditions = response.data.conditions, search = search, database = database)
        }

        fun sync(conditions: List<ConditionResponse>, search: RSearch, database: Realm) {
            search.conditions.deleteAllFromRealm()

            for ((index, objectS) in conditions.withIndex()) {
                val condition = RCondition()
                condition.condition = objectS.condition
                condition.operator = objectS.operator
                condition.value = objectS.value
                condition.sortId = index
                search.conditions.add(condition)
            }
        }
    }
}