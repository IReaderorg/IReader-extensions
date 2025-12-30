package ireader.testserver

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.DexFileReader
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import kotlinx.coroutines.*
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Efficiently loads IReader sources by converting APK/DEX files to JAR using dex2jar.
 * 
 * Features:
 * - Parallel APK conversion for faster startup
 * - Persistent cache based on file hash
 * - Lazy loading option for on-demand conversion
 */
class Dex2JarLoader(private val deps: Dependencies) {
    
    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "ireader-source-jars").apply { mkdirs() }
    private val loadedClassLoaders = ConcurrentHashMap<String, URLClassLoader>()
    private val loadedSources = ConcurrentHashMap<Long, CatalogSource>()
    private val apkIndex = ConcurrentHashMap<Long, ApkInfo>()
    
    fun isDex2JarAvailable(): Boolean = true
    
    /**
     * Scans for APKs and builds an index without loading them.
     * Returns list of available source IDs that can be loaded on-demand.
     */
    fun scanAvailableApks(sourcesDir: File = SourceScanner.findSourcesDir()): List<ApkInfo> {
        val apkFiles = findApkFiles(sourcesDir)
        println("   Found ${apkFiles.size} APK file(s)")
        
        apkFiles.forEach { apk ->
            val info = ApkInfo(
                file = apk,
                name = extractSourceName(apk),
                cacheKey = computeCacheKey(apk)
            )
            // Use name as temporary ID until loaded
            apkIndex[info.name.hashCode().toLong()] = info
        }
        
        return apkIndex.values.toList()
    }
    
    /**
     * Loads all sources in parallel for faster startup.
     */
    fun loadAllSourcesParallel(sourcesDir: File = SourceScanner.findSourcesDir()): List<CatalogSource> = runBlocking {
        val apkFiles = findApkFiles(sourcesDir)
        println("   Found ${apkFiles.size} APK file(s)")
        
        if (apkFiles.isEmpty()) return@runBlocking emptyList()
        
        // Check cache status
        val (cached, needsConversion) = apkFiles.partition { apk ->
            val cacheKey = computeCacheKey(apk)
            File(cacheDir, "$cacheKey.jar").exists()
        }
        
        println("   Cache: ${cached.size} cached, ${needsConversion.size} need conversion")
        
        // Convert uncached APKs in parallel (limit concurrency to avoid OOM)
        val dispatcher = Dispatchers.IO.limitedParallelism(4)
        
        if (needsConversion.isNotEmpty()) {
            println("   Converting ${needsConversion.size} APKs in parallel...")
            val converted = java.util.concurrent.atomic.AtomicInteger(0)
            needsConversion.map { apk ->
                async(dispatcher) {
                    try {
                        val cacheKey = computeCacheKey(apk)
                        val jarFile = File(cacheDir, "$cacheKey.jar")
                        if (!jarFile.exists()) {
                            convertApkToJar(apk, jarFile)
                        }
                        val count = converted.incrementAndGet()
                        if (count % 10 == 0 || count == needsConversion.size) {
                            println("   Converted $count/${needsConversion.size} APKs...")
                        }
                    } catch (e: Exception) {
                        println("   ✗ ${apk.nameWithoutExtension}: ${e.message}")
                    }
                }
            }.awaitAll()
            println("   Conversion complete!")
        }
        
        // Load all sources in parallel
        println("   Loading sources...")
        val sources = apkFiles.mapNotNull { apk ->
            async(dispatcher) {
                try {
                    loadSourceFromApk(apk)
                } catch (e: Exception) {
                    println("   ✗ ${apk.nameWithoutExtension}: ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
        
        sources.forEach { source ->
            loadedSources[source.id] = source
            println("   ✓ ${source.name} (${source.lang})")
        }
        
        sources
    }
    
    /**
     * Load a single source by APK file (for lazy loading).
     */
    fun loadSource(apk: File): CatalogSource? {
        return try {
            loadSourceFromApk(apk)?.also { source ->
                loadedSources[source.id] = source
            }
        } catch (e: Exception) {
            println("   ✗ ${apk.nameWithoutExtension}: ${e.message}")
            null
        }
    }
    
    /**
     * Get already loaded source by ID.
     */
    fun getLoadedSource(id: Long): CatalogSource? = loadedSources[id]
    
    private fun findApkFiles(sourcesDir: File): List<File> {
        val apkFiles = mutableListOf<File>()
        sourcesDir.walkTopDown().maxDepth(10).forEach { file ->
            if (file.extension == "apk" && file.parentFile.name == "debug") {
                apkFiles.add(file)
            }
        }
        return apkFiles
    }
    
    private fun extractSourceName(apk: File): String {
        // Extract name from APK filename: ireader-en-freewebnovel-v2.12.apk -> freewebnovel
        val name = apk.nameWithoutExtension
        val parts = name.split("-")
        return if (parts.size >= 3) parts[2] else name
    }
    
    private fun computeCacheKey(apk: File): String {
        // Use file size + last modified as quick hash (faster than MD5)
        return "${apk.nameWithoutExtension}-${apk.length()}-${apk.lastModified()}"
    }
    
    private fun loadSourceFromApk(apk: File): CatalogSource? {
        val cacheKey = computeCacheKey(apk)
        val jarFile = File(cacheDir, "$cacheKey.jar")
        
        if (!jarFile.exists()) {
            if (!convertApkToJar(apk, jarFile)) {
                return null
            }
        }
        
        return loadExtensionFromJar(jarFile, cacheKey)
    }
    
    private fun convertApkToJar(apk: File, outputJar: File): Boolean {
        return try {
            val dexFile = extractDexFromApk(apk) ?: return false
            val dexData = Files.readAllBytes(dexFile.toPath())
            val reader = DexFileReader(dexData)
            
            Dex2jar.from(reader)
                .skipDebug(true)
                .optimizeSynchronized(false)
                .printIR(false)
                .noCode(false)
                .to(outputJar.toPath())
            
            dexFile.delete()
            outputJar.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    private fun extractDexFromApk(apk: File): File? {
        return try {
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("classes.dex") ?: return null
                val tempDex = File.createTempFile("dex_", ".dex")
                zip.getInputStream(entry).use { input ->
                    tempDex.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempDex
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadExtensionFromJar(jarFile: File, cacheKey: String): CatalogSource? {
        val classLoader = loadedClassLoaders.getOrPut(cacheKey) {
            URLClassLoader(arrayOf(jarFile.toURI().toURL()), this::class.java.classLoader)
        }
        
        return try {
            val extensionClass = classLoader.loadClass("tachiyomix.extension.Extension")
            if (!CatalogSource::class.java.isAssignableFrom(extensionClass)) return null
            
            val constructor = try {
                extensionClass.constructors.firstOrNull { 
                    it.parameterCount == 1 && it.parameterTypes[0] == Dependencies::class.java
                }
            } catch (e: VerifyError) { null }
            
            constructor?.newInstance(deps) as? CatalogSource
        } catch (e: ClassNotFoundException) { null }
        catch (e: VerifyError) { null }
        catch (e: NoClassDefFoundError) { null }
        catch (e: Exception) { null }
    }
    
    fun clearCache() {
        loadedClassLoaders.values.forEach { try { it.close() } catch (e: Exception) {} }
        loadedClassLoaders.clear()
        loadedSources.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}

data class ApkInfo(
    val file: File,
    val name: String,
    val cacheKey: String
)
