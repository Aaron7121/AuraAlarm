package com.auraalarm.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Representación de la postura independiente de ML Kit, para poder probar el conteo con tests JVM.
 * Coordenadas en píxeles con el eje Y hacia abajo, ya orientadas como se ve en pantalla.
 */
enum class Joint {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

data class Landmark(val x: Float, val y: Float, val likelihood: Float)

enum class Side(
    val shoulder: Joint,
    val elbow: Joint,
    val wrist: Joint,
    val hip: Joint,
    val knee: Joint,
    val ankle: Joint,
) {
    LEFT(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, Joint.LEFT_WRIST, Joint.LEFT_HIP, Joint.LEFT_KNEE, Joint.LEFT_ANKLE),
    RIGHT(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST, Joint.RIGHT_HIP, Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE),
}

class BodyPose(val joints: Map<Joint, Landmark>) {

    fun visible(joint: Joint, minLikelihood: Float = MIN_LIKELIHOOD): Landmark? =
        joints[joint]?.takeIf { it.likelihood >= minLikelihood }

    /**
     * Elige el lado del cuerpo mejor visto. De perfil, el lado lejano suele estar tapado y
     * ML Kit lo "adivina" con baja confianza, así que nos quedamos con el lado más fiable.
     */
    fun bestSide(vararg parts: (Side) -> Joint): Side? =
        Side.entries
            .mapNotNull { side ->
                val likelihoods = parts.map { part -> visible(part(side))?.likelihood ?: return@mapNotNull null }
                side to likelihoods.min()
            }
            .maxByOrNull { it.second }
            ?.first

    companion object {
        const val MIN_LIKELIHOOD = 0.5f

        /** Huesos para dibujar el esqueleto sobre la cámara. */
        val BONES: List<Pair<Joint, Joint>> = listOf(
            Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER,
            Joint.LEFT_HIP to Joint.RIGHT_HIP,
            Joint.LEFT_SHOULDER to Joint.LEFT_HIP,
            Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,
            Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW,
            Joint.LEFT_ELBOW to Joint.LEFT_WRIST,
            Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW,
            Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,
            Joint.LEFT_HIP to Joint.LEFT_KNEE,
            Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
            Joint.RIGHT_HIP to Joint.RIGHT_KNEE,
            Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE,
        )
    }
}

object Geometry {
    /** Ángulo en grados (0–180) formado en [b] por los segmentos b→a y b→c. */
    fun angle(a: Landmark, b: Landmark, c: Landmark): Double {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()
        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()
        val n = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (n == 0.0) return 180.0
        val cos = ((v1x * v2x + v1y * v2y) / n).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    fun distance(a: Landmark, b: Landmark): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** Inclinación de la recta a–b respecto a la horizontal, 0–90°. */
    fun angleFromHorizontal(a: Landmark, b: Landmark): Double =
        Math.toDegrees(atan2(abs(a.y - b.y).toDouble(), abs(a.x - b.x).toDouble()))

    /** Inclinación de la recta a–b respecto a la vertical, 0–90°. */
    fun angleFromVertical(a: Landmark, b: Landmark): Double = 90.0 - angleFromHorizontal(a, b)
}
