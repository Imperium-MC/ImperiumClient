/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
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
package io.github.axolotlclient.api.types

import java.util.regex.Pattern

@JvmRecord
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String?,
    val build: String?
) {
    override fun toString(): String {
        val b = StringBuilder()
        b.append(major).append(".").append(minor).append(".").append(patch)
        if (prerelease != null) {
            b.append("-").append(prerelease)
        }
        if (build != null) {
            b.append("+").append(build)
        }
        return b.toString()
    }

    override fun equals(obj: Any?): Boolean {
        if (obj is SemVer) {
            return compareTo(obj) == 0
        }
        return toString() == obj.toString()
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + (prerelease?.hashCode() ?: 0)
        return result
    }

    fun compareTo(o: SemVer): Int {
        var i: Int
        i = major.compareTo(o.major)
        if (i != 0) {
            return i
        }
        i = minor.compareTo(o.minor)
        if (i != 0) {
            return i
        }
        i = patch.compareTo(o.patch)
        if (i != 0) {
            return i
        }
        // null means the version has no prerelease and sorts highest
        i = if (prerelease != null) {
            if (o.prerelease != null) 0 else -1
        } else {
            if (o.prerelease != null) 1 else 0
        }
        if (i != 0) {
            return i
        }

        if (prerelease == null) {
            return 0
        }

        val self = prerelease.split(".").toTypedArray()
        val other = o.prerelease!!.split(".").toTypedArray()

        for (index in 0 until minOf(self.size, other.size)) {
            val selfNumeric = self[index].matches(Regex("\\d+"))
            val otherNumeric = other[index].matches(Regex("\\d+"))
            if (selfNumeric != otherNumeric) {
                return if (selfNumeric) -1 else 1
            } else if (!selfNumeric) {
                i = self[index].compareTo(other[index])
                if (i != 0) {
                    return i
                }
            }
        }
        return self.size.compareTo(other.size)
    }

    fun isNewerThan(other: String): Boolean {
        val parts = other.split(".").toTypedArray()
        val major = parts[0].toInt()

        if (this.major > major) {
            return true
        } else if (this.major < major) {
            return false
        }

        val minor = parts[1].toInt()

        if (this.minor > minor) {
            return true
        } else if (this.minor < minor) {
            return false
        }

        val patch = parts[2].split("-")[0].split("+")[0].toInt()

        if (this.patch > patch) {
            return true
        } else if (this.patch < patch) {
            return false
        }

        return false
    }

    companion object {
        @JvmField
        val EMPTY = SemVer(0, 0, 0, null, null)

        // Adapted from https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
        // NOTE: the Java original shipped an unbalanced pattern (one unclosed group), so ANY SemVer class
        // access threw ExceptionInInitializerError and silently broke API startup + update checks.
        // Restored to the canonical semver.org regex during the Kotlin conversion.
        private val SEMVER_PATTERN =
            Pattern.compile(
                "^(?<major>0|[1-9]\\d*)\\." +
                    "(?<minor>0|[1-9]\\d*)" +
                    "(\\.(?<patch>0|[1-9]\\d*))?" +
                    "(?:-(?<prerelease>(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
                    "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*)" +
                    ")?(?:\\+(?<buildmetadata>[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
            )

        @JvmStatic
        fun parse(version: String): SemVer {
            val matcher = SEMVER_PATTERN.matcher(version)
            if (!matcher.find()) {
                return EMPTY
            }

            val major = matcher.group("major").toInt()
            val minor = matcher.group("minor").toInt()
            // minecraft treats the `patch` component as optional...
            val patchString: String? = matcher.group("patch")
            val patch = patchString?.toInt() ?: 0
            val prerelease = matcher.group("prerelease")
            val buildmetadata = matcher.group("buildmetadata")
            return SemVer(major, minor, patch, prerelease, buildmetadata)
        }
    }
}
