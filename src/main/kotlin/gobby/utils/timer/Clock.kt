package gobby.utils.timer

class Clock(val delay: Long = 0L) {

    var lastTime = System.currentTimeMillis()

    inline fun getTime(): Long {
        return System.currentTimeMillis() - lastTime
    }

    /**
     * @param setTime sets lastTime if time has passed
     */
    inline fun hasTimePassed(setTime: Boolean = false): Boolean {
        if (getTime() >= delay) {
            if (setTime) lastTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    /**
     * Sets lastTime to now
     */
    inline fun update() {
        lastTime = System.currentTimeMillis()
    }


    /**
     * @param delay the delay to check if it has passed since lastTime
     * @param setTime sets lastTime if time has passed
     */
    inline fun hasTimePassed(delay: Long, setTime: Boolean = false): Boolean {
        if (getTime() >= delay) {
            if (setTime) lastTime = System.currentTimeMillis()
            return true
        }
        return false
    }
}