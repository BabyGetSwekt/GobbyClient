package gobby.pathfinder.etherwarp

data class EtherwarpPathConfig(
    val yawStep: Float = DEFAULT_YAW_STEP,
    val pitchStep: Float = DEFAULT_PITCH_STEP,
    val hWeight: Double = DEFAULT_H_WEIGHT,
    val threads: Int = DEFAULT_THREADS,
    val timeout: Long = DEFAULT_TIMEOUT_MS,
    val etherRange: Double = EtherwarpKind.ETHERWARP.defaultRange
) {
    companion object {
        const val DEFAULT_YAW_STEP = 6f
        const val DEFAULT_PITCH_STEP = 7f
        const val DEFAULT_H_WEIGHT = 6.7
        const val DEFAULT_THREADS = 6
        const val DEFAULT_TIMEOUT_MS = 670L
    }
}
