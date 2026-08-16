package com.switch2.controllers.core

import android.util.Log

interface Switch2Logger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

object Switch2Log {
    private const val DEFAULT_TAG = "Switch2Controllers"
    var logger: Switch2Logger = DefaultLogger()
    var isLoggingEnabled: Boolean = true

    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) logger.debug(tag, message)
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) logger.info(tag, message)
    }

    fun w(message: String, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) logger.warn(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        if (isLoggingEnabled) logger.error(tag, message, throwable)
    }

    private class DefaultLogger : Switch2Logger {
        override fun debug(tag: String, message: String) {
            Log.d(tag, message)
        }

        override fun info(tag: String, message: String) {
            Log.i(tag, message)
        }

        override fun warn(tag: String, message: String) {
            Log.w(tag, message)
        }

        override fun error(tag: String, message: String, throwable: Throwable?) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
