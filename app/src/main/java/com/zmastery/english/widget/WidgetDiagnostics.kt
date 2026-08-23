package com.zmastery.english.widget

import android.util.Log

/**
 * نظام تشخيص الودجت — يسجّل كل مرحلة من مراحل التحديث لتسهيل اكتشاف
 * سبب الفشل على أجهزة معينة.
 *
 * لا يسجّل أي مفاتيح أو بيانات حساسة — فقط حالة الودجت ومراحل التنفيذ.
 */
object WidgetDiagnostics {
    private const val TAG = "ZMasteryWidget"

    /** مرحلة نجحت. */
    fun logStage(stage: String, widgetId: Int, detail: String = "") {
        Log.d(TAG, "[✓ $stage] widget=$widgetId $detail")
    }

    /** مرحلة فشلت — لا توقف التنفيذ، بل تسجّل السبب. */
    fun logError(stage: String, widgetId: Int, error: Throwable) {
        Log.e(TAG, "[✗ $stage] widget=$widgetId FAILED: ${error.javaClass.simpleName}: ${error.message}")
    }

    /** مرحلة فشلت مع معلومات إضافية. */
    fun logError(stage: String, widgetId: Int, message: String) {
        Log.e(TAG, "[✗ $stage] widget=$widgetId FAILED: $message")
    }

    /** تسجيل حالة البيانات المحمّلة. */
    fun logDataState(widgetId: Int, hasData: Boolean, streak: Int, xp: Int, mood: String) {
        Log.d(TAG, "[DATA] widget=$widgetId hasData=$hasData streak=$streak xp=$xp mood=$mood")
    }

    /** تسجيل نجاح أو فشل updateAppWidget النهائي. */
    fun logFinalUpdate(widgetId: Int, success: Boolean, error: Throwable? = null) {
        if (success) {
            Log.d(TAG, "[✓ UPDATE] widget=$widgetId → updateAppWidget succeeded")
        } else {
            Log.e(TAG, "[✗ UPDATE] widget=$widgetId → updateAppWidget FAILED: ${error?.message}")
        }
    }
}
