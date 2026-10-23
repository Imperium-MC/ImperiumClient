/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.modules.wecui

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.modules.AbstractModule
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.nio.charset.StandardCharsets

/**
 * WorldEdit CUI (Alpine parity: `worldedit_cui`) — renders the server-side WorldEdit
 * selection in-world: the cuboid wireframe with the classic per-block grid on the three
 * faces touching the minimum corner, or the ellipsoid with its bounding box and principal
 * circles. Corner markers use their own colours so pos1/pos2 stay tellable apart.
 *
 * ## Wire protocol
 *
 * Speaks the public WorldEdit CUI protocol on the `worldedit:cui` plugin channel
 * (EPL-2.0; reference implementations: EngineHub's WorldEdit server events and the
 * WorldEditCUI client). Bukkit plugin messaging carries *bare UTF-8 text lines*,
 * `|`-separated, no length prefix and no NBT framing — so the payload codec is a
 * raw-UTF-8 stream codec (the same lesson as `ImperiumIntegration.RAW_UTF8`;
 * a `fromCodec(Codec.STRING)` decoder reads NBT and fails on bare bytes).
 *
 * Client → server, once per session, announces CUI support so WorldEdit starts
 * dispatching selection updates: `v|4`.
 *
 * Server → client, event id first, `+` prefix marks multi-selection events
 * (we render the last-announced region):
 *  - `s|<shape>` — new selection; known shapes `cuboid`, `ellipsoid`,
 *    `polygon2d`, `cylinder`; unknown shapes clear the selection.
 *  - `p|<id>|<x>|<y>|<z>|[area]` — 3D point (id 0 = pos1, 1 = pos2).
 *  - `p2|<id>|<x>|<z>|[area]` — 2D outline point (polygon); the legacy
 *    3-arg form `p2|blockX|blockY|blockZ` sets the second cuboid corner.
 *  - `e|<id>|<x>|<y>|<z>` — ellipsoid center (id 0) / radii (id 1).
 *  - `mm|<minY>|<maxY>` — polygon y bounds.
 *  - `u`, `col`, `grid` — ignored (update ticks / colour hints).
 *
 * Legacy one-shot cuboid line accepted for older setups:
 * `p|world|minX|minY|minZ|maxX|maxY|maxZ|cuboid`. See docs/wecui-server.md for the
 * exact bytes a server (including ImperiumMC's own region tools) should send.
 *
 * ## Rendering
 *
 * Hooked into Fabric's `LevelRenderEvents.BEFORE_GIZMOS` (fired inside
 * `LevelRenderer#submitFeatures` right before `finalizeGizmoCollection`), where
 * we open the frame's gizmo collector window and submit [net.minecraft.gizmos.Gizmos]
 * line/cuboid gizmos in world coordinates. Vanilla's gizmo feature renderer draws them with
 * the game's line pipelines — translucent (alpha < 255) lines are depth-tested but never
 * occlude, matching the classic client-side CUI mods. No mixin of our own is needed.
 *
 * ## Server-side adoption (docs/wecui-server.md)
 *
 * The channel is two-way and server-driven, so the ImperiumMC plugin can visualize its own
 * regions (mine regions, plot regions, event areas) the same way: register the outgoing
 * plugin channel, wait for the client's `v|4` announcement, then send
 * `s|cuboid` + two `p` events per region. Details in docs/wecui-server.md.
 */
class WorldEditCui private constructor() : AbstractModule() {

	// Classic WorldEditCUI palette: bronze-red grid, green pos1, blue pos2; line_opacity
	// (default 0.8) lands them at the classic ~80% translucency.
	@JvmField
	val enabled = BooleanOption("enabled", true) { value ->
		if (value) {
			sendHandshakeIfNeeded()
		}
	}

	@JvmField
	val gridColor = ColorOption("grid_color", Color(0xFFCC4C4C.toInt()))
	@JvmField
	val minCornerColor = ColorOption("min_corner_color", Color(0xFF33CC33.toInt()))
	@JvmField
	val maxCornerColor = ColorOption("max_corner_color", Color(0xFF3333CC.toInt()))
	@JvmField
	val lineOpacity = FloatOption("line_opacity", 0.8f, 0.1f, 1.0f)
	@JvmField
	val showGrid = BooleanOption("show_grid", true)
	@JvmField
	val lineWidth = FloatOption("line_width", 2.0f, 0.5f, 8.0f)

	private val category = OptionCategory.create("worldeditcui")

	/** The handshake is sent at most once per server connection (WorldEdit re-dispatches on re-send). */
	private var handshakeSent = false

	/** Dimension of the last frame's render; a change means the selection is stale. */
	private var lastDimension: String? = null

	override fun init() {
		category.add(enabled, gridColor, minCornerColor, maxCornerColor, lineOpacity, showGrid, lineWidth)
		AxolotlClientCommon.getInstance().getConfig().addCategory(category)

		registerChannel()

		ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
			handshakeSent = false
			WESelectionHolder.getInstance().clear()
			if (enabled.get()) {
				sendHandshake()
			}
		}

		ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
			handshakeSent = false
			WESelectionHolder.getInstance().clear()
		}

		LevelRenderEvents.BEFORE_GIZMOS.register(LevelRenderEvents.BeforeGizmos { context ->
			if (!enabled.get()) {
				return@BeforeGizmos
			}
			val client = Minecraft.getInstance()
			val level = client.level ?: return@BeforeGizmos
			// World change within a server (portal): the old world's selection is stale; the
			// server re-announces when a new selection is made there.
			val dimension = level.dimension().identifier().toString()
			if (dimension != lastDimension) {
				lastDimension = dimension
				WESelectionHolder.getInstance().clear()
			}
			val selection = WESelectionHolder.getInstance()
			if (!selection.hasSelection()) {
				return@BeforeGizmos
			}
			val palette = WESelectionRenderer.Palette(
				withOpacity(gridColor.get()), withOpacity(minCornerColor.get()),
				withOpacity(maxCornerColor.get())
			)
			val segments = WESelectionRenderer.buildSegments(selection, palette, showGrid.get())
			if (segments.isEmpty()) {
				return@BeforeGizmos
			}
			// The frame's gizmo collection window: everything added inside is drained by
			// LevelRenderer.finalizeGizmoCollection a moment later.
			context.levelRenderer().collectPerFrameRenderThreadGizmos().use {
				WESelectionRenderer.submit(segments, lineWidth.get())
			}
		})
	}

	/**
	 * Channel registration follows the FeatureDisabler shape: `PayloadTypeRegistry` for
	 * both directions plus a global receiver, so Bukkit plugin messages on `worldedit:cui`
	 * decode instead of being dropped as an unknown payload. The codec is bare UTF-8 (see
	 * class docs) — WorldEdit writes raw text, no NBT framing.
	 */
	private fun registerChannel() {
		PayloadTypeRegistry.clientboundPlay().register(CLIENTBOUND, ClientboundCuiPayload.CODEC)
		PayloadTypeRegistry.serverboundPlay().register(SERVERBOUND, ServerboundCuiPayload.CODEC)

		ClientPlayNetworking.registerGlobalReceiver(CLIENTBOUND) { payload, ctx ->
			ctx.client().execute(Runnable {
				var message = payload.message
				// "type|a|b|c..." — a leading '+' marks a multi-selection event.
				val multi = message.startsWith("+")
				if (multi) {
					message = message.substring(1)
				}
				val split = message.split("\\|".toRegex(), -1).toTypedArray()
				val args = if (split.size > 1) split.copyOfRange(1, split.size) else emptyArray()
				WESelectionHolder.getInstance().handle(multi, split[0], args.toList())
			})
		}
	}

	private fun sendHandshakeIfNeeded() {
		if (Minecraft.getInstance().getConnection() != null && !handshakeSent) {
			sendHandshake()
		}
	}

	private fun sendHandshake() {
		if (handshakeSent) {
			return
		}
		handshakeSent = true
		ClientPlayNetworking.send(ServerboundCuiPayload("v|$PROTOCOL_VERSION"))
	}

	private fun withOpacity(color: Color): Int =
		color.withAlpha((color.alpha * lineOpacity.get()).toInt()).toInt()

	/** Bare-UTF-8 codec: decode consumes the whole remaining buffer; encode writes raw bytes. */
	@JvmRecord
	data class ClientboundCuiPayload(val message: String) : CustomPacketPayload {
		companion object {
			@JvmField
			val CODEC: StreamCodec<ByteBuf, ClientboundCuiPayload> =
				RAW_UTF8.map({ ClientboundCuiPayload(it) }, { it.message })
		}

		override fun type(): CustomPacketPayload.Type<*> = CLIENTBOUND
	}

	@JvmRecord
	data class ServerboundCuiPayload(val message: String) : CustomPacketPayload {
		companion object {
			@JvmField
			val CODEC: StreamCodec<ByteBuf, ServerboundCuiPayload> =
				RAW_UTF8.map({ ServerboundCuiPayload(it) }, { it.message })
		}

		override fun type(): CustomPacketPayload.Type<*> = SERVERBOUND
	}

	companion object {
		private val Instance = WorldEditCui()

		/** Protocol version we announce; 4 is what current WorldEdit/WorldEditCUI speak. */
		private const val PROTOCOL_VERSION = "4"

		private val CLIENTBOUND =
			CustomPacketPayload.Type<ClientboundCuiPayload>(Identifier.fromNamespaceAndPath("worldedit", "cui"))
		private val SERVERBOUND =
			CustomPacketPayload.Type<ServerboundCuiPayload>(Identifier.fromNamespaceAndPath("worldedit", "cui"))

		@JvmStatic
		fun getInstance(): WorldEditCui = Instance

		internal val RAW_UTF8: StreamCodec<ByteBuf, String> = object : StreamCodec<ByteBuf, String> {
			override fun decode(buf: ByteBuf): String {
				val bytes = ByteArray(buf.readableBytes())
				buf.readBytes(bytes)
				return String(bytes, StandardCharsets.UTF_8)
			}

			override fun encode(buf: ByteBuf, value: String) {
				buf.writeBytes(value.toByteArray(StandardCharsets.UTF_8))
			}
		}
	}
}
