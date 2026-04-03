package com.privateai.camera.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import java.io.File

class VaultModelTest {

    // --- VaultMediaType tests ---

    @Test
    fun `VaultMediaType has exactly three values`() {
        assertEquals(3, VaultMediaType.entries.size)
    }

    @Test
    fun `VaultMediaType contains PHOTO, VIDEO, and PDF`() {
        val names = VaultMediaType.entries.map { it.name }.toSet()
        assertEquals(setOf("PHOTO", "VIDEO", "PDF"), names)
    }

    // --- VaultCategory tests ---

    @Test
    fun `VaultCategory has exactly six entries`() {
        assertEquals(6, VaultCategory.entries.size)
    }

    @Test
    fun `VaultCategory entries are in expected order`() {
        val expected = listOf("CAMERA", "VIDEO", "SCAN", "DETECT", "REPORTS", "FILES")
        assertEquals(expected, VaultCategory.entries.map { it.name })
    }

    @Test
    fun `VaultCategory CAMERA has correct label and dirName`() {
        assertEquals("Camera", VaultCategory.CAMERA.label)
        assertEquals("camera", VaultCategory.CAMERA.dirName)
    }

    @Test
    fun `VaultCategory VIDEO has correct label and dirName`() {
        assertEquals("Videos", VaultCategory.VIDEO.label)
        assertEquals("video", VaultCategory.VIDEO.dirName)
    }

    @Test
    fun `VaultCategory SCAN has correct label and dirName`() {
        assertEquals("Scans", VaultCategory.SCAN.label)
        assertEquals("scan", VaultCategory.SCAN.dirName)
    }

    @Test
    fun `VaultCategory DETECT has correct label and dirName`() {
        assertEquals("Detections", VaultCategory.DETECT.label)
        assertEquals("detect", VaultCategory.DETECT.dirName)
    }

    @Test
    fun `VaultCategory REPORTS has correct label and dirName`() {
        assertEquals("Reports", VaultCategory.REPORTS.label)
        assertEquals("reports", VaultCategory.REPORTS.dirName)
    }

    @Test
    fun `VaultCategory FILES has correct label and dirName`() {
        assertEquals("Files", VaultCategory.FILES.label)
        assertEquals("files", VaultCategory.FILES.dirName)
    }

    @Test
    fun `all VaultCategory dirNames are unique`() {
        val dirNames = VaultCategory.entries.map { it.dirName }
        assertEquals(dirNames.size, dirNames.toSet().size)
    }

    // --- VaultPhoto tests ---

    @Test
    fun `VaultPhoto creation preserves all fields`() {
        val encFile = File("/test/photo.enc")
        val thumbFile = File("/test/photo.thumb.enc")

        val photo = VaultPhoto(
            id = "abc-123",
            timestamp = 1000L,
            category = VaultCategory.CAMERA,
            encryptedFile = encFile,
            thumbnailFile = thumbFile
        )

        assertEquals("abc-123", photo.id)
        assertEquals(1000L, photo.timestamp)
        assertEquals(VaultCategory.CAMERA, photo.category)
        assertEquals(encFile, photo.encryptedFile)
        assertEquals(thumbFile, photo.thumbnailFile)
    }

    @Test
    fun `VaultPhoto default mediaType is PHOTO`() {
        val photo = VaultPhoto(
            id = "test",
            timestamp = 0L,
            category = VaultCategory.CAMERA,
            encryptedFile = File("/a"),
            thumbnailFile = File("/b")
        )

        assertEquals(VaultMediaType.PHOTO, photo.mediaType)
    }

    @Test
    fun `VaultPhoto can be created with VIDEO mediaType`() {
        val photo = VaultPhoto(
            id = "vid-1",
            timestamp = 500L,
            category = VaultCategory.VIDEO,
            encryptedFile = File("/v.enc"),
            thumbnailFile = File("/v.thumb.enc"),
            mediaType = VaultMediaType.VIDEO
        )

        assertEquals(VaultMediaType.VIDEO, photo.mediaType)
    }

    @Test
    fun `VaultPhoto copy changes only specified fields`() {
        val original = VaultPhoto(
            id = "orig",
            timestamp = 100L,
            category = VaultCategory.SCAN,
            encryptedFile = File("/orig.enc"),
            thumbnailFile = File("/orig.thumb.enc"),
            mediaType = VaultMediaType.PHOTO
        )

        val copied = original.copy(category = VaultCategory.FILES)

        assertEquals("orig", copied.id)
        assertEquals(100L, copied.timestamp)
        assertEquals(VaultCategory.FILES, copied.category)
        assertEquals(original.encryptedFile, copied.encryptedFile)
        assertEquals(original.thumbnailFile, copied.thumbnailFile)
        assertEquals(VaultMediaType.PHOTO, copied.mediaType)
    }

    @Test
    fun `VaultPhoto equality is based on all fields`() {
        val file1 = File("/a.enc")
        val file2 = File("/a.thumb.enc")

        val photo1 = VaultPhoto("id", 1L, VaultCategory.CAMERA, file1, file2)
        val photo2 = VaultPhoto("id", 1L, VaultCategory.CAMERA, file1, file2)

        assertEquals(photo1, photo2)
        assertEquals(photo1.hashCode(), photo2.hashCode())
    }

    @Test
    fun `VaultPhoto copy creates a new instance`() {
        val original = VaultPhoto("x", 0L, VaultCategory.DETECT, File("/x"), File("/y"))
        val copied = original.copy()

        assertNotSame(original, copied)
        assertEquals(original, copied)
    }
}
