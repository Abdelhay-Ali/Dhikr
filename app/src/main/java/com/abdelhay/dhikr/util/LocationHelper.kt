package com.abdelhay.dhikr.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class Place(val latitude: Double, val longitude: Double, val name: String?)

/**
 * نستعمل LocationManager الموجود في النظام بدل خدمات Google،
 * فيعمل التطبيق على الأجهزة التي لا تحمل Play Services (هواوي مثلًا) وبلا تبعية إضافية.
 *
 * الموقع يُقرأ مرّة ويُحفظ؛ لا نتعقّب المستخدم ولا نرسل موقعه إلى أي خادم.
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** آخر موقع معروف من أي مزوّد متاح — كافٍ تمامًا لحساب المواقيت. */
    suspend fun lastKnown(context: Context): Place? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val best: Location? = runCatching {
            lm.getProviders(true)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()

        best?.let { Place(it.latitude, it.longitude, reverseGeocode(context, it.latitude, it.longitude)) }
    }

    private fun reverseGeocode(context: Context, lat: Double, lng: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale("ar")).getFromLocation(lat, lng, 1)
        list?.firstOrNull()?.let { a ->
            listOfNotNull(a.locality ?: a.subAdminArea, a.countryName).joinToString("، ")
        }
    }.getOrNull()
}
