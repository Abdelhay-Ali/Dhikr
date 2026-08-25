package com.abdelhay.dhikr.prayer

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** دقّة مستشعر المجال المغناطيسي — تحدّد هل نطلب المعايرة أم لا. */
enum class CompassAccuracy(val label: String) {
    UNRELIABLE("غير موثوقة"),
    LOW("ضعيفة"),
    MEDIUM("متوسّطة"),
    HIGH("عالية");

    val needsCalibration: Boolean get() = this == UNRELIABLE || this == LOW
}

data class QiblaReading(
    /** اتجاه الجهاز بالنسبة للشمال **الحقيقي** بالدرجات (٠..٣٦٠). */
    val heading: Float,
    /** اتجاه القبلة من الشمال الحقيقي بالدرجات. */
    val qiblaBearing: Double,
    val accuracy: CompassAccuracy,
    val hasSensor: Boolean
) {
    /** الزاوية التي يجب أن تدور بها إبرة القبلة على الشاشة. */
    val needleAngle: Float get() = (qiblaBearing.toFloat() - heading + 360f) % 360f

    /** هل يوجّه المستخدم هاتفه إلى القبلة الآن (بهامش ٣ درجات)؟ */
    val isAligned: Boolean get() = needleAngle <= 3f || needleAngle >= 357f
}

/**
 * بوصلة القبلة.
 *
 * ثلاث دقائق تقنية تُفسد البوصلة إن أُهملت، وقد عولجت هنا:
 *
 * ١. المستشعر يعطي الشمال **المغناطيسي**، واتجاه القبلة محسوب من الشمال **الحقيقي**.
 *    الفرق بينهما (الانحراف المغناطيسي) يبلغ في بعض البلدان أكثر من عشر درجات،
 *    فنصحّحه بـ [GeomagneticField] حسب موقع المستخدم.
 *
 * ٢. قراءة المستشعر مهتزّة، فنمرّرها على مرشّح تمرير منخفض بالمتّجهات لا بالأرقام —
 *    لأن المتوسّط العددي يخطئ عند العبور من ٣٥٩ إلى ١.
 *
 * ٣. دقّة المستشعر تتدهور قرب المعادن والشواحن، فنبلّغ المستخدم بدل أن نعطيه رقمًا كاذبًا.
 */
class QiblaCompass(
    context: Context,
    latitude: Double,
    longitude: Double,
    private val onReading: (QiblaReading) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val qiblaBearing: Double = Qibla(Coordinates(latitude, longitude)).direction

    private val declination: Float = GeomagneticField(
        latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis()
    ).declination

    private var accuracy = CompassAccuracy.MEDIUM

    // مكوّنا المتّجه المُرشَّح
    private var smoothedSin = 0f
    private var smoothedCos = 0f
    private var initialized = false

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    val hasSensor: Boolean get() = rotationSensor != null

    fun start() {
        val s = rotationSensor ?: run {
            onReading(QiblaReading(0f, qiblaBearing, accuracy, hasSensor = false))
            return
        }
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X, SensorManager.AXIS_Y,
            remapped
        )
        SensorManager.getOrientation(remapped, orientation)

        val magneticDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val trueDegrees = (magneticDegrees + declination + 360f) % 360f

        val rad = Math.toRadians(trueDegrees.toDouble())
        val s = sin(rad).toFloat()
        val c = cos(rad).toFloat()

        if (!initialized) {
            smoothedSin = s; smoothedCos = c; initialized = true
        } else {
            val alpha = 0.15f
            smoothedSin += alpha * (s - smoothedSin)
            smoothedCos += alpha * (c - smoothedCos)
        }

        val heading = ((Math.toDegrees(
            kotlin.math.atan2(smoothedSin.toDouble(), smoothedCos.toDouble())
        ).toFloat()) + 360f) % 360f

        onReading(QiblaReading(heading, qiblaBearing, accuracy, hasSensor = true))
    }

    override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
        accuracy = when (value) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            else -> CompassAccuracy.UNRELIABLE
        }
    }

    companion object {
        /** المسافة إلى الكعبة بالكيلومترات — معلومة مفيدة ومطمئنة للمستخدم. */
        fun distanceToKaaba(latitude: Double, longitude: Double): Int {
            val kaabaLat = 21.4224779
            val kaabaLng = 39.8251832
            val r = 6371.0
            val dLat = Math.toRadians(kaabaLat - latitude)
            val dLng = Math.toRadians(kaabaLng - longitude)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(kaabaLat)) *
                sin(dLng / 2) * sin(dLng / 2)
            return (2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))).toInt()
        }

        /** فرق الزاوية الأقصر بين اتجاهين. */
        fun angleDifference(a: Float, b: Float): Float {
            val d = abs(a - b) % 360f
            return if (d > 180f) 360f - d else d
        }
    }
}
