# Module settings reference

Every module in the reference client and the settings it exposes, taken from a real profile export
(70 modules). Use it as the spec when building or extending a module. Types: `boolean`, `number_input`,
`color`, `selector`/`dropdown` (enum), `keybinding`, `string_input`, `module_position`.

Settings from the shared HUD style block (background, brackets, colours, corner radius, blur, border,
text shadow, width/height, hide in F3, position, z-level) are listed once in
[alpine-parity.md](alpine-parity.md#shared-hud-settings) and marked here as **+ HUD style**.

**ON** = enabled in the profile this was taken from, so those modules get built first.

### armor_status

`low_effect` (boolean), `low_effect_threshold` (number_input), `display_name` (boolean), `orientation` (selector), `durability_format` (selector), `name_color` (color), `durability_color_default` (color), `durability_color_start` (color), `durability_color_end` (color) **+ HUD style**

### block_coordinates

`dynamic_block_type_color` (boolean), `label_color` (color), `value_color` (color) **+ HUD style**

### block_overlay

`highlight_mode` (dropdown), `highlight` (boolean), `highlight_color` (color), `outline` (boolean), `outline_color` (color), `line_width` (number_input)

### boss_bar (ON)

`display_health` (boolean), `display_text` (boolean) **+ HUD style**

### chat_settings (ON)

`smooth_chat` (boolean), `animation_duration` (number_input), `chat_algebra` (boolean), `filter_empty_messages` (boolean), `persistent_chat` (boolean), `hover_highlight` (boolean), `screenshot_chat` (keybinding), `peek_chat` (keybinding), `timestamp` (boolean), `timestamp_format` (dropdown), `highlight_ign` (boolean), `ping_ign` (boolean) **+ HUD style**

### chat_settings/compact_chat

`compact_time_based` (boolean), `compact_time_window` (number_input), `compact_ignore_blank` (boolean), `compact_ignore_line_breaks` (boolean)

### chunk_borders

`mode` (dropdown), `radius` (number_input), `line_thickness` (number_input), `offset` (number_input), `lock_y_value` (number_input), `lock_y` (boolean), `keybind` (keybinding), `color` (color)

### clear_water

`disable_liquid_fog` (boolean), `disable_liquid_fov` (boolean), `overlay_transparency` (number_input)

### client

`menu_key` (keybinding), `menu_scale` (number_input), `vanilla_title_screen` (boolean), `main_menu_mod_buttons` (boolean), `menu_theme` (dropdown), `menu_frame_limit` (number_input), `hud_frame_limit` (number_input), `tooltip_position` (dropdown), `limiter` (dropdown), `background_fps` (number_input), `afk_fps` (number_input), `afk_delay` (number_input), `long_afk_fps` (number_input), `long_afk_delay` (number_input), `notification_position` (dropdown), `no_fog` (boolean), `minimal_view_bob` (boolean), `fire_height` (number_input), `shield_height` (number_input), `pumpkin_opacity` (number_input), `spyglass_opacity` (number_input), `frost_opacity` (number_input), `render_advancements` (boolean), `render_toasts` (boolean), `accept_partner_packs` (boolean), `purge_old_logs` (boolean), `disable_open_gl_errors` (boolean), `hide_completely` (boolean), `hide_distant` (boolean), `entity_render_distance` (number_input), `hide_completely` (boolean), `disable_upside_down_checks` (boolean), `hide_distant` (boolean), `hide_nametags` (boolean), `entity_render_distance` (number_input), `lazy_chunk_loading` (boolean), `chunk_updates` (number_input), `hide_hoppers` (boolean), `hide_water` (boolean), `hide_lava` (boolean), `optimize_chests` (boolean), `optimize_spawners` (boolean), `disable_enchant_table_books` (boolean), `hide_completely` (boolean), `hide_off_screen` (boolean), `hide_distant_entity` (boolean), `hide_distant_text` (boolean), `entity_render_distance` (number_input), `text_render_distance` (number_input), `disable_text_glow` (boolean), `optimize_display_name` (boolean), `hide_lightning_bolts` (boolean), `hide_tnt` (boolean), `hide_sand` (boolean), `minimal` (boolean), `disable_tnt_flash` (boolean), `disable_tnt_expansion` (boolean), `disable_font_shadows` (boolean), `model_stitching_fix` (boolean), `culling_fix` (boolean), `optimize_entity_collisions` (boolean)

### clock

`format` (selector) **+ HUD style**

### combo_count

_no own settings_ **+ HUD style**

### compass (ON)

`show_arrow` (boolean), `show_yaw` (boolean), `show_waypoints` (boolean), `waypoint_style` (selector) **+ HUD style**

### cooldowns (ON)

`orientation` (selector), `display_mode` (selector), `show_name` (boolean), `show_icon` (boolean), `collapsed_mode` (boolean), `blink` (boolean), `circle_timer` (boolean), `override_color` (boolean), `duration_color` (color) **+ HUD style**

### coordinates

`display_labels` (boolean), `display_cardinal` (boolean), `display_axis` (boolean), `orientation` (selector), `display_biome` (boolean), `dynamic_biome_color` (boolean), `middle_click_looking_at` (boolean), `shout_coordinates` (keybinding), `use_faction_chat` (boolean), `label_color` (color), `value_color` (color), `direction_color` (color) **+ HUD style**

### cps (ON)

`show_right_click` (boolean) **+ HUD style**

### crosshair

`style` (selector), `length` (number_input), `thickness` (number_input), `gap` (number_input), `show_in_f5` (boolean), `invert_color` (boolean), `color` (color), `dynamic_color` (boolean), `friendly_color` (color), `enemy_color` (color), `size` (number_input)

### day_counter

_no own settings_ **+ HUD style**

### discord_rpc

`display_server_ip` (boolean)

### emotes

`keybinding_wheel_key`, `string_input_slot_1`, `string_input_slot_2`, `string_input_slot_3`, `string_input_slot_4`, `string_input_slot_5`, `string_input_slot_6`, `string_input_slot_7`, `string_input_slot_8`

### enchantedmc (ON)

_no own settings_

### enchantedmc/boosts (ON)

`disabled_types` (dropdown), `show_icon` (boolean), `override_color` (boolean), `hide_permanent` (boolean), `duration_color` (color) **+ HUD style**

### enchantedmc/clicking

`agreed_no_afk` (boolean), `toggle_key` (keybinding), `cps` (number_input), `click_key` (selector), `require_entity` (boolean), `in_screens` (boolean), `require_hold` (boolean)

### enchantedmc/fishing (ON)

`agreed_no_afk` (boolean), `pause_in_menus` (boolean), `toggle_key` (keybinding), `rod_cooldown` (number_input), `delay` (number_input)

### entity_count

`display_total_count` (boolean) **+ HUD style**

### environment (ON)

`time_enabled` (boolean), `time_state` (dropdown), `fixed_time` (boolean), `time_current` (number_input), `time_multiplier` (number_input), `weather_enabled` (boolean), `weather_state` (dropdown), `weather_intensity` (number_input)

### factions (ON)

_no own settings_

### factions/breadcrumbs

`show_tnt` (boolean), `show_sand` (boolean), `show_concrete` (boolean), `timeout` (number_input), `line_width` (number_input), `tnt_color` (color), `sand_color` (color), `concrete_color` (color)

### factions/cannon_playback

`record_tnt` (boolean), `record_falling_blocks` (boolean), `max_recording_time` (number_input), `start_stop_recording` (keybinding), `reset_recording` (keybinding), `next_frame` (keybinding), `prev_frame` (keybinding) **+ HUD style**

### factions/cannon_view

`keybind` (keybinding)

### factions/explosion_boxes

`highlight` (boolean), `block_center` (boolean), `timeout` (number_input), `box_size` (number_input), `line_thickness` (number_input), `color` (color), `creeper_color` (color)

### factions/patchcrumbs

`tracers` (boolean), `use_faction_chat` (boolean), `shout_keybind` (keybinding), `direction` (dropdown), `use_sand` (boolean), `use_explosions` (boolean), `timeout` (number_input), `line_width` (number_input), `primary_color` (color), `secondary_color` (color), `tracer_color` (color), `tracer_width` (number_input)

### farming

`prevent_breaking_bottom_cane` (boolean), `prevent_breaking_bottom_bamboo` (boolean), `disable_cactus_rendering` (boolean), `disable_string_rendering` (boolean)

### fps (ON)

_no own settings_ **+ HUD style**

### fullbright (ON)

`keybind` (keybinding)

### hit_color

`color_armor` (boolean), `color_trim` (boolean), `color` (color)

### hurt_cam

`disable_damage_tilt` (boolean), `tilt_multiplier` (number_input)

### info_hud (ON)

`display_title` (boolean), `brace_style` (dropdown), `header_color` (color), `primary_color` (color), `secondary_color` (color), `registry` (obj) **+ HUD style**

### inventory_tweaks (W5)

`hide_vanilla_potion_hud` (boolean), `keep_centered` (boolean), `compact_item_counts` (boolean)

### inventory_tweaks/inventory_search (ON)

`search_bar_position` (dropdown), `search_bar_width` (number_input), `search_bar_height` (number_input), `highlight_slot` (boolean), `highlight_color` (color)

### inventory_tweaks/scrollable_tooltips (ON)

`smooth_scroll` (boolean), `activation_key` (dropdown), `scroll_multiplier` (number_input), `invert_scroll_direction` (boolean)

### keystrokes

`display_space` (boolean), `full_space` (boolean), `display_mouse_buttons` (boolean), `display_cps` (boolean), `key_size` (number_input) **+ HUD style**

### low_hp_tint

`health_threshold` (number_input), `adaptive_opacity` (boolean), `min_opacity` (number_input), `color` (color)

### memory

_no own settings_ **+ HUD style**

### minimap

`map_size` (number_input), `shape` (selector), `rounded_radius` (number_input), `rotate_map` (boolean), `map_opacity` (number_input), `indicator_size` (number_input), `show_north_indicator` (boolean), `max_depth` (number_input), `display_coordinates` (boolean), `coordinate_position` (selector), `show_waypoints` (boolean), `show_players` (boolean), `player_heads` (boolean), `show_mobs` (boolean), `show_world_border` (boolean), `open_fullscreen_map` (keybinding), `zoom_in` (keybinding), `zoom_out` (keybinding), `change_mode` (keybinding), `north_indicator` (color), `player_indicator` (color), `mob_indicator` (color), `world_border` (color), `background` (color), `coordinate_background` (color) **+ HUD style**

### nametag_editor (ON)

`show_own_nametag` (boolean), `show_client_icon` (boolean), `background` (boolean) **+ HUD style**

### obsidian_count

_no own settings_ **+ HUD style**

### pack_display

`display_description` (boolean), `trim_extension` (boolean), `display_icon` (boolean) **+ HUD style**

### particles

`disabled_particles` (dropdown), `multiplier_enabled` (boolean), `multiplier` (number_input), `affect_critical` (boolean), `affect_sharpness` (boolean)

### patchcrumbs/status_hud

`show_title` (boolean), `show_labels` (boolean), `label_color` (color), `value_color` (color) **+ HUD style**

### perspective_mod

`keybind` (keybinding), `hold_mode` (boolean), `invert_pitch` (boolean), `smooth_transition` (boolean), `animation_duration` (number_input)

### ping

_no own settings_ **+ HUD style**

### playtime

`format` (dropdown) **+ HUD style**

### potion_count

_no own settings_ **+ HUD style**

### potion_status

`show_name` (boolean), `show_duration` (boolean), `show_icon` (boolean), `collapsed_mode` (boolean), `blink` (boolean), `dynamic_color` (boolean), `display_vanilla` (boolean), `duration_color` (color) **+ HUD style**

### reach_display

_no own settings_ **+ HUD style**

### scoreboard (ON)

`display_scores` (boolean), `server_hud_safety` (boolean) **+ HUD style**

### server_display

`display_icon` (boolean) **+ HUD style**

### snap_look

`perspective_mode` (dropdown), `keybind` (keybinding), `hold_mode` (boolean), `smooth_transition` (boolean), `animation_duration` (number_input)

### speed

`unit` (selector) **+ HUD style**

### spotify_state

`display_cover` (boolean), `scrolling_track_name` (boolean), `scrolling_speed` (number_input), `progress_bar` (boolean), `display_timestamp` (boolean), `play_pause` (keybinding), `prev_track` (keybinding), `next_track` (keybinding) **+ HUD style**

### stopwatch

`start_stop` (keybinding), `reset` (keybinding) **+ HUD style**

### tab_editor (ON)

`show_ping_as_number` (boolean), `highlight_group_members` (boolean), `highlight_self` (boolean), `highlight_color_self` (color), `highlight_color_group` (color) **+ HUD style**

### titles (ON)

_no own settings_ **+ HUD style**

### tnt_timer

`dynamic_color` (boolean), `early_fuse_color` (color), `warning_fuse_color` (color), `critical_fuse_color` (color) **+ HUD style**

### toggle_sneak_sprint (ON)

`toggle_sprint` (boolean), `toggle_sneak` (boolean), `double_tap_sprint` (boolean), `key_press_threshold` (number_input), `sneak_fly` (boolean), `fly_while_swimming` (boolean), `fly_boost` (boolean), `fly_boost_hold` (boolean), `vertical_boost` (boolean), `fly_boost_multiplier` (number_input)

### toggle_sneak_sprint/status_hud (ON)

_no own settings_ **+ HUD style**

### tps (ON)

`adaptive_color` (boolean), `high_tps_color` (color), `medium_tps_color` (color), `low_tps_color` (color), `critical_tps_color` (color) **+ HUD style**

### waypoints (ON)

`show_background` (boolean), `create_waypoint_keybind` (keybinding), `create_death_waypoints` (boolean), `limit_death_waypoints` (boolean), `max_death_waypoints` (number_input), `death_waypoint_color` (color) **+ HUD style**

### worldedit_cui

`line_width` (number_input), `primary_color` (color), `secondary_color` (color), `edge_color` (color)

### zoom

`scroll_to_zoom` (boolean), `cinematic_effect` (boolean), `modify_sensitivity` (boolean), `smooth_zoom` (boolean), `activate_zoom` (keybinding), `animation_duration` (number_input), `default_multiplier` (number_input)
