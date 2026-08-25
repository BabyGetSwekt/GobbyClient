package gobby.utils

object HashMix {

    private const val MIX_A = -4658895280553007687L
    private const val MIX_B = -7723592293110705685L
    private const val FIRST_SHIFT = 30
    private const val SECOND_SHIFT = 27
    private const val FINAL_SHIFT = 31

    fun mix(value: Long): Long {
        var result = value
        result = (result xor (result ushr FIRST_SHIFT)) * MIX_A
        result = (result xor (result ushr SECOND_SHIFT)) * MIX_B
        return result xor (result ushr FINAL_SHIFT)
    }

    fun mixRotated(value: Long, rotation: Int): Long = mix(value).rotateLeft(rotation)
}
