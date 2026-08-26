package gobby.gui.click

import net.minecraft.resources.Identifier as ResourceLocation

enum class Category(val displayName: String, iconName: String) {
    DUNGEONS("Dungeons", "dungeons"),
    FLOOR7("Floor 7", "floor7"),
    SKYBLOCK("Skyblock", "skyblock"),
    MINING("Mining", "mining"),
    RENDER("Render", "render"),
    COMMANDS("Commands", "commands"),
    DEVELOPER("Developer", "developer");

    val iconTexture: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/category/$iconName")
}
