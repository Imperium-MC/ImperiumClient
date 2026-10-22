/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.config.profiles

import com.google.common.hash.Hashing
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.resource.AxoResource
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.dialogs.DialogProvider
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipInputStream

class Profiles private constructor() {

    private var storage: ProfileStorage = ProfileStorage()

    fun loadProfiles() {
        if (Files.exists(PROFILES_CONFIG)) {
            try {
                Files.newBufferedReader(PROFILES_CONFIG).use { stream ->
                    storage = GsonHelper.GSON.fromJson(stream, ProfileStorage::class.java)
                }
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.warn("Failed to load profiles!", e)
            }
        } else {
            storage = ProfileStorage()
            storage.current = newProfile("Default")
            saveProfiles()
            for (name in arrayOf("axolotlclient.json", "custom_hud.json", "keystrokes.json")) {
                val oldPath = AxolotlClientCommon.resolveConfigFile(name)
                val newPath = resolveProfileFile(name)
                if (Files.exists(oldPath) && !Files.exists(newPath)) {
                    try {
                        Files.createDirectories(newPath.parent)
                        Files.move(oldPath, newPath)
                    } catch (e: IOException) {
                        AxolotlClientCommon.getInstance().logger.warn(
                            "Failed to move {} to profile-based config path at {}", oldPath, newPath, e
                        )
                    }
                }
            }
        }
    }

    fun saveProfiles() {
        try {
            Files.createDirectories(PROFILES_CONFIG.parent)
            Files.newBufferedWriter(PROFILES_CONFIG).use { stream ->
                GsonHelper.GSON.toJson(storage, stream)
            }
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to save profiles, falling back to 'default'", e)
        }
    }

    fun iterateAvailable(action: (Profile) -> Unit) {
        storage.available.forEach(action)
    }

    fun remove(profile: Profile) {
        storage.available.remove(profile)
    }

    fun switchTo(profile: Profile) {
        check(storage.available.contains(profile)) { "Unknown profile!" }
        AxolotlClientCommon.getInstance().logger.debug("Switching to profile {}", profile.name())
        storage.current = profile
        AxolotlClientCommon.getInstance().reloadConfig()
    }

    fun getCurrent(): Profile? = storage.current

    fun resolveProfileFile(path: String): Path = getCurrent()!!.getPath().resolve(path)

    @Suppress("UnstableApiUsage")
    fun newProfile(name: String): Profile {
        val p = Profile(name, Hashing.sha512().hashUnencodedChars(UUID.randomUUID().toString()).toString())
        storage.available.add(p)
        return p
    }

    fun duplicate(profile: Profile): Profile {
        AxolotlClientCommon.getInstance().saveConfig()
        val duplicate = newProfile(AxoI18n.translate("profiles.duplicated", profile.name()))
        try {
            Files.copy(profile.getPath(), duplicate.getPath())
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to duplicate profile!")
        }
        return duplicate
    }

    fun exportProfile(profile: Profile): CompletableFuture<*> {
        return DialogProvider.IMPL.save(
            "Choose export destination",
            FabricLoader.getInstance().gameDir.resolve(
                LocalDateTime.now().format(EXPORT_TIME_FORMAT) + "_" + profile.name() + PROFILE_EXPORT_FILE_EXTENSION
            ),
            PROFILE_EXPORT_FILE_EXTENSION
        ).thenAccept { opt ->
            if (opt.isEmpty) {
                return@thenAccept
            }
            val outPath = opt.get()
            if (!outPath.toString().endsWith(PROFILE_EXPORT_FILE_EXTENSION)) {
                AxolotlClientCommon.getInstance().notificationProvider
                    .addStatus(
                        "profiles.profile.export.notification.failed",
                        "profiles.profile.export.notification.failed.invalid_destination"
                    )
                return@thenAccept
            }

            try {
                Files.deleteIfExists(outPath)
                FileSystems.newFileSystem(outPath, mapOf("create" to "true")).use { fs ->
                    val realRoot = profile.getPath()
                    Files.walkFileTree(realRoot, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val zipped = fs.getPath("profile").resolve(realRoot.relativize(file).toString())
                            Files.createDirectories(zipped.parent)
                            Files.copy(file.toAbsolutePath(), zipped)
                            return super.visitFile(file, attrs)
                        }
                    })
                    Files.writeString(fs.getPath(PROFILE_INFO_FILE), GsonHelper.GSON.toJson(ProfileInfo(profile)))
                }
                AxolotlClientCommon.getInstance().notificationProvider
                    .addStatus(
                        "profiles.profile.export.notification.success",
                        "profiles.profile.export.notification.success.desc",
                        profile.name(),
                        FabricLoader.getInstance().gameDir.relativize(outPath)
                    )
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.info("Failed to export profile", e)
                AxolotlClientCommon.getInstance().notificationProvider
                    .addStatus(
                        "profiles.profile.export.notification.failed",
                        "profiles.profile.export.notification.failed.generic"
                    )
            }
        }
    }

    fun importProfiles(): CompletableFuture<List<Profile>> {
        return DialogProvider.IMPL.openMultiple("Import Profile", FabricLoader.getInstance().gameDir, PROFILE_EXPORT_FILE_EXTENSION)
            .thenApply { opt ->
                if (opt.isEmpty) return@thenApply emptyList()
                val imported = opt.get().map { readProfile(it) }.filterNotNull()
                saveProfiles()
                if (imported.isNotEmpty()) {
                    val count = imported.size
                    if (count == 1) {
                        AxolotlClientCommon.getInstance().notificationProvider
                            .addStatus(
                                "profiles.profile.import.notification.success",
                                "profiles.profile.import.notification.success.desc.one"
                            )
                    } else {
                        AxolotlClientCommon.getInstance().notificationProvider
                            .addStatus(
                                "profiles.profile.import.notification.success",
                                "profiles.profile.import.notification.success.desc.more",
                                count
                            )
                    }
                }
                imported
            }
    }

    internal fun readProfile(p: Path): Profile? {
        try {
            FileSystems.newFileSystem(p).use { fs ->
                val profileInfoPath = fs.getPath(PROFILE_INFO_FILE)
                if (!Files.exists(profileInfoPath)) {
                    AxolotlClientCommon.getInstance().logger.warn("Skipping bad profile file at {}", p)
                    AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                        "profiles.profile.import.notification.failed",
                        "profiles.profile.import.notification.malformed_profile",
                        p.fileName
                    )
                    return null
                }
                val profileInfo = GsonHelper.GSON.fromJson(Files.readString(profileInfoPath), ProfileInfo::class.java)
                AxolotlClientCommon.getInstance().logger.debug("Extracting profile: {}", profileInfo)
                val newProfile = newProfile(profileInfo.name)
                Files.createDirectories(newProfile.getPath())
                val fakeRoot = fs.getPath("/profile")
                if (!Files.exists(fakeRoot)) {
                    AxolotlClientCommon.getInstance().logger.debug("Profile is empty!")
                    return newProfile
                }
                Files.walkFileTree(fakeRoot, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val outPath = newProfile.getPath().resolve(fakeRoot.relativize(file).toString()).normalize()
                        AxolotlClientCommon.getInstance().logger.debug("Extracting profile file {} to {}", file, outPath)
                        if (!outPath.startsWith(newProfile.getPath())) {
                            throw AccessDeniedException(file.toString(), profileInfo.name, null)
                        }
                        Files.copy(file, outPath)
                        return super.visitFile(file, attrs)
                    }
                })
                AxolotlClientCommon.getInstance().logger.debug("Extracted profile {}", newProfile.name())
                return newProfile
            }
        } catch (e: AccessDeniedException) {
            AxolotlClientCommon.getInstance().logger.warn("Profile {} tried to escape its directory, aborting import.", p)
            AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                "profiles.profile.import.notification.failed",
                "profiles.profile.import.notification.malformed_profile",
                p.fileName
            )
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to import profile from {}:", p, e)
            AxolotlClientCommon.getInstance().notificationProvider
                .addStatus(
                    "profiles.profile.import.notification.failed",
                    "profiles.profile.import.notification.failed.generic"
                )
        }
        return null
    }

    fun findPresets(): List<ProfilePreset> {
        val mc = AxoMinecraftClient.getInstance()
        val resourceManager = mc.`br$getResourceManager`()
        val presetCandidates = resourceManager.`br$listResources`(
            AxolotlClientCommon.MODID, "profiles/presets"
        ) { id -> id.`br$getPath`().endsWith(PROFILE_EXPORT_FILE_EXTENSION) }
        val foundPresets = ArrayList<ProfilePreset>()
        presetCandidates.forEach { (id, resource) ->
            try {
                ZipInputStream(resource.`br$asStream`()).use { input ->
                    var entry = input.nextEntry
                    while (entry != null && entry.name != PROFILE_INFO_FILE) {
                        entry = input.nextEntry
                    }
                    if (entry == null) {
                        AxolotlClientCommon.getInstance().logger.warn("Skipping bad profile preset from {}", id)
                        return@forEach
                    }
                    val profileInfo = GsonHelper.GSON.fromJson(InputStreamReader(input), ProfileInfo::class.java)
                    foundPresets.add(ProfilePreset(profileInfo, id, resource))
                }
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.warn("Failed to read profile preset from {}", id, e)
            }
        }
        return foundPresets
    }

    @JvmRecord
    data class ProfilePreset(val info: ProfileInfo, val id: AxoIdentifier, val resource: AxoResource) {
        fun importProfile() {
            val tmp = PROFILES_CONFIG.resolveSibling("tmp").resolve(info.id + ".zip")
            try {
                Files.createDirectories(tmp.parent)
                Files.copy(resource.`br$asStream`(), tmp)
                val profile = getInstance().readProfile(tmp)
                Files.delete(tmp)
                Files.delete(tmp.parent)
                if (profile == null) {
                    return
                }
                getInstance().saveProfiles()
                getInstance().switchTo(profile)
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.warn(
                    "Skipping bad profile file at {}, {}:", id, info.name, e
                )
                AxolotlClientCommon.getInstance().notificationProvider.addStatus(
                    "profiles.profile.import.notification.failed",
                    "profiles.profile.import.notification.malformed_profile",
                    info.name
                )
            }
        }
    }

    class Profile(name: String, private val id: String) {
        var name: String = name

        fun getPath(): Path = AxolotlClientCommon.resolveConfigFile("profiles").resolve(id())

        fun name(): String = name

        fun id(): String = id

        override fun toString(): String =
            "Profile[" +
                "name=" + name + ", " +
                "id=" + id + ']'
    }

    @JsonAdapter(ProfileStorageLoader::class)
    class ProfileStorage internal constructor() {
        internal var current: Profile? = null
        internal val available: MutableList<Profile> = ArrayList()

        internal constructor(current: Profile?, available: Collection<Profile>) : this() {
            this.current = current
            this.available.addAll(available)
        }
    }

    class ProfileStorageLoader : TypeAdapter<ProfileStorage>() {

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ProfileStorage?) {
            if (value == null) {
                out.nullValue()
                return
            }

            out.beginObject()
            out.name("current").value(value.current!!.id())
            out.name("available").beginArray()
            for (entry in value.available) {
                out.beginObject()
                out.name("name").value(entry.name())
                out.name("id").value(entry.id())
                out.endObject()
            }
            out.endArray()
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(`in`: JsonReader): ProfileStorage? {
            if (`in`.peek() != JsonToken.BEGIN_OBJECT) {
                return null
            }

            @Suppress("UNCHECKED_CAST")
            val obj = GsonHelper.read(`in`) as Map<String, Any>? ?: return null
            val available = (obj["available"] as List<Map<String, String>>).map { e ->
                Profile(e["name"]!!, e["id"]!!)
            }
            val profiles = available.associateBy { it.id() }
            return ProfileStorage(profiles[obj["current"] as String?], available)
        }
    }

    @JsonAdapter(ProfileInfo.ProfileInfoAdapter::class)
    @JvmRecord
    data class ProfileInfo(val name: String, val id: String) {
        constructor(p: Profile) : this(p.name(), p.id())

        class ProfileInfoAdapter : TypeAdapter<ProfileInfo>() {

            @Throws(IOException::class)
            override fun write(jsonWriter: JsonWriter, profileInfo: ProfileInfo?) {
                if (profileInfo == null) {
                    jsonWriter.nullValue()
                    return
                }
                jsonWriter.beginObject()
                jsonWriter.name("name").value(profileInfo.name)
                    .name("id").value(profileInfo.id)
                jsonWriter.endObject()
            }

            @Throws(IOException::class)
            override fun read(jsonReader: JsonReader): ProfileInfo? {
                if (jsonReader.peek() == JsonToken.NULL) return null

                @Suppress("UNCHECKED_CAST")
                val map = GsonHelper.read(jsonReader) as Map<String, String>? ?: return null
                return ProfileInfo(map["name"]!!, map["id"]!!)
            }
        }
    }

    companion object {
        private val PROFILES_CONFIG: Path =
            AxolotlClientCommon.resolveConfigFile("profiles").resolve("profiles.json")
        private const val PROFILE_EXPORT_FILE_EXTENSION = ".axoprofile"
        private val EXPORT_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss")
        private const val PROFILE_INFO_FILE = "profile_info.json"

        private val INSTANCE = Profiles()

        @JvmStatic
        fun getInstance(): Profiles = INSTANCE
    }
}
