package gobby.utils.skyblock.dungeon.tiles

/**
 * Contents of this file are based on OdinClient and the work of odtheking under BSD 3-Clause License.
 * All the credits go to him.
 * @author odtheking (https://github.com/odtheking/)
 * License: https://github.com/odtheking/Odin/blob/main/LICENSE
 * Original source: https://github.com/odtheking/Odin/blob/d0dd8febc14b5b122202791098eff879aefcfc2e/src/main/kotlin/me/odinmain/utils/skyblock/dungeon/tiles/Rotations.kt
 */
enum class Rotations(
    val x: Int,
    val z: Int
) {
    NORTH(15, 15),
    SOUTH(-15, -15),
    WEST(15, -15),
    EAST(-15, 15),
    NONE(0, 0);
}