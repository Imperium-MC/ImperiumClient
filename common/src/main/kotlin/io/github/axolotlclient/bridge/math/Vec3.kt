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

package io.github.axolotlclient.bridge.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Suppress("unused")
@JvmRecord
data class Vec3(val x: Double, val y: Double, val z: Double) {
	fun x(x: Double): Vec3 = Vec3(x, y, z)

	fun y(y: Double): Vec3 = Vec3(x, y, z)

	fun z(z: Double): Vec3 = Vec3(x, y, z)

	fun lenSq(): Double = x * x + y * y + z * z

	fun len(): Double = sqrt(lenSq())

	fun add(rhs: Vec3): Vec3 = add(rhs.x, rhs.y, rhs.z)

	fun add(x: Double, y: Double, z: Double): Vec3 = Vec3(this.x + x, this.y + y, this.z + z)

	fun sub(rhs: Vec3): Vec3 = sub(rhs.x, rhs.y, rhs.z)

	fun sub(x: Double, y: Double, z: Double): Vec3 = Vec3(this.x - x, this.y - y, this.z - z)

	fun distSq(rhs: Vec3): Double {
		val dx = x - rhs.x
		val dy = y - rhs.y
		val dz = z - rhs.z

		return dx * dx + dy * dy + dz * dz
	}

	fun dist(rhs: Vec3): Double = sqrt(distSq(rhs))

	fun abs(): Vec3 = Vec3(abs(x), abs(y), abs(z))

	fun max(value: Double): Vec3 = Vec3(max(x, value), max(y, value), max(z, value))

	fun min(value: Double): Vec3 = Vec3(min(x, value), min(y, value), min(z, value))

	fun div(scalar: Double): Vec3 = div(scalar, scalar, scalar)

	fun div(d: Vec3): Vec3 = div(d.x, d.y, d.z)

	fun div(x: Double, y: Double, z: Double): Vec3 = Vec3(this.x / x, this.y / y, this.z / z)

	fun negate(): Vec3 = Vec3(-x, -y, -z)
}
