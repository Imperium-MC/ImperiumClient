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

package io.github.axolotlclient.bridge.internal

import com.google.common.base.Preconditions
import io.github.axolotlclient.bridge.BridgeVersion
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffects
import io.github.axolotlclient.bridge.item.AxoEnchants
import io.github.axolotlclient.bridge.item.AxoItems
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoSprites
import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.Type
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import org.spongepowered.asm.util.Annotations
import java.io.IOException
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.stream.Collectors
import java.util.stream.Stream

class BridgeValidationPostprocessor : IMixinConfigPlugin {
	override fun onLoad(mixinPackage: String) {
		if (!IS_DEV) {
			return
		}

		val ctPath = FabricLoader.getInstance().getModContainer("axolotlclient")
			.orElseThrow()
			.findPath("axolotlclient.classtweaker")
		if (ctPath.isPresent) {
			try {
				Files.newBufferedReader(ctPath.get()).use { reader ->
					val lines = reader.lines().filter { s: String -> s.startsWith("inject-interface") }
						.map { s: String -> s.split(" +".toRegex()).dropLastWhile { it.isEmpty() } }
						.peek { l: List<String> ->
							if (l.size != 3) {
								throw IllegalArgumentException(
									"Badly formatted interface injection in " + ctPath.get() + ": " + java.lang.String.join(" ", l)
								)
							}
						}.toList()
					val map = HashMap<String, MutableSet<String>>()
					lines.forEach { line: List<String> ->
						map.computeIfAbsent(line[1].replace("/", ".")) { _: String -> HashSet() }.add(line[2])
					}
					remainingInjections = map
				}
			} catch (e: IOException) {
				throw RuntimeException("Failed to read classtweaker file!", e)
			}
		}
	}

	override fun getRefMapperConfig(): String? = null

	override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean = true

	override fun acceptTargets(myTargets: Set<String>, otherTargets: Set<String>) {
	}

	override fun getMixins(): List<String>? = null

	override fun preApply(targetClassName: String, targetClass: org.objectweb.asm.tree.ClassNode, mixinClassName: String, mixinInfo: IMixinInfo) {
		if (!IS_DEV) {
			return
		}

		mixinInfo.getClassNode(0).interfaces.forEach { s: String ->
			val ifs = remainingInjections!![targetClassName]
			ifs?.remove(s)
		}
	}

	override fun postApply(targetClassName: String, targetClass: org.objectweb.asm.tree.ClassNode, mixinClassName: String, mixinInfo: IMixinInfo) {
		if (!IS_DEV) {
			return
		}

		val bridgeMethods = targetClass.interfaces.stream()
			.filter { x: String -> x.startsWith("io/github/axolotlclient/bridge") }
			.map { name: String -> forName(name) }
			.flatMap { clazz: Class<*> ->
				Arrays.stream(
					if (clazz.getAnnotation(RequiresImpl.Inherits::class.java) != null)
						clazz.methods
					else
						clazz.declaredMethods
				)
			}
			.filter { element: AnnotatedElement -> checkVersion(element) }
			.map { method: java.lang.reflect.Method -> method.name + Type.getMethodDescriptor(method) }
			.collect(Collectors.toCollection { HashSet() })

		for (method in targetClass.methods) {
			if (Annotations.getVisible(method, RequiresImpl::class.java) != null) {
				messages.add("bridge method not implemented: $targetClassName::${method.name}${method.desc}")
			}

			bridgeMethods.remove(method.name + method.desc)
		}

		for (bridgeMethod in bridgeMethods) {
			messages.add("bridge method not implemented: $targetClassName::$bridgeMethod")
		}
	}

	companion object {
		private val IS_DEV = FabricLoader.getInstance().isDevelopmentEnvironment
		private val messages: MutableList<String> = ArrayList()
		private var remainingInjections: MutableMap<String, MutableSet<String>>? = null

		private fun checkVersion(impl: RequiresImpl): Boolean =
			BridgeVersion.version().ordinal >= impl.min.ordinal &&
				BridgeVersion.version().ordinal <= impl.max.ordinal

		private fun checkVersion(element: AnnotatedElement): Boolean {
			val ann = element.getAnnotation(RequiresImpl::class.java) ?: return false

			return checkVersion(ann)
		}

		private fun forName(name: String): Class<*> =
			Class.forName(name.replace("/", "."), false, BridgeValidationPostprocessor::class.java.classLoader)

		private fun getStatic(field: Field): Any? = field.get(null)

		private fun loadClasses(path: Path) {
			Preconditions.checkArgument(Files.isDirectory(path))
			Files.walk(path).use { stream: Stream<Path> ->
				stream.filter { p: Path -> Files.isRegularFile(p) }
					.map { p: Path -> path.relativize(p) }
					.map { obj: Path -> obj.toString() }
					.filter { p: String -> p.endsWith(".class") }
					.map { p: String -> p.substring(0, p.length - ".class".length).replace("/", ".") }
					.filter { p: String -> p.startsWith("net.minecraft") || p.startsWith("io.github.axolotlclient") || p.startsWith("com.mojang.blaze3d") }
					.forEach { s: String ->
						try {
							forName(s)
						} catch (e: RuntimeException) {
							if (e.message!!.contains("in environment type CLIENT")) {
								return@forEach
							}

							throw e
						}
					}
			}
		}

		private fun validateFields(clazz: Class<*>) {
			Arrays.stream(clazz.fields)
				.filter { x: Field -> Modifier.isStatic(x.modifiers) }
				.filter { x: Field -> checkVersion(x) }
				.filter { field: Field -> getStatic(field) == null }
				.forEach { field: Field ->
					messages.add("missing bridge value implementation for ${clazz.name}::${field.name}")
				}
		}

		@JvmStatic
		fun validate() {
			Preconditions.checkState(IS_DEV, "BridgeValidationPostprocessor.validate() called in prod?")

			Stream.of("minecraft", "axolotlclient-common")
				.map { x: String -> FabricLoader.getInstance().getModContainer(x).orElseThrow() }
				.flatMap { x: net.fabricmc.loader.api.ModContainer -> x.rootPaths.stream() }
				.forEach { path: Path -> loadClasses(path) }

			validateFields(AxoStatusEffects::class.java)
			validateFields(AxoItems::class.java)
			validateFields(AxoSprites::class.java)
			validateFields(AxoKeys::class.java)
			validateFields(AxoEnchants::class.java)

			remainingInjections!!.forEach { (k: String, v: MutableSet<String>) ->
				for (ifc in v) {
					messages.add("bridge interface not injected: $ifc -> $k")
				}
			}

			if (messages.isNotEmpty()) {
				messages.forEach { s: String -> println(s) }
				throw RuntimeException("missing bridge implementations!")
			}
		}
	}
}
