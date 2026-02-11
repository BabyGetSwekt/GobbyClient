package gobby.events

abstract class Events {
    abstract class Cancelable<T: Any> : Events() {
        private var returnValue: T? = null
        var isCanceled: Boolean = false

        open fun cancel() {
            isCanceled = true
        }

        fun hasReturnValue(): Boolean {
            return this.returnValue != null
        }

        fun setReturnValue(value: T) {
            isCanceled = true
            returnValue = value
        }

        fun getReturnValue(): T? {
            return this.returnValue
        }
    }

    open fun onceSent() {}
}