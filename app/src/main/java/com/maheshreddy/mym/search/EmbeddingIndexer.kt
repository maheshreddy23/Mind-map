package com.maheshreddy.mym.search

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maheshreddy.mym.core.library.Retrieval
import com.maheshreddy.mym.data.db.EntryDao
import com.maheshreddy.mym.data.db.EntryVectorEntity
import com.maheshreddy.mym.data.db.TopicDao
import com.maheshreddy.mym.data.db.VectorDao
import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps an embedding of every entry for Ask's search by meaning (v2 §7). After each filing or edit a short job
 * embeds the new or changed entries right away (up to [BATCH]); a bigger backlog (a new embedding model, a
 * restored backup) continues in the background only while the phone is charging.
 */
@Singleton
class EmbeddingIndexer @Inject constructor(@ApplicationContext private val context: Context) {
    fun request() = enqueue(charging = false)

    internal fun enqueue(charging: Boolean) {
        val req = OneTimeWorkRequestBuilder<EmbeddingWorker>()
            .apply { if (charging) setConstraints(Constraints.Builder().setRequiresCharging(true).build()) }
            .build()
        // WorkManager is absent only in plain unit/instrumented tests that build the repository by hand.
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req) }
    }

    companion object {
        const val WORK = "embed-entries"
        const val BATCH = 50
    }
}

/** The model key stored with each vector, so switching embedding models re-embeds everything. */
fun ModelStore.embeddingKey(): String? = file(ModelKind.EMBEDDING)?.let { f ->
    val id = activeId(ModelKind.EMBEDDING)
    // Imported files share one slot id, so the file name tells two imports (and their families) apart.
    if (id == null || id.startsWith("import:")) "import:${f.name}:${f.length()}" else id
}

class EmbeddingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun engines(): Engines
        fun models(): ModelStore
        fun entries(): EntryDao
        fun topics(): TopicDao
        fun vectors(): VectorDao
        fun indexer(): EmbeddingIndexer
    }

    override suspend fun doWork(): Result {
        val d = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val key = d.models().embeddingKey() ?: return Result.success()
        return try {
            d.vectors().dropOtherModels(key)
            val charging = isCharging()
            val titles = HashMap<Long, String?>()
            val pending = d.entries().getAll().mapNotNull { e ->
                val topic = e.topicId?.let { id -> titles.getOrPut(id) { d.topics().get(id)?.title } }
                val text = Retrieval.entryText(e.title, e.body, topic)
                val have = d.vectors().get(e.id)
                if (have != null && have.model == key && have.textHash == text.hashCode()) null else e.id to text
            }
            if (pending.isEmpty()) return Result.success()
            val embedder = d.engines().embedder() ?: return Result.success()
            val now = if (charging) pending else pending.take(EmbeddingIndexer.BATCH)
            for ((id, text) in now) {
                if (isStopped) return Result.success()
                val v = embedder.embed(text)
                d.vectors().put(EntryVectorEntity(id, key, v.size, Retrieval.toBytes(v), text.hashCode()))
            }
            if (now.size < pending.size) d.indexer().enqueue(charging = true)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun isCharging(): Boolean {
        val bm = applicationContext.getSystemService(android.os.BatteryManager::class.java)
        return bm?.isCharging == true
    }
}
