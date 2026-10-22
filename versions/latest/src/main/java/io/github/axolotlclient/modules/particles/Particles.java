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

package io.github.axolotlclient.modules.particles;

import java.util.*;
import java.util.stream.Collectors;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption;
import io.github.axolotlclient.mixin.SingleQuadParticleAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.util.ClientColors;
import lombok.Getter;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;

public class Particles extends AbstractModule {

	@Getter
	private static final Particles Instance = new Particles();

	/**
	 * Convenience aliases so short names from Alpine-style profiles resolve: "crit" suppresses
	 * critical_hit, "sharpness"/"enchant" suppress enchanted_hit.
	 */
	private static final Map<String, String> PARTICLE_ALIASES = Map.of(
		"crit", "critical_hit",
		"sharpness", "enchanted_hit",
		"enchant", "enchanted_hit",
		"enchanted", "enchanted_hit"
	);

	public final HashMap<ParticleType<?>, HashMap<String, Option<?>>> particleOptions = new HashMap<>();

	private final OptionCategory cat = OptionCategory.create("particles");
	private final BooleanOption enabled = new BooleanOption("enabled", false);
	private final StringOption disabledParticles = new StringOption("disabled_particles", "",
		this::updateDisabledParticles);
	private final FloatOption critMultiplier = new FloatOption("crit_multiplier", 1.0F, 0.0F, 2.0F);
	private final FloatOption sharpnessMultiplier = new FloatOption("sharpness_multiplier", 1.0F, 0.0F, 2.0F);

	// Alpine profile settings (M6): a global scale across every particle type (on top of
	// the per-type counts and the crit/sharpness multipliers above).
	private final BooleanOption multiplierEnabled = new BooleanOption("multiplier_enabled", false);
	private final FloatOption multiplier = new FloatOption("multiplier", 1.0F, 0.0F, 2.0F);
	private final Set<String> disabledParticleNames = new HashSet<>();

	@Override
	public void init() {
		cat.add(enabled, disabledParticles, critMultiplier, sharpnessMultiplier, multiplierEnabled, multiplier);

		addParticleOptions();
		AxolotlClient.config().rendering.add(cat);
	}

	private void addParticleOptions() {
		for (ParticleType<?> type : BuiltInRegistries.PARTICLE_TYPE.stream().sorted(new AlphabeticalComparator()).toList()) {
			if (BuiltInRegistries.PARTICLE_TYPE.getKey(type) != null) {
				//noinspection DataFlowIssue
				OptionCategory category = OptionCategory.create(
					Arrays.stream(BuiltInRegistries.PARTICLE_TYPE.getKey(type).getPath().split("_"))
						.map(StringUtils::capitalize).collect(Collectors.joining(" ")));
				HashMap<String, Option<?>> optionsByKey = new LinkedHashMap<>();

				populateMap(optionsByKey, new BooleanOption("showParticle", true),
					new IntegerOption("count", 1, 1, 20),
					new BooleanOption("customColor", false),
					new ColorOption("color", "particles.color.tooltip", ClientColors.WHITE));

				if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT) {
					populateMap(optionsByKey, new BooleanOption("alwaysCrit", false));
				}

				optionsByKey.values().forEach(category::add);
				particleOptions.put(type, optionsByKey);

				cat.add(category);
			}
		}
	}

	private void populateMap(HashMap<String, Option<?>> map, Option<?>... options) {
		for (Option<?> option : options) {
			map.put(option.getName(), option);
		}
	}

	public void applyOptions(SingleQuadParticle particle, ParticleType<?> type) {
		if (enabled.get()) {
			HashMap<String, Option<?>> options = particleOptions.get(type);
			if (((BooleanOption) options.get("customColor")).get()) {
				Color color = ((ColorOption) options.get("color")).get();
				particle.setColor(color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F);
				((SingleQuadParticleAccessor) particle).axolotlclient$setColorAlpha(color.getAlpha() / 255F);
			}
		}
	}

	public int getMultiplier(ParticleType<?> type) {
		if (enabled.get() && particleOptions.containsKey(type)) {
			HashMap<String, Option<?>> options = particleOptions.get(type);

			int count = ((IntegerOption) options.get("count")).get();
			if (multiplierEnabled.get()) {
				// Global scale on top of the per-type count; clamped at 0 (0 kills the type).
				return Math.max(0, Math.round(count * multiplier.get()));
			}
			return count;
		}
		return 1;
	}

	/**
	 * Global emitter scaling for crit (critical_hit) and sharpness (enchanted_hit) particles,
	 * applied on top of the per-particle count: 0 removes them entirely, 2 doubles them.
	 */
	public float getGlobalMultiplier(ParticleType<?> type) {
		if (!enabled.get()) {
			return 1F;
		}
		if (type == ParticleTypes.CRIT) {
			return critMultiplier.get();
		}
		if (type == ParticleTypes.ENCHANTED_HIT) {
			return sharpnessMultiplier.get();
		}
		return 1F;
	}

	/**
	 * Whether the particle type is named in the comma-separated "disabled particles" list.
	 * Entries match registry names ("critical_hit", "enchanted_hit") case-insensitively,
	 * namespaces are ignored and the aliases crit/sharpness/enchant are accepted.
	 */
	public boolean isDisabled(ParticleType<?> type) {
		if (!enabled.get() || disabledParticleNames.isEmpty()) {
			return false;
		}
		Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
		if (key == null) {
			return false;
		}
		String path = key.getPath();
		for (String name : disabledParticleNames) {
			if (name.equals(path)) {
				return true;
			}
			String alias = PARTICLE_ALIASES.get(name);
			if (alias != null && alias.equals(path)) {
				return true;
			}
		}
		return false;
	}

	private void updateDisabledParticles(String value) {
		disabledParticleNames.clear();
		for (String entry : value.split(",")) {
			String name = entry.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
			int namespace = name.indexOf(':');
			if (namespace >= 0) {
				name = name.substring(namespace + 1);
			}
			if (!name.isEmpty()) {
				disabledParticleNames.add(name);
			}
		}
	}

	public boolean getAlwaysOn(ParticleType<?> type) {
		return enabled.get() && particleOptions.containsKey(type)
			&& ((BooleanOption) Particles.getInstance().particleOptions.get(type).get("alwaysCrit")).get();
	}

	public boolean getShowParticle(ParticleType<?> type) {
		return enabled.get() && particleOptions.containsKey(type)
			? ((BooleanOption) Particles.getInstance().particleOptions.get(type).get("showParticle")).get()
			: true;
	}

	protected static class AlphabeticalComparator implements Comparator<ParticleType<?>> {

		// Function to compare
		public int compare(ParticleType<?> s1, ParticleType<?> s2) {
			if (getName(s1).equals(getName(s2)))
				return 0;
			String[] strings = {getName(s1), getName(s2)};
			Arrays.sort(strings, Collections.reverseOrder());

			if (strings[0].equals(getName(s1)))
				return 1;
			else
				return -1;
		}

		private String getName(ParticleType<?> type) {
			if (BuiltInRegistries.PARTICLE_TYPE.getKey(type) != null) {
				//noinspection DataFlowIssue
				return BuiltInRegistries.PARTICLE_TYPE.getKey(type).getPath();
			}
			return "";
		}
	}
}
