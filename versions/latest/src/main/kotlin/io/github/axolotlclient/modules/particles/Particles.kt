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

package io.github.axolotlclient.modules.particles

import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.Locale

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.mixin.SingleQuadParticleAccessor
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.ClientColors
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import org.apache.commons.lang3.StringUtils
import kotlin.math.max
import kotlin.math.roundToInt

class Particles : AbstractModule() {
    @JvmField
    val particleOptions: HashMap<ParticleType<*>, HashMap<String, Option<*>>> = HashMap()

    private val cat = OptionCategory.create("particles")
    private val enabled = BooleanOption("enabled", false)
    private val disabledParticles = StringOption("disabled_particles", "") { this.updateDisabledParticles(it) }
    private val critMultiplier = FloatOption("crit_multiplier", 1.0f, 0.0f, 2.0f)
    private val sharpnessMultiplier = FloatOption("sharpness_multiplier", 1.0f, 0.0f, 2.0f)

    // Alpine profile settings (M6): a global scale across every particle type (on top of
    // the per-type counts and the crit/sharpness multipliers above).
    private val multiplierEnabled = BooleanOption("multiplier_enabled", false)
    private val multiplier = FloatOption("multiplier", 1.0f, 0.0f, 2.0f)
    private val disabledParticleNames: MutableSet<String> = HashSet()

    override fun init() {
        cat.add(enabled, disabledParticles, critMultiplier, sharpnessMultiplier, multiplierEnabled, multiplier)

        addParticleOptions()
        AxolotlClient.config().rendering.add(cat)
    }

    private fun addParticleOptions() {
        for (type in BuiltInRegistries.PARTICLE_TYPE.stream().sorted(AlphabeticalComparator()).toList()) {
            if (BuiltInRegistries.PARTICLE_TYPE.getKey(type) != null) {
                val category = OptionCategory.create(
                    Arrays.stream(BuiltInRegistries.PARTICLE_TYPE.getKey(type)!!.getPath().split("_").toTypedArray())
                        .map { s: String -> StringUtils.capitalize(s) }
                        .collect(java.util.stream.Collectors.joining(" "))
                )
                val optionsByKey = LinkedHashMap<String, Option<*>>()

                populateMap(
                    optionsByKey, BooleanOption("showParticle", true),
                    IntegerOption("count", 1, 1, 20),
                    BooleanOption("customColor", false),
                    ColorOption("color", "particles.color.tooltip", ClientColors.WHITE)
                )

                if (type === ParticleTypes.CRIT || type === ParticleTypes.ENCHANTED_HIT) {
                    populateMap(optionsByKey, BooleanOption("alwaysCrit", false))
                }

                optionsByKey.values.forEach(category::add)
                particleOptions[type] = optionsByKey

                cat.add(category)
            }
        }
    }

    private fun populateMap(map: HashMap<String, Option<*>>, vararg options: Option<*>) {
        for (option in options) {
            map[option.getName()] = option
        }
    }

    fun applyOptions(particle: SingleQuadParticle, type: ParticleType<*>) {
        if (enabled.get()) {
            val options = particleOptions[type]!!
            if ((options["customColor"] as BooleanOption).get()) {
                val color = (options["color"] as ColorOption).get()
                particle.setColor(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f)
                (particle as SingleQuadParticleAccessor).`axolotlclient$setColorAlpha`(color.getAlpha() / 255f)
            }
        }
    }

    fun getMultiplier(type: ParticleType<*>): Int {
        if (enabled.get() && particleOptions.containsKey(type)) {
            val options = particleOptions[type]!!

            val count = (options["count"] as IntegerOption).get()
            if (multiplierEnabled.get()) {
                // Global scale on top of the per-type count; clamped at 0 (0 kills the type).
                return max(0, (count * multiplier.get()).roundToInt())
            }
            return count
        }
        return 1
    }

    /**
     * Global emitter scaling for crit (critical_hit) and sharpness (enchanted_hit) particles,
     * applied on top of the per-particle count: 0 removes them entirely, 2 doubles them.
     */
    fun getGlobalMultiplier(type: ParticleType<*>): Float {
        if (!enabled.get()) {
            return 1f
        }
        if (type === ParticleTypes.CRIT) {
            return critMultiplier.get()
        }
        if (type === ParticleTypes.ENCHANTED_HIT) {
            return sharpnessMultiplier.get()
        }
        return 1f
    }

    /**
     * Whether the particle type is named in the comma-separated "disabled particles" list.
     */
    fun isDisabled(type: ParticleType<*>): Boolean {
        if (!enabled.get() || disabledParticleNames.isEmpty()) {
            return false
        }
        val key = BuiltInRegistries.PARTICLE_TYPE.getKey(type) ?: return false
        val path = key.getPath()
        for (name in disabledParticleNames) {
            if (name == path) {
                return true
            }
            val alias = PARTICLE_ALIASES[name]
            if (alias != null && alias == path) {
                return true
            }
        }
        return false
    }

    fun getAlwaysOn(type: ParticleType<*>): Boolean =
        enabled.get() && particleOptions.containsKey(type) &&
            (getInstance().particleOptions[type]!!["alwaysCrit"] as BooleanOption).get()

    fun getShowParticle(type: ParticleType<*>): Boolean =
        if (enabled.get() && particleOptions.containsKey(type))
            (getInstance().particleOptions[type]!!["showParticle"] as BooleanOption).get()
        else
            true

    private fun updateDisabledParticles(value: String) {
        disabledParticleNames.clear()
        for (entry in value.split(",")) {
            var name = entry.trim().lowercase(Locale.ROOT).replace(' ', '_')
            val namespace = name.indexOf(':')
            if (namespace >= 0) {
                name = name.substring(namespace + 1)
            }
            if (name.isNotEmpty()) {
                disabledParticleNames.add(name)
            }
        }
    }

    protected class AlphabeticalComparator : Comparator<ParticleType<*>> {
        // Function to compare
        override fun compare(s1: ParticleType<*>, s2: ParticleType<*>): Int {
            if (getName(s1) == getName(s2)) return 0
            val strings = arrayOf(getName(s1), getName(s2))
            Arrays.sort(strings, Collections.reverseOrder())

            return if (strings[0] == getName(s1)) 1 else -1
        }

        private fun getName(type: ParticleType<*>): String =
            BuiltInRegistries.PARTICLE_TYPE.getKey(type)?.getPath() ?: ""
    }

    companion object {
        /**
         * Convenience aliases so short names from Alpine-style profiles resolve: "crit" suppresses
         * critical_hit, "sharpness"/"enchant" suppress enchanted_hit.
         */
        private val PARTICLE_ALIASES: Map<String, String> = mapOf(
            "crit" to "critical_hit",
            "sharpness" to "enchanted_hit",
            "enchant" to "enchanted_hit",
            "enchanted" to "enchanted_hit"
        )

        private val Instance = Particles()

        @JvmStatic
        fun getInstance(): Particles = Instance
    }
}
