package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BrowserViewMode
import com.example.data.model.RecentFileEntry
import com.example.data.model.RepoConfig
import com.example.data.model.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gitNoteDataStore: DataStore<Preferences> by preferencesDataStore(name = "gitnote_prefs")

class ConfigStore(private val context: Context) {

    private val moshi = Moshi.Builder().build()
    private val recentListType = Types.newParameterizedType(List::class.java, RecentFileEntry::class.java)
    private val recentListAdapter = moshi.adapter<List<RecentFileEntry>>(recentListType)

    val repoConfigFlow: Flow<RepoConfig?> = context.gitNoteDataStore.data.map { prefs ->
        val owner = prefs[KEY_OWNER].orEmpty()
        val repo = prefs[KEY_REPO].orEmpty()
        val branch = prefs[KEY_BRANCH].orEmpty()
        val encryptedToken = prefs[KEY_TOKEN_ENC].orEmpty()
        if (owner.isBlank() || repo.isBlank() || encryptedToken.isBlank()) {
            null
        } else {
            val token = TokenCrypto.decrypt(encryptedToken)
            if (token.isBlank()) null else RepoConfig(owner, repo, branch.ifBlank { "main" }, token)
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.gitNoteDataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val browserViewModeFlow: Flow<BrowserViewMode> = context.gitNoteDataStore.data.map { prefs ->
        val raw = prefs[KEY_BROWSER_VIEW_MODE]
        BrowserViewMode.entries.firstOrNull { it.name == raw } ?: BrowserViewMode.FOLDER
    }

    val recentFilesFlow: Flow<List<RecentFileEntry>> = context.gitNoteDataStore.data.map { prefs ->
        parseRecentList(prefs[KEY_RECENT_FILES])
    }

    suspend fun getInitialRepoConfig(): RepoConfig? = repoConfigFlow.first()

    suspend fun getInitialRecentFiles(): List<RecentFileEntry> = recentFilesFlow.first()

    suspend fun saveRepoConfig(config: RepoConfig) {
        val current = repoConfigFlow.first()
        val isDifferentRepo = current != null &&
            (current.owner != config.owner || current.repo != config.repo || current.branch != config.branch)
        val encrypted = TokenCrypto.encrypt(config.token)
        context.gitNoteDataStore.edit { prefs ->
            prefs[KEY_OWNER] = config.owner
            prefs[KEY_REPO] = config.repo
            prefs[KEY_BRANCH] = config.branch
            prefs[KEY_TOKEN_ENC] = encrypted
            if (isDifferentRepo) {
                prefs.remove(KEY_RECENT_FILES)
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.gitNoteDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setBrowserViewMode(mode: BrowserViewMode) {
        context.gitNoteDataStore.edit { prefs ->
            prefs[KEY_BROWSER_VIEW_MODE] = mode.name
        }
    }

    suspend fun recordRecentFile(path: String, wasEdited: Boolean) {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return
        context.gitNoteDataStore.edit { prefs ->
            val current = parseRecentList(prefs[KEY_RECENT_FILES])
            val existing = current.firstOrNull { it.path == normalized }
            val updatedEntry = RecentFileEntry(
                path = normalized,
                lastOpenedAt = System.currentTimeMillis(),
                wasEdited = wasEdited || (existing?.wasEdited == true),
                isPinned = existing?.isPinned == true
            )
            val merged = listOf(updatedEntry) + current.filterNot { it.path == normalized }
            val pinned = merged.filter { it.isPinned }
            val unpinned = merged.filterNot { it.isPinned }
                .take((MAX_RECENT_FILES - pinned.size).coerceAtLeast(0))
            val updatedList = (pinned + unpinned).sortedByDescending { it.lastOpenedAt }
            prefs[KEY_RECENT_FILES] = recentListAdapter.toJson(updatedList)
        }
    }

    suspend fun togglePinRecentFile(path: String) {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return
        context.gitNoteDataStore.edit { prefs ->
            val current = parseRecentList(prefs[KEY_RECENT_FILES])
            val updated = current.map { entry ->
                if (entry.path == normalized) entry.copy(isPinned = !entry.isPinned) else entry
            }
            prefs[KEY_RECENT_FILES] = recentListAdapter.toJson(updated)
        }
    }

    suspend fun removeRecentFile(path: String) {
        val normalized = path.trim('/')
        context.gitNoteDataStore.edit { prefs ->
            val current = parseRecentList(prefs[KEY_RECENT_FILES])
            val updated = current.filterNot { it.path == normalized }
            prefs[KEY_RECENT_FILES] = recentListAdapter.toJson(updated)
        }
    }

    suspend fun clearRepoConfig() {
        context.gitNoteDataStore.edit { prefs ->
            prefs.remove(KEY_OWNER)
            prefs.remove(KEY_REPO)
            prefs.remove(KEY_BRANCH)
            prefs.remove(KEY_TOKEN_ENC)
            prefs.remove(KEY_RECENT_FILES)
        }
    }

    private fun parseRecentList(json: String?): List<RecentFileEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { recentListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_RECENT_FILES = 30
        private val KEY_OWNER = stringPreferencesKey("repo_owner")
        private val KEY_REPO = stringPreferencesKey("repo_name")
        private val KEY_BRANCH = stringPreferencesKey("repo_branch")
        private val KEY_TOKEN_ENC = stringPreferencesKey("repo_token_enc")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_BROWSER_VIEW_MODE = stringPreferencesKey("browser_view_mode")
        private val KEY_RECENT_FILES = stringPreferencesKey("recent_files_json")
    }
}
