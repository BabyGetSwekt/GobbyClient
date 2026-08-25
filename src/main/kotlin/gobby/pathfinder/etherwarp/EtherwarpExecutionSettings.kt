package gobby.pathfinder.etherwarp

internal object EtherwarpExecutionSettings {
    @Volatile
    var rapidCastSpacingNanos: Long = DEFAULT_RAPID_SPACING_NANOS

    @Volatile
    var rotationVarianceEnabled: Boolean = false

    @Volatile
    var teleportSmoothingEnabled: Boolean = false

    @Volatile
    var keepLastServerRotationEnabled: Boolean = false

    @Volatile
    var rotateWaitServerTickEnabled: Boolean = true

    fun setRapidCastSpacingMillis(milliseconds: Long) {
        rapidCastSpacingNanos = milliseconds.coerceIn(MIN_RAPID_SPACING_MILLIS, MAX_RAPID_SPACING_MILLIS) * NANOS_PER_MILLISECOND
    }

    fun boundedRapidSpacing(): Long = rapidCastSpacingNanos.coerceIn(MIN_RAPID_SPACING_NANOS, MAX_RAPID_SPACING_NANOS)

    private const val DEFAULT_RAPID_SPACING_NANOS = 0L
    private const val MIN_RAPID_SPACING_NANOS = 0L
    private const val MAX_RAPID_SPACING_NANOS = 200_000_000L
    private const val MIN_RAPID_SPACING_MILLIS = 0L
    private const val MAX_RAPID_SPACING_MILLIS = 200L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
