package com.example

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.AppRepository
import com.example.worker.WorkerRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The worker-chain wiring (v3.0 D-01, OQ-1): the desktop edition ships the
 * autonomous pipeline as a RUNNING feature.
 *
 *  1. [WorkerRegistry.register] registers a factory for EVERY worker class
 *     the app enqueues (the registry's own WORKER_CLASSES is the
 *     grep-verified call-site inventory);
 *  2. the shim engine actually EXECUTES chains through the registered
 *     factories — in order, output-forward, halted by failure — the exact
 *     path StudioViewModel's Autonomous-drafting enqueue drives.
 *
 * Room-free: the DAOs are JDK proxies (the factories capture them, nothing
 * invokes them); the chain workers are test-local.
 */
class WorkerWiringTest {

    /** The desktop Context shim's four abstract members, rooted at a temp dir. */
    private class FakeContext(root: File) : Context() {
        override val filesDir: File = File(root, "files").apply { mkdirs() }
        override val cacheDir: File = File(root, "cache").apply { mkdirs() }
        override val externalCacheDir: File? = File(root, "external-cache").apply { mkdirs() }
        override val noBackupFilesDir: File = File(root, "no-backup").apply { mkdirs() }
    }

    @Test
    fun `register covers every worker class the app enqueues`() {
        val root = kotlin.io.path.createTempDirectory("worker-wiring").toFile()
        try {
            val context = FakeContext(root)
            val repository = AppRepository(
                storyDao = proxy(),
                audioTrackDao = proxy(),
                bookDao = proxy(),
                chapterDao = proxy(),
                lorebookDao = proxy(),
                chekhovGunDao = proxy(),
                questionDao = proxy(),
                serialArcDao = proxy()
            )
            WorkerRegistry.register(
                appContext = context,
                bookDao = proxy(),
                chapterDao = proxy(),
                lorebookDao = proxy(),
                chekhovGunDao = proxy(),
                questionDao = proxy(),
                serialArcDao = proxy(),
                repository = repository
            )
            assertTrue(WorkerRegistry.registered, "the registry flags a completed registration")
            // The public probe is the proof: every enqueued class resolves.
            val registered = WorkManager.registeredWorkerClasses()
            WorkerRegistry.WORKER_CLASSES.forEach { clazz ->
                assertTrue(
                    registered.contains(clazz.kotlin),
                    "no factory registered for ${clazz.name} — enqueues of it would log 'No factory' and stop"
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    /** Chain workers: record executions, forward output, fail on demand. */
    private class StepWorker(
        context: Context,
        params: WorkerParameters,
        private val fail: Boolean,
        private val log: MutableList<String>
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            log += inputData.getString("step")!!
            if (fail) return Result.failure()
            return Result.success(
                Data.Builder().putString("step", inputData.getString("step") + "→next").build()
            )
        }
    }

    @Test
    fun `the shim executes a registered chain in order and halts on failure`() {
        val root = kotlin.io.path.createTempDirectory("worker-chain").toFile()
        try {
            val context = FakeContext(root)
            val log = java.util.concurrent.CopyOnWriteArrayList<String>()
            androidx.work.WorkManager.registerWorker(StepWorker::class) { p ->
                StepWorker(context, p, fail = p.inputData.getBoolean("fail", false), log = log)
            }

            val first = OneTimeWorkRequestBuilder<StepWorker>()
                .setInputData(Data.Builder().putString("step", "first").build())
                .build()
            val second = OneTimeWorkRequestBuilder<StepWorker>()
                .setInputData(Data.Builder().putString("step", "second").build())
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("wiring-test-chain", ExistingWorkPolicy.REPLACE, first)
                .then(second)
                .enqueue()

            kotlinx.coroutines.runBlocking {
                withTimeout(20_000) {
                    while (log.size < 2) kotlinx.coroutines.delay(50)
                }
            }
            assertEquals(listOf("first", "second"), log.toList(), "the chain runs in order")

            // failure halts the chain: the third step never runs
            val failing = OneTimeWorkRequestBuilder<StepWorker>()
                .setInputData(
                    Data.Builder().putString("step", "failing").putBoolean("fail", true).build()
                )
                .build()
            val never = OneTimeWorkRequestBuilder<StepWorker>()
                .setInputData(Data.Builder().putString("step", "never").build())
                .build()
            WorkManager.getInstance(context)
                .beginUniqueWork("wiring-test-fail", ExistingWorkPolicy.REPLACE, failing)
                .then(never)
                .enqueue()
            kotlinx.coroutines.runBlocking {
                withTimeout(20_000) {
                    while (!log.contains("failing")) kotlinx.coroutines.delay(50)
                }
                kotlinx.coroutines.delay(500)
            }
            assertTrue(!log.contains("never"), "a failed step halts the chain: $log")
        } finally {
            root.deleteRecursively()
        }
    }

    /** A JDK proxy standing in for a Room DAO interface — captured, never invoked. */
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> proxy(): T =
        java.lang.reflect.Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
}
