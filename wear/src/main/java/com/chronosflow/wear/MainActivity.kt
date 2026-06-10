package com.chronosflow.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal launcher surface so the Wear app is installable and visible in the app list.
 *
 * The primary watch experience is the focus ongoing activity surfaced by
 * [FocusWearListenerService] when a session is active on the phone; this screen is a simple
 * resting entry point rather than a full control surface.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "ChronosFlow"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Start a focus session on your phone to see the live timer here."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            gravity = Gravity.CENTER
        }

        root.addView(title)
        root.addView(subtitle)
        setContentView(root)
    }
}
