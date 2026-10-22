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

package io.github.axolotlclient.bridge.events.types

import io.github.axolotlclient.bridge.scores.AxoObjective
import java.util.Objects

// Replaces lombok @Data + @EqualsAndHashCode(callSuper = true): equals/hashCode include
// the inherited cancelled flag, toString mirrors the lombok output.
class ScoreboardRenderEvent(val objective: AxoObjective) : CancellableEvent() {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ScoreboardRenderEvent) return false
		if (!super.equals(other)) return false
		return objective == other.objective
	}

	override fun hashCode(): Int = Objects.hash(super.hashCode(), objective)

	override fun toString(): String =
		"ScoreboardRenderEvent(objective=$objective, isCancelled=$isCancelled)"
}
