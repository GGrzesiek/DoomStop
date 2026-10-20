package io.github.ggrzesiek.doomstop

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "doomstop"
    const val BLOCK_FB_APP = "block_fb_app"
    const val DEBUG = "debug"

    fun get(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun blockFbApp(ctx: Context): Boolean = get(ctx).getBoolean(BLOCK_FB_APP, false)

    fun debug(ctx: Context): Boolean = get(ctx).getBoolean(DEBUG, false)
}
