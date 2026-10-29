package com.karelisio.canif.data

import com.karelisio.canif.CanifApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Favorites and recents of the home screen. */
object ToolRepository {
    private val app get() = CanifApp.instance
    private val db get() = app.db

    /** Called after favorites change (App Shortcuts, widget refresh…). */
    var onFavoritesChanged: (suspend (List<String>) -> Unit)? = null

    val favorites: Flow<List<String>> = db.favorites().observe().map { list -> list.map { it.toolId } }
    val recents: Flow<List<String>> = db.recents().observe(12).map { list -> list.map { it.toolId } }

    fun recordUse(id: String) = app.appScope.launch {
        db.recents().touch(RecentEntity(id, System.currentTimeMillis()))
        db.recents().trim()
    }

    fun toggleFavorite(id: String) = app.appScope.launch {
        val dao = db.favorites()
        if (dao.all().any { it.toolId == id }) dao.delete(id)
        else dao.insert(FavoriteEntity(id, dao.nextPosition()))
        notifyFavorites()
    }

    fun reorderFavorites(ids: List<String>) = app.appScope.launch {
        db.favorites().reorder(ids)
        notifyFavorites()
    }

    private suspend fun notifyFavorites() {
        onFavoritesChanged?.invoke(db.favorites().all().map { it.toolId })
    }
}
