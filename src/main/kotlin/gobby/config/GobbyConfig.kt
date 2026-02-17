package gobby.config

import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.Category
import gg.essential.vigilance.data.Property
import gg.essential.vigilance.data.PropertyType
import gg.essential.vigilance.data.SortingBehavior
import gobby.gui.property.KeybindPropertyInfo
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
        type = PropertyType.CUSTOM,
        name = "Keybind Test",
        description = "Click the button and press a key to bind it.",
        category = "Skyblock",
        subcategory = "Keybinds",
        customPropertyInfo = KeybindPropertyInfo::class,
    )
    var keybindTest = -1

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
        description = "Only enabled to developers, enables some extra features (without being dev this won't work)",
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

    @Property(
        type = PropertyType.SWITCH,
        name = "P5 Debuff Helper",
        description = "Enables P5 debuff helpers for Floor 7",
        category = "Floor 7",
        subcategory = "P5 Debuff Helper"
    )
    var p5DebuffHelper = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Auto Align",
        description = "Automatically solves the arrow align device (P3 S3). ",
        category = "Floor 7",
        subcategory = "Arrow Align Device"
    )
    var autoAlign = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Align Helper",
        description = "Prevents you from overspinning arrows in the align device",
        category = "Floor 7",
        subcategory = "Arrow Align Device"
    )
    var alignHelper = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Sneak to Override",
        description = "Sneak to bypass the align helper block and continue clicking",
        category = "Floor 7",
        subcategory = "Arrow Align Device"
    )
    var alignSneakOverride = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Last Breath Helper",
        description = "Automatically releases and re-charges LB when under a dragon.\nYou MUST hold rightclick for this to work.",
        category = "Floor 7",
        subcategory = "P5 Debuff Helper"
    )
    var lastBreathHelper = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Render Debuff Area",
        description = "Renders the areas where debuff timing applies",
        category = "Floor 7",
        subcategory = "P5 Debuff Helper"
    )
    var renderDebuffArea = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Cancel Interact",
        description = "Cancels the interaction with blocks so u can throw pearls freely",
        category = "Skyblock",
        subcategory = "Map Helper"
    )
    var cancelInteract = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Auto Pre 4",
        description = "Automatically completes the fourth device",
        category = "Floor 7",
        subcategory = "Shooting Device"
    )
    var autoPre4 = false

    @Property(
        type = PropertyType.SELECTOR,
        name = "Aim Style",
        description = "How the aim rotates to the target. You should set this on snap when NOT using a term\nDo NOT enable packet mode, it's kinda here for the memes tbh",
        category = "Floor 7",
        subcategory = "Shooting Device",
        options = ["Snap", "Ease", "Packet"],
    )
    var autoPre4AimStyle = 1

    @Property(
        type = PropertyType.SWITCH,
        name = "Shooting Device ESP",
        description = "Highlights shot positions and shows the current aim target",
        category = "Floor 7",
        subcategory = "Shooting Device"
    )
    var shootingDeviceEsp = false

    @Property(
        type = PropertyType.SWITCH,
        name = "Lights Device Triggerbot",
        description = "Automatically right-clicks the Lights Device levers when looking at them",
        category = "Floor 7",
        subcategory = "Lights Device"
    )
    var lightsDeviceTriggerbot = false

    @Property(
        type = PropertyType.SWITCH,
        name = "P3 Lever Triggerbot",
        description = "Automatically right-clicks P3 levers when looking at them",
        category = "Floor 7",
        subcategory = "Lights Device"
    )
    var p3LeverTriggerbot = false

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
        addDependency("autoPre4AimStyle","autoPre4")
        addDependency("alignSneakOverride","alignHelper")
        addDependency("lastBreathHelper","p5DebuffHelper")
        addDependency("renderDebuffArea","p5DebuffHelper")
    }

    private object Sorting : SortingBehavior() {
        override fun getCategoryComparator(): Comparator<in Category> = Comparator.comparingInt { o: Category ->
            configCategories.indexOf(o.name)
        }

        private val configCategories = listOf(
            "Dungeons", "Floor 7", "Skyblock", "Galatea", "Render", "Developer",
        )
    }
}