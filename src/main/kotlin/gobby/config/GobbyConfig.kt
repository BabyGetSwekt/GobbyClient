package gobby.config

import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.Category
import gg.essential.vigilance.data.Property
import gg.essential.vigilance.data.PropertyType
import gg.essential.vigilance.data.SortingBehavior
import gobby.gui.property.KeybindProperty
import java.awt.Color
import java.io.File


object GobbyConfig : Vigilant(File("./config/gobbyclientFabric/config.toml"), "GobbyClient", sortingBehavior = Sorting) {

    @Property(
        type = PropertyType.SWITCH,
        name = "Turtle ESP",
        description = "Enable turtle ESP, used for shards",
        category = "Galatea",
        subcategory = "Turtles"
    )
    var turtleEsp = false

    @Property(
        type = PropertyType.COLOR,
        name = "Turtle ESP Color",
        description = "Pick a color",
        category = "Galatea",
        subcategory = "Turtles"
    )
    var turtleEspColor = Color(208, 88, 2, 72)

    @Property(
        type = PropertyType.SWITCH,
        name = "Turtle ESP Line",
        description = "Draws a line to the turtle",
        category = "Galatea",
        subcategory = "Turtles"
    )
    var turtleEspLines = true

    @Property(
        type = PropertyType.COLOR,
        name = "Turtle ESP Line Color",
        description = "Pick a color of the line",
        category = "Galatea",
        subcategory = "Turtles"
    )
    var turtleEspLineColor = Color(208, 88, 2, 100)

    @Property(
        type = PropertyType.SELECTOR,
        name = "Turtle ESP Line Mode",
        description = "At which part you want the lines to be rendered",
        category = "Galatea",
        subcategory = "Turtles",
        options = ["Feet", "Crosshair"],
    )
    var turtleEspLineMode = 1

    @Property(
        type = PropertyType.SWITCH,
        name = "Sea Lumies Block ESP",
        description = "Enables the ESP, duhhh\nThe radius is based on the render distance",
        category = "Galatea",
        subcategory = "Sea Lumies",
        hidden = true
    )
    var seaLumiesESP = false

    @Property(
        type = PropertyType.SLIDER,
        name = "Sea Lumies Amount",
        description = "The amount of sea lumies required to be rendered",
        category = "Galatea",
        subcategory = "Sea Lumies",
        min = 1,
        max = 4,
        hidden = true
    )
    var seaLumiesAmount = 1

    @Property(
        type = PropertyType.COLOR,
        name = "Sea Lumies Color Block ESP",
        description = "Enables the ESP, duhhh",
        category = "Galatea",
        subcategory = "Sea Lumies",
        hidden = true
    )
    var seaLumiesColor = Color(218, 11, 11, 70)

//    @Property(
//        type = PropertyType.CUSTOM,
//        name = "Keybind Test",
//        description = "Fuck keybinds.",
//        category = "Skyblock",
//        subcategory = "Test",
//        customPropertyInfo = KeybindProperty::class,
//    )
//    var keybindUp = 17

    @Property(
        type = PropertyType.SWITCH,
        name = "Starred Mob ESP",
        description = "Highlights starred mobs in dungeons",
        category = "Dungeons",
        subcategory = "Starred Mob ESP"
    )
    var starredMobEsp = false

    @Property(
        type = PropertyType.COLOR,
        name = "Starred Mob ESP Color",
        description = "Pick a color for starred mob highlights",
        category = "Dungeons",
        subcategory = "Starred Mob ESP"
    )
    var starredMobEspColor = Color(255, 255, 239, 72)

    @Property(
        type = PropertyType.SWITCH,
        name = "Starred Mob ESP Line",
        description = "Draws a line to starred mobs",
        category = "Dungeons",
        subcategory = "Starred Mob ESP"
    )
    var starredMobEspLines = false

    @Property(
        type = PropertyType.SELECTOR,
        name = "Starred Mob ESP Line Mode",
        description = "Where the line starts from",
        category = "Dungeons",
        subcategory = "Starred Mob ESP",
        options = ["Feet", "Crosshair"],
    )
    var starredMobEspLineMode = 1

    @Property(
        type = PropertyType.SWITCH,
        name = "Mini Boss ESP",
        description = "Highlights mini bosses in dungeons (Lost Adventurer, Frozen Adventurer, Angry Archaeologist)",
        category = "Dungeons",
        subcategory = "Mini Boss ESP"
    )
    var miniBossEsp = false

    @Property(
        type = PropertyType.COLOR,
        name = "Mini Boss ESP Color",
        description = "Pick a color for mini boss highlights",
        category = "Dungeons",
        subcategory = "Mini Boss ESP"
    )
    var miniBossEspColor = Color(255, 170, 0, 72)

    @Property(
        type = PropertyType.SWITCH,
        name = "Mini Boss ESP Line",
        description = "Draws a line to mini bosses",
        category = "Dungeons",
        subcategory = "Mini Boss ESP"
    )
    var miniBossEspLines = false

    @Property(
        type = PropertyType.SELECTOR,
        name = "Mini Boss ESP Line Mode",
        description = "Where the line starts from",
        category = "Dungeons",
        subcategory = "Mini Boss ESP",
        options = ["Feet", "Crosshair"],
    )
    var miniBossEspLineMode = 1

    @Property(
        type = PropertyType.SWITCH,
        name = "Secret Triggerbot",
        description = "Automatically right-clicks dungeon secrets (chests, levers, skulls) when looking at them",
        category = "Dungeons",
        subcategory = "Secret Triggerbot"
    )
    var secretTriggerbot = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Auto Close Chest",
        description = "Automatically closes secret chests in dungeons",
        category = "Dungeons",
        subcategory = "Auto Close Chest"
    )
    var autoCloseChest = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Full Bright",
        description = "Enables full bright",
        category = "Render",
        subcategory = "Full Bright"
    )
    var fullBright = true

    @Property(
        type = PropertyType.SWITCH,
        name = "No Fire",
        description = "Disables the \"on fire\" effect",
        category = "Render",
        subcategory = "No Fire"
    )
    var noFire = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Terminator Autoclick",
        description = "Automically left clicks to use the salvation ability",
        category = "Skyblock",
        subcategory = "Terminator Autoclicker"
    )
    var terminatorAc = false

    @Property(
        type = PropertyType.SLIDER,
        name = "Clicks Per Second",
        description = "The amount of clicks per second for the autoclicker",
        category = "Skyblock",
        subcategory = "Terminator Autoclicker",
        min = 1,
        max = 12,
    )
    var terminatorCps = 5

    @Property(
        type = PropertyType.SWITCH,
        name = "Party Commands",
        description = "Enables party commands, type !help in party chat for explanation. WORK IN PROGRESS, NOT FULLY DONE YET",
        category = "Skyblock",
        subcategory = "Party Commands"
    )
    var partyCommands = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Render Coordinate Beacons",
        description = "Renders a beacon on the coordinates that someone uses \"/gobby sendcoords\" on.",
        category = "Skyblock",
        subcategory = "Waypoints",
        searchTags = ["waypoints"]
    )
    var renderCoordBeacons = true

    @Property(
        type = PropertyType.SWITCH,
        name = "Enable Developer Mode",
        description = "Only abled to developers, enables some extra features (without being dev this won't work)",
        category = "Developer",
        subcategory = "Developer Mode"
    )
    var devMode = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Anti Pearl Cooldown",
        description = "Prevents the pearl cooldown from being applied, allows u to spam pearls",
        category = "Skyblock",
        subcategory = "Ender Pearl"
    )
    var antiPearlCooldown = false

    fun init() {
        initialize()
        markDirty()
        //addDependency("turtleEspColor", "turtleEsp")
        //addDependency("seaLumiesAmount","seaLumiesESP")
        addDependency("turtleEspLines","turtleEsp")
        addDependency("turtleEspLineColor","turtleEspLines")
        addDependency("turtleEspLineMode","turtleEspLines")
        addDependency("starredMobEspColor","starredMobEsp")
        addDependency("starredMobEspLines","starredMobEsp")
        addDependency("starredMobEspLineMode","starredMobEspLines")
        addDependency("miniBossEspColor","miniBossEsp")
        addDependency("miniBossEspLines","miniBossEsp")
        addDependency("miniBossEspLineMode","miniBossEspLines")
    }

    private object Sorting : SortingBehavior() {
        override fun getCategoryComparator(): Comparator<in Category> = Comparator.comparingInt { o: Category ->
            configCategories.indexOf(o.name)
        }

        private val configCategories = listOf(
            "Skyblock", "Galatea", "Render", "Developer",
        )
    }
}