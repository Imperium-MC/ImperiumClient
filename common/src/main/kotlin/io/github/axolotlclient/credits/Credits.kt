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

package io.github.axolotlclient.credits

class Credits private constructor(val name: String, vararg val things: String) {

    companion object {
        private val contributors: MutableCollection<Credits> = LinkedHashSet()
        private val otherPeople: MutableCollection<Credits> = LinkedHashSet()

        init {
            contributor("moehreag", "Author, Programming", "https://github.com/moehreag")
            contributor("TheKodeToad", "Contributor", "Motion Blur", "Freelook", "Zoom")
            contributor("DragonEggBedrockBreaking", "Bugfixes", "Inspiration of new Features")
            contributor("Astralchroma", "Backend Development", "Backend Hosting")
            contributor("gart", "gartbin dev and host", "Image sharing help", "https://gart.sh", "Backend developer")
            contributor("xlingy", "Website author")
            contributor("DarkKronicle", "Bedwars Overlay", "Author of KronHUD, the best HUD mod!")
            contributor("Floweynt", "Playerstats command")
            contributor("XXLSteve", "French Translation")

            otherPerson("AMereBagatelle", "Author of the excellent FabricSkyBoxes Mod")
        }

        @JvmStatic
        fun getContributors(): MutableCollection<Credits> = contributors

        @JvmStatic
        fun getOtherPeople(): MutableCollection<Credits> = otherPeople

        private fun contributor(name: String, vararg things: String) {
            contributors.add(Credits(name, *things))
        }

        private fun otherPerson(name: String, vararg things: String) {
            otherPeople.add(Credits(name, *things))
        }
    }
}
