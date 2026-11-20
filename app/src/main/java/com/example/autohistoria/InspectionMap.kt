package com.example.autohistoria

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openInspectionMap(context: Context, point: GnssReading) {
    val uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
        .appendQueryParameter("api","1").appendQueryParameter("query","${point.lat},${point.lon}").build()
    try { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context,"No hay una aplicación para abrir el mapa.",Toast.LENGTH_LONG).show() }
}
