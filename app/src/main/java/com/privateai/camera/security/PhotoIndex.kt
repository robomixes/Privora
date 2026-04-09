package com.privateai.camera.security

import android.content.ContentValues
import android.graphics.Bitmap
import android.util.Log
import com.privateai.camera.bridge.FaceEmbedder
import com.privateai.camera.bridge.ImageClassifier
import com.privateai.camera.bridge.OnnxDetector
import com.privateai.camera.util.BlurDetector
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray

/**
 * Manages an encrypted index that maps vault photo IDs to their AI-generated
 * labels, feature vectors, and blur scores. Backed by SQLCipher via PrivoraDatabase.
 */
class PhotoIndex(private val database: PrivoraDatabase) {

    companion object {
        private const val TAG = "PhotoIndex"
    }

    private val db: SQLiteDatabase get() = database.db

    /**
     * Named face identities -- each has a stable ID, a user-given name, and a centroid embedding.
     */
    data class FaceIdentity(
        val id: String,           // stable UUID
        val name: String,         // display name (can be renamed freely)
        val centroid: FloatArray,  // average embedding of all faces in this group
        val personId: String? = null  // linked PrivateContact.id (survives renames)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceIdentity) return false
            return id == other.id && name == other.name && personId == other.personId && centroid.contentEquals(other.centroid)
        }
        override fun hashCode(): Int = id.hashCode()
    }

    data class FaceEntry(
        val box: FloatArray,      // [x, y, w, h] normalized 0-1
        val embedding: FloatArray // 128-dim
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceEntry) return false
            return box.contentEquals(other.box) &&
                    embedding.contentEquals(other.embedding)
        }

        override fun hashCode(): Int {
            var result = box.contentHashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }

    data class PhotoIndexEntry(
        val labels: List<String>,        // top-5 ImageNet labels
        val scores: List<Float>,         // confidence scores for labels
        val featureVector: FloatArray?,   // 1000-dim for similarity
        val blurScore: Double,
        val faces: List<FaceEntry> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PhotoIndexEntry) return false
            return labels == other.labels &&
                    scores == other.scores &&
                    featureVector.contentEquals(other.featureVector) &&
                    blurScore == other.blurScore &&
                    faces == other.faces
        }

        override fun hashCode(): Int {
            var result = labels.hashCode()
            result = 31 * result + scores.hashCode()
            result = 31 * result + (featureVector?.contentHashCode() ?: 0)
            result = 31 * result + blurScore.hashCode()
            result = 31 * result + faces.hashCode()
            return result
        }
    }

    /**
     * Check if a photo is already indexed.
     */
    fun isIndexed(photoId: String): Boolean {
        db.rawQuery("SELECT 1 FROM photo_index WHERE photo_id = ?", arrayOf(photoId)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Remove deleted photos from the index.
     */
    fun removeEntries(photoIds: Set<String>) {
        if (photoIds.isEmpty()) return
        db.beginTransaction()
        try {
            for (id in photoIds) {
                db.delete("photo_index", "photo_id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Index a single photo. Returns the entry.
     */
    fun indexPhoto(
        photoId: String,
        bitmap: Bitmap,
        classifier: ImageClassifier,
        blurDetector: BlurDetector = BlurDetector,
        faceEmbedder: FaceEmbedder? = null,
        detector: OnnxDetector? = null
    ): PhotoIndexEntry {
        if (bitmap.isRecycled) return PhotoIndexEntry(emptyList(), emptyList(), null, -1.0, emptyList())

        // 1. Detect objects with YOLOv8n (COCO 80 — optional, skipped in batch mode)
        val detections = if (detector != null && !bitmap.isRecycled) {
            try { detector.detect(bitmap, minConfidence = 0.45f) }
            catch (_: Throwable) { emptyList() }
        } else emptyList()

        // 2. Classify + get embedding in ONE inference (fused — saves ~150ms per photo)
        val (classifications, featureVector) = if (!bitmap.isRecycled) {
            classifier.classifyWithEmbedding(bitmap)
        } else emptyList<Pair<String, Float>>() to FloatArray(0)

        // 3. Merge: detector labels first, then classifier
        val merged = mutableListOf<Pair<String, Float>>()
        val seen = mutableSetOf<String>()
        for (det in detections.distinctBy { it.className }) {
            val label = det.className.replaceFirstChar { it.uppercase() }
            if (label.lowercase() !in seen) { seen.add(label.lowercase()); merged.add(label to det.confidence) }
        }
        for ((label, score) in classifications) {
            if (label.lowercase() !in seen) { seen.add(label.lowercase()); merged.add(label to score) }
        }
        val labels = merged.take(8).map { it.first }
        val scores = merged.take(8).map { it.second }

        // 4. Blur score
        val blurScore = if (!bitmap.isRecycled) blurDetector.getBlurScore(bitmap) else -1.0

        // 5. Detect faces and compute embeddings
        val faces = if (!bitmap.isRecycled) {
            faceEmbedder?.detectAndEmbed(bitmap)?.map { (box, emb) ->
                FaceEntry(floatArrayOf(box.left, box.top, box.width(), box.height()), emb)
            } ?: emptyList()
        } else emptyList()

        // 5b. Create face identities for new faces that don't match any existing identity
        if (faces.isNotEmpty()) {
            val existingIdentities = loadFaceIdentitiesList()
            db.beginTransaction()
            try {
                for (face in faces) {
                    val matchesExisting = existingIdentities.any { identity ->
                        cosineSimilarity(face.embedding, identity.centroid) > 0.70f
                    }
                    if (!matchesExisting) {
                        val newId = java.util.UUID.randomUUID().toString()
                        val cv = ContentValues().apply {
                            put("id", newId)
                            put("name", "")
                            put("centroid", face.embedding.copyOf().toBlob())
                        }
                        db.insertWithOnConflict("face_identities", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        // 6. Store in database
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("photo_id", photoId)
                put("labels", JSONArray(labels).toString())
                put("scores", JSONArray(scores.map { it.toDouble() }).toString())
                put("feature_vector", featureVector?.toBlob())
                put("blur_score", blurScore)
                put("indexed_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("photo_index", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            // Delete old face entries for this photo then insert new ones
            db.delete("face_entries", "photo_id = ?", arrayOf(photoId))
            faces.forEachIndexed { i, face ->
                val faceCv = ContentValues().apply {
                    put("photo_id", photoId)
                    put("face_index", i)
                    put("box", face.box.toBlob())
                    put("embedding", face.embedding.toBlob())
                }
                db.insert("face_entries", null, faceCv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val entry = PhotoIndexEntry(labels, scores, featureVector, blurScore, faces)
        Log.d(TAG, "Indexed photo $photoId: ${labels.take(3)}, blur=$blurScore, faces=${faces.size}")
        return entry
    }

    /** Map common search terms to ImageNet labels that don't match intuitively. */
    private val searchAliases = mapOf(
            "person" to listOf("jean", "suit", "jersey", "sweatshirt", "T-shirt", "bikini", "miniskirt", "bow_tie", "wig", "sunglass", "sunglasses", "mask", "lipstick", "hair_spray"),
            "people" to listOf("jean", "suit", "jersey", "sweatshirt", "T-shirt", "bikini", "miniskirt", "bow_tie", "wig", "sunglass", "sunglasses"),
            "selfie" to listOf("sunglass", "sunglasses", "wig", "lipstick", "hair_spray", "mask", "jean", "T-shirt", "jersey"),
            "face" to listOf("wig", "sunglass", "sunglasses", "lipstick", "mask", "hair_spray"),
            "food" to listOf("pizza", "cheeseburger", "hotdog", "ice_cream", "burrito", "plate", "restaurant", "menu", "cup", "bowl", "tray", "bakery", "meat_loaf", "potpie"),
            "animal" to listOf("dog", "cat", "bird", "fish", "horse", "cow", "sheep", "elephant", "bear", "zebra", "giraffe", "tiger", "lion", "rabbit", "hamster", "monkey"),
            "car" to listOf("car_wheel", "sports_car", "convertible", "limousine", "minivan", "cab", "jeep", "ambulance", "pickup", "racer", "beach_wagon"),
            "flower" to listOf("daisy", "rose", "sunflower", "tulip", "pot", "vase", "bouquet"),
            "building" to listOf("church", "castle", "palace", "mosque", "monastery", "library", "cinema", "barn", "greenhouse", "dome"),
            "beach" to listOf("seashore", "sandbar", "lakeside", "pier", "bikini", "snorkel", "surfboard"),
            "nature" to listOf("valley", "mountain", "volcano", "cliff", "lakeside", "seashore", "coral_reef", "alp"),
            "sky" to listOf("cloud", "balloon", "parachute", "kite"),
            "tree" to listOf("oak", "palm", "fig", "bonsai", "larch"),
            "baby" to listOf("bib", "diaper", "crib", "cradle", "bassinet"),
            "sport" to listOf("soccer_ball", "basketball", "tennis_ball", "volleyball", "baseball", "golf_ball", "rugby_ball", "ping-pong_ball"),
            "phone" to listOf("cell_phone", "cellular_telephone", "smartphone", "iPod", "dial_telephone"),
            "laptop" to listOf("laptop", "notebook", "desktop_computer", "screen", "monitor"),
            "book" to listOf("book_jacket", "comic_book", "notebook"),
            "cat" to listOf("tabby", "Persian_cat", "Siamese_cat", "Egyptian_cat", "tiger_cat"),
            "dog" to listOf("golden_retriever", "Labrador_retriever", "German_shepherd", "poodle", "beagle", "husky", "bulldog", "pug", "Chihuahua", "collie", "dalmatian"),
    )

    /**
     * Search by label text. Returns matching photo IDs sorted by relevance.
     * Supports aliases: "person" -> matches jean, suit, T-shirt, etc.
     */
    fun searchByLabel(query: String): List<String> {
        val queryWords = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return emptyList()

        // Expand query with aliases
        val expandedWords = mutableSetOf<String>()
        for (word in queryWords) {
            expandedWords.add(word)
            searchAliases[word]?.forEach { expandedWords.add(it.lowercase()) }
        }

        val results = mutableListOf<Pair<String, Float>>()

        // Search by face group names
        val faceMatchPhotoIds = mutableSetOf<String>()
        val identities = loadFaceIdentitiesList()
        val namedIdentityIds = identities.filter { identity ->
            if (identity.name.isNotBlank() && queryWords.any { q -> identity.name.lowercase().contains(q) }) return@filter true
            false
        }.map { it.id }.toMutableSet()
        identities.forEach { identity ->
            if (identity.personId != null && queryWords.any { q -> q == identity.personId }) {
                namedIdentityIds.add(identity.id)
            }
        }
        if (namedIdentityIds.isNotEmpty()) {
            val groups = getFaceGroups()
            for (identityId in namedIdentityIds) {
                groups[identityId]?.forEach { (photoId, _, _) ->
                    faceMatchPhotoIds.add(photoId)
                }
            }
        }
        faceMatchPhotoIds.forEach { results.add(Pair(it, 2.0f)) }

        // "person"/"people"/"face"/"selfie" -> return ALL photos that have any detected face
        val faceKeywords = setOf("person", "people", "face", "selfie", "faces")
        if (queryWords.any { it in faceKeywords }) {
            // Find all photo_ids that have face_entries
            db.rawQuery(
                "SELECT DISTINCT photo_id FROM face_entries",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val photoId = cursor.getString(0)
                    if (photoId !in faceMatchPhotoIds) {
                        results.add(Pair(photoId, 1.5f))
                        faceMatchPhotoIds.add(photoId) // prevent dups in label search below
                    }
                }
            }
        }

        // Search labels with LIKE for each expanded word
        for (word in expandedWords) {
            val likePattern = "%$word%"
            db.rawQuery(
                "SELECT photo_id, labels, scores FROM photo_index WHERE labels LIKE ?",
                arrayOf(likePattern)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val photoId = cursor.getString(0)
                    if (photoId in faceMatchPhotoIds) continue
                    val labelsJson = cursor.getString(1)
                    val scoresJson = cursor.getString(2)
                    val labelsList = parseJsonStringArray(labelsJson)
                    val scoresList = parseJsonFloatArray(scoresJson)

                    var totalScore = 0f
                    for ((labelIndex, label) in labelsList.withIndex()) {
                        val labelLower = label.lowercase().replace(" ", "_")
                        for (w in expandedWords) {
                            if (labelLower.contains(w) || w.contains(labelLower)) {
                                totalScore += if (labelIndex < scoresList.size) scoresList[labelIndex] else 0.1f
                            }
                        }
                    }
                    if (totalScore > 0f) {
                        results.add(Pair(photoId, totalScore))
                    }
                }
            }
        }

        // Deduplicate: keep highest score per photoId
        val bestScores = mutableMapOf<String, Float>()
        for ((id, score) in results) {
            bestScores[id] = maxOf(bestScores[id] ?: 0f, score)
        }

        return bestScores.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * Find photos similar to the given photo.
     * If the photo has faces, prioritize face similarity (find same person).
     * Otherwise fall back to general image similarity.
     */
    fun findSimilar(photoId: String, topN: Int = 20): List<Pair<String, Float>> {
        // Load target photo's faces
        val targetFaces = loadFaceEntriesForPhoto(photoId)

        if (targetFaces.isNotEmpty()) {
            val faceResults = mutableMapOf<String, Float>()
            // Load all face entries from other photos
            db.rawQuery(
                "SELECT photo_id, embedding FROM face_entries WHERE photo_id != ?",
                arrayOf(photoId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val otherId = cursor.getString(0)
                    val otherEmb = cursor.getBlob(1).toFloatArray()
                    for (targetFace in targetFaces) {
                        val sim = cosineSimilarity(targetFace.embedding, otherEmb)
                        if (sim > 0.5f) {
                            faceResults[otherId] = maxOf(faceResults[otherId] ?: 0f, sim)
                        }
                    }
                }
            }
            if (faceResults.isNotEmpty()) {
                return faceResults.entries.sortedByDescending { it.value }
                    .take(topN).map { it.key to it.value }
            }
        }

        // Fall back to general image feature similarity
        val targetVector = loadFeatureVector(photoId) ?: return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        db.rawQuery(
            "SELECT photo_id, feature_vector FROM photo_index WHERE photo_id != ? AND feature_vector IS NOT NULL",
            arrayOf(photoId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val otherId = cursor.getString(0)
                val otherVector = cursor.getBlob(1).toFloatArray()
                val similarity = cosineSimilarity(targetVector, otherVector)
                if (similarity > 0.3f) {
                    results.add(Pair(otherId, similarity))
                }
            }
        }
        return results.sortedByDescending { it.second }.take(topN)
    }

    /**
     * Find groups of duplicate/near-identical photos.
     */
    fun findDuplicates(threshold: Float = 0.90f, validPhotoIds: Set<String>? = null): List<List<String>> {
        // Load all photo IDs and feature vectors (skip trashed/deleted photos)
        val entries = mutableListOf<Pair<String, FloatArray>>()
        db.rawQuery(
            "SELECT photo_id, feature_vector FROM photo_index WHERE feature_vector IS NOT NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (validPhotoIds != null && id !in validPhotoIds) continue
                val fv = cursor.getBlob(1).toFloatArray()
                if (fv.isEmpty()) continue
                entries.add(id to fv)
            }
        }

        val photoIds = entries.map { it.first }
        val parent = mutableMapOf<String, String>()

        // Union-Find helpers
        fun find(x: String): String {
            var root = x
            while (parent[root] != root) {
                parent[root] = parent[parent[root]!!]!!
                root = parent[root]!!
            }
            return root
        }

        fun union(x: String, y: String) {
            val rootX = find(x)
            val rootY = find(y)
            if (rootX != rootY) {
                parent[rootX] = rootY
            }
        }

        for (id in photoIds) {
            parent[id] = id
        }

        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val similarity = cosineSimilarity(entries[i].second, entries[j].second)
                if (similarity >= threshold) {
                    union(entries[i].first, entries[j].first)
                }
            }
        }

        val groups = mutableMapOf<String, MutableList<String>>()
        for (id in photoIds) {
            val root = find(id)
            groups.getOrPut(root) { mutableListOf() }.add(id)
        }

        return groups.values.filter { it.size >= 2 }
    }

    /**
     * Find blurry photos. Returns photo IDs with blur score below threshold.
     */
    fun findBlurry(threshold: Double = 100.0, validPhotoIds: Set<String>? = null): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM photo_index WHERE blur_score < ? AND blur_score >= 0",
            arrayOf(threshold.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (validPhotoIds == null || id in validPhotoIds) results.add(id)
            }
        }
        return results
    }

    /**
     * Get index progress: number of indexed photos.
     */
    fun getIndexedCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM photo_index", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get labels for a specific photo.
     */
    fun getLabels(photoId: String): List<String> {
        db.rawQuery(
            "SELECT labels FROM photo_index WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseJsonStringArray(cursor.getString(0))
            }
        }
        return emptyList()
    }

    fun getLabelsWithScores(photoId: String): List<Pair<String, Float>> {
        db.rawQuery(
            "SELECT labels, scores FROM photo_index WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val labels = parseJsonStringArray(cursor.getString(0))
                val scoresJson = cursor.getString(1)
                val scores = try {
                    val arr = JSONArray(scoresJson)
                    (0 until arr.length()).map { arr.getDouble(it).toFloat() }
                } catch (_: Exception) { emptyList() }
                return labels.zip(scores)
            }
        }
        return emptyList()
    }

    /**
     * Get all unique labels across all indexed photos (for auto-suggest).
     */
    fun getAllLabels(): List<String> {
        val allLabels = mutableSetOf<String>()
        db.rawQuery("SELECT DISTINCT labels FROM photo_index", null).use { cursor ->
            while (cursor.moveToNext()) {
                val labelsJson = cursor.getString(0)
                parseJsonStringArray(labelsJson).forEach { allLabels.add(it) }
            }
        }
        return allLabels.sorted()
    }

    /**
     * Clear the entire index.
     */
    fun clearIndex() {
        db.beginTransaction()
        try {
            db.delete("photo_index", null, null) // cascades to face_entries
            db.setTransactionSuccessful()
            Log.d(TAG, "Index cleared")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Group faces across all photos by similarity, using stable identities.
     * Returns map of identity ID -> list of (photoId, faceIndex, FaceEntry).
     * Photos with multiple faces appear in multiple groups.
     */
    fun getFaceGroups(threshold: Float = 0.78f): Map<String, List<Triple<String, Int, FaceEntry>>> {
        val identities = loadFaceIdentitiesList().toMutableList()

        // Load all face entries from DB
        val allFaces = mutableListOf<Triple<String, Int, FaceEntry>>()
        db.rawQuery(
            "SELECT photo_id, face_index, box, embedding FROM face_entries",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val photoId = cursor.getString(0)
                val faceIndex = cursor.getInt(1)
                val box = cursor.getBlob(2).toFloatArray()
                val emb = cursor.getBlob(3).toFloatArray()
                allFaces.add(Triple(photoId, faceIndex, FaceEntry(box, emb)))
            }
        }
        if (allFaces.isEmpty()) return emptyMap()

        // Match each face to an existing identity or create a new one
        val groups = mutableMapOf<String, MutableList<Triple<String, Int, FaceEntry>>>()
        val updatedIdentities = identities.toMutableList()

        for (face in allFaces) {
            var bestId: String? = null
            var bestSim = threshold

            for (identity in updatedIdentities) {
                val sim = cosineSimilarity(face.third.embedding, identity.centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestId = identity.id
                }
            }

            if (bestId != null) {
                groups.getOrPut(bestId) { mutableListOf() }.add(face)
                // Update centroid (running average)
                val members = groups[bestId]!!
                val idx = updatedIdentities.indexOfFirst { it.id == bestId }
                if (idx >= 0) {
                    val newCentroid = FloatArray(updatedIdentities[idx].centroid.size)
                    for (m in members) for (i in newCentroid.indices) newCentroid[i] += m.third.embedding[i]
                    for (i in newCentroid.indices) newCentroid[i] /= members.size
                    updatedIdentities[idx] = updatedIdentities[idx].copy(centroid = newCentroid)
                }
            } else {
                // No match at strict threshold -- try softer match
                var softId: String? = null
                var softSim = threshold * 0.7f
                for (identity in updatedIdentities) {
                    val sim = cosineSimilarity(face.third.embedding, identity.centroid)
                    if (sim > softSim) { softSim = sim; softId = identity.id }
                }
                if (softId != null) {
                    groups.getOrPut(softId) { mutableListOf() }.add(face)
                }
            }
        }

        // Update centroids of existing identities in DB
        val existingIds = identities.map { it.id }.toSet()
        db.beginTransaction()
        try {
            for (updated in updatedIdentities) {
                if (updated.id in existingIds) {
                    val original = identities.find { it.id == updated.id }
                    if (original != null && !original.centroid.contentEquals(updated.centroid)) {
                        val cv = ContentValues().apply {
                            put("centroid", updated.centroid.toBlob())
                        }
                        db.update("face_identities", cv, "id = ?", arrayOf(updated.id))
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Filter out excluded photos and return groups with 2+ faces
        val excluded = loadAllExclusions()
        val filtered = groups.mapValues { (id, members) ->
            val excludedSet = excluded[id] ?: emptySet()
            members.filter { it.first !in excludedSet }
        }
        // Sort by group size descending (largest groups first)
        return filtered.filter { it.value.size >= 2 }
            .toSortedMap(compareByDescending { filtered[it]?.size ?: 0 })
    }

    /**
     * Merge two face groups into one. The kept group gets the combined centroid
     * and the name from whichever group had one. Future indexing will use the
     * broader centroid to match both appearances.
     */
    fun mergeFaceGroups(keepId: String, mergeIntoId: String) {
        val identities = loadFaceIdentitiesList()
        val keep = identities.find { it.id == keepId } ?: return
        val merge = identities.find { it.id == mergeIntoId } ?: return

        // Count how many faces belong to each group by comparing cosine similarity
        var keepCount = 0
        var mergeCount = 0
        db.rawQuery("SELECT embedding FROM face_entries", null).use { cursor ->
            while (cursor.moveToNext()) {
                val emb = cursor.getBlob(0).toFloatArray()
                val simKeep = cosineSimilarity(emb, keep.centroid)
                val simMerge = cosineSimilarity(emb, merge.centroid)
                if (simKeep > simMerge) keepCount++ else mergeCount++
            }
        }
        keepCount = keepCount.coerceAtLeast(1)
        mergeCount = mergeCount.coerceAtLeast(1)
        val total = keepCount + mergeCount

        // Weighted average centroid
        val newCentroid = FloatArray(keep.centroid.size) { i ->
            (keep.centroid[i] * keepCount + merge.centroid[i] * mergeCount) / total
        }

        // Keep name from whichever has one
        val name = keep.name.ifBlank { merge.name }

        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("name", name)
                put("centroid", newCentroid.toBlob())
            }
            db.update("face_identities", cv, "id = ?", arrayOf(keepId))
            db.delete("face_identities", "id = ?", arrayOf(mergeIntoId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Log.d(TAG, "Merged face group '$mergeIntoId' into '$keepId' (name='$name', keep=$keepCount, merge=$mergeCount)")
    }

    /**
     * Remove specific photos from a face group.
     */
    fun removeFromFaceGroup(identityId: String, photoIds: Set<String>) {
        db.beginTransaction()
        try {
            for (photoId in photoIds) {
                val cv = ContentValues().apply {
                    put("identity_id", identityId)
                    put("photo_id", photoId)
                }
                db.insertWithOnConflict("face_exclusions", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getExcludedFromGroup(identityId: String): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM face_exclusions WHERE identity_id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
        return result
    }

    /**
     * Auto-name face groups by matching against People profile photos.
     */
    fun autoNameFromContacts(
        contactRepo: ContactRepository,
        faceEmbedder: com.privateai.camera.bridge.FaceEmbedder,
        threshold: Float = 0.70f
    ) {
        val identities = loadFaceIdentitiesList().toMutableList()
        val contacts = contactRepo.listContacts()
        if (contacts.isEmpty() || identities.isEmpty()) return

        var changed = false
        for (contact in contacts) {
            if (identities.any { it.personId == contact.id }) continue
            if (wasExplicitlyUnlinked(contact.id)) continue

            val profileBitmap = contactRepo.loadProfilePhoto(contact.id) ?: continue
            val embeddings = faceEmbedder.detectAndEmbed(profileBitmap)
            profileBitmap.recycle()
            if (embeddings.isEmpty()) continue

            val profileEmbedding = embeddings[0].second

            var bestIdentityIdx = -1
            var bestSim = threshold

            for ((idx, identity) in identities.withIndex()) {
                if (identity.personId != null) continue
                val sim = cosineSimilarity(profileEmbedding, identity.centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestIdentityIdx = idx
                }
            }

            if (bestIdentityIdx >= 0) {
                val identity = identities[bestIdentityIdx]
                val newName = identity.name.ifBlank { contact.name }
                identities[bestIdentityIdx] = identity.copy(name = newName, personId = contact.id)
                val cv = ContentValues().apply {
                    put("name", newName)
                    put("person_id", contact.id)
                }
                db.update("face_identities", cv, "id = ?", arrayOf(identity.id))
                changed = true
                Log.d(TAG, "Auto-linked face group '${identity.id}' to person '${contact.name}' (similarity: ${"%.3f".format(bestSim)})")
            }
        }
    }

    fun setFaceGroupName(identityId: String, name: String) {
        val cv = ContentValues().apply {
            put("name", name)
        }
        db.update("face_identities", cv, "id = ?", arrayOf(identityId))
    }

    /** Link a face group to a person (contact). Survives group renames. */
    fun linkFaceGroupToPerson(identityId: String, personId: String, personName: String) {
        // Get current name to preserve it if non-blank
        var currentName = ""
        db.rawQuery("SELECT name FROM face_identities WHERE id = ?", arrayOf(identityId)).use { cursor ->
            if (cursor.moveToFirst()) currentName = cursor.getString(0) ?: ""
        }
        val cv = ContentValues().apply {
            put("person_id", personId)
            put("name", currentName.ifBlank { personName })
        }
        val updated = db.update("face_identities", cv, "id = ?", arrayOf(identityId))
        if (updated == 0) return

        // Clear unlinked status since user explicitly re-linked
        db.delete("face_unlinked", "person_id = ?", arrayOf(personId))
    }

    /** Get the personId linked to a face group. */
    fun getFaceGroupPersonId(identityId: String): String? {
        db.rawQuery(
            "SELECT person_id FROM face_identities WHERE id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val value = cursor.getString(0)
                return if (value.isNullOrEmpty()) null else value
            }
        }
        return null
    }

    /** Find face group identity linked to a specific person. */
    fun findIdentityByPersonId(personId: String): FaceIdentity? {
        db.rawQuery(
            "SELECT id, name, centroid, person_id FROM face_identities WHERE person_id = ?",
            arrayOf(personId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return FaceIdentity(
                    cursor.getString(0),
                    cursor.getString(1) ?: "",
                    cursor.getBlob(2).toFloatArray(),
                    cursor.getString(3)?.ifEmpty { null }
                )
            }
        }
        return null
    }

    /** Unlink a face group from a person. Clears personId but keeps the name. */
    fun unlinkFaceGroupFromPerson(personId: String) {
        val cv = ContentValues().apply {
            putNull("person_id")
        }
        db.update("face_identities", cv, "person_id = ?", arrayOf(personId))

        // Remember this was explicitly unlinked so auto-link doesn't redo it
        val ulCv = ContentValues().apply {
            put("person_id", personId)
        }
        db.insertWithOnConflict("face_unlinked", null, ulCv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Check if a person was explicitly unlinked (prevents auto-relinking). */
    fun wasExplicitlyUnlinked(personId: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM face_unlinked WHERE person_id = ?",
            arrayOf(personId)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Find photos by matching a face embedding against all indexed faces.
     * Used when profile photo exists but no face group is linked.
     */
    fun findPhotosByFaceEmbedding(embedding: FloatArray, threshold: Float = 0.55f): List<String> {
        val results = mutableSetOf<String>()
        db.rawQuery("SELECT photo_id, embedding FROM face_entries", null).use { cursor ->
            while (cursor.moveToNext()) {
                val photoId = cursor.getString(0)
                if (photoId in results) continue
                val emb = cursor.getBlob(1).toFloatArray()
                if (cosineSimilarity(emb, embedding) > threshold) {
                    results.add(photoId)
                }
            }
        }
        return results.toList()
    }

    /** Clear the unlinked status (e.g., when profile photo is added). */
    fun clearUnlinkedStatus(personId: String) {
        db.delete("face_unlinked", "person_id = ?", arrayOf(personId))
    }

    fun getFaceGroupName(identityId: String): String? {
        db.rawQuery(
            "SELECT name FROM face_identities WHERE id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                return if (name.isNullOrBlank()) null else name
            }
        }
        return null
    }

    fun loadFaceIdentities() {
        // No-op: identities are now in SQLCipher. Kept for API compatibility.
    }

    // ---- Private helpers ----

    /**
     * Load all face identities from DB.
     */
    private fun loadFaceIdentitiesList(): List<FaceIdentity> {
        val result = mutableListOf<FaceIdentity>()
        db.rawQuery("SELECT id, name, centroid, person_id FROM face_identities", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(FaceIdentity(
                    cursor.getString(0),
                    cursor.getString(1) ?: "",
                    cursor.getBlob(2).toFloatArray(),
                    cursor.getString(3)?.ifEmpty { null }
                ))
            }
        }
        return result
    }

    /**
     * Load face entries for a specific photo.
     */
    private fun loadFaceEntriesForPhoto(photoId: String): List<FaceEntry> {
        val result = mutableListOf<FaceEntry>()
        db.rawQuery(
            "SELECT box, embedding FROM face_entries WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(FaceEntry(
                    cursor.getBlob(0).toFloatArray(),
                    cursor.getBlob(1).toFloatArray()
                ))
            }
        }
        return result
    }

    /**
     * Load feature vector for a specific photo.
     */
    private fun loadFeatureVector(photoId: String): FloatArray? {
        db.rawQuery(
            "SELECT feature_vector FROM photo_index WHERE photo_id = ? AND feature_vector IS NOT NULL",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getBlob(0).toFloatArray()
            }
        }
        return null
    }

    /**
     * Load all face exclusions.
     */
    private fun loadAllExclusions(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        db.rawQuery("SELECT identity_id, photo_id FROM face_exclusions", null).use { cursor ->
            while (cursor.moveToNext()) {
                val identityId = cursor.getString(0)
                val photoId = cursor.getString(1)
                result.getOrPut(identityId) { mutableSetOf() }.add(photoId)
            }
        }
        return result
    }

    /**
     * Compute cosine similarity between two float arrays.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) {
            dot / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        } else {
            0f
        }
    }

    /**
     * Parse a JSON array string into a list of strings.
     */
    private fun parseJsonStringArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse a JSON array string into a list of floats.
     */
    private fun parseJsonFloatArray(json: String): List<Float> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
