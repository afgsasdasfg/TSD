package com.wb.fbs.tsd.utils

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build

object ScanFeedback {

    fun success(context: Context) {
        vibrate(context, 200)
        // TODO: добавить звук успеха (res/raw/success.mp3)
    }

    fun error(context: Context) {
        vibrate(context, 500)
        // TODO: добавить звук ошибки
    }

    private fun vibrate(context: Context, milliseconds: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }
}