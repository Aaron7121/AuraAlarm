package com.auraalarm.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Posturas sintéticas (vista de perfil, píxeles con Y hacia abajo) para validar el conteo
 * y los filtros anti-trampa sin necesidad de cámara.
 */
class RepCounterTest {

    private fun pose(vararg pts: Pair<Joint, Pair<Float, Float>>, likelihood: Float = 0.95f) =
        BodyPose(pts.associate { (j, p) -> j to Landmark(p.first, p.second, likelihood) })

    /** Interpola linealmente entre dos posturas con las mismas articulaciones. */
    private fun lerp(a: BodyPose, b: BodyPose, t: Float) = BodyPose(
        a.joints.mapValues { (j, la) ->
            val lb = b.joints.getValue(j)
            Landmark(la.x + (lb.x - la.x) * t, la.y + (lb.y - la.y) * t, la.likelihood)
        },
    )

    /** Recorre A→B→A [times] veces con [steps] fotogramas por tramo, a ~30 fps. */
    private fun cycle(counter: RepCounter, a: BodyPose, b: BodyPose, times: Int, steps: Int = 15, start: Long = 0L): Long {
        var t = start
        repeat(times) {
            for (i in 0..steps) { counter.update(lerp(a, b, i / steps.toFloat()), t); t += 33 }
            for (i in 0..steps) { counter.update(lerp(b, a, i / steps.toFloat()), t); t += 33 }
        }
        return t
    }

    // ---------------------------------------------------------------- Sentadillas

    private val standing = pose(
        Joint.LEFT_SHOULDER to (200f to 80f),
        Joint.LEFT_HIP to (200f to 380f),
        Joint.LEFT_KNEE to (205f to 600f),
        Joint.LEFT_ANKLE to (200f to 800f),
    )

    private val squatBottom = pose(
        Joint.LEFT_SHOULDER to (180f to 330f), // torso inclinado ~20°
        Joint.LEFT_HIP to (80f to 610f),       // cadera atrás, a la altura de la rodilla
        Joint.LEFT_KNEE to (280f to 620f),
        Joint.LEFT_ANKLE to (200f to 800f),
    )

    private val halfSquat = lerp(standing, squatBottom, 0.35f)

    @Test
    fun countsFullSquats() {
        val c = SquatCounter()
        cycle(c, standing, squatBottom, times = 5)
        assertEquals(5, c.reps)
    }

    @Test
    fun ignoresHalfSquats() {
        val c = SquatCounter()
        cycle(c, standing, halfSquat, times = 5)
        assertEquals(0, c.reps)
    }

    @Test
    fun lyingInBedNeverCountsSquats() {
        val lying = pose(
            Joint.LEFT_SHOULDER to (100f to 500f),
            Joint.LEFT_HIP to (400f to 505f),
            Joint.LEFT_KNEE to (550f to 505f),
            Joint.LEFT_ANKLE to (700f to 510f),
        )
        val kneesUp = pose(
            Joint.LEFT_SHOULDER to (100f to 500f),
            Joint.LEFT_HIP to (400f to 505f),
            Joint.LEFT_KNEE to (500f to 400f),
            Joint.LEFT_ANKLE to (600f to 505f),
        )
        val c = SquatCounter()
        cycle(c, lying, kneesUp, times = 6)
        assertEquals(0, c.reps)
    }

    @Test
    fun squatNeedsShouldersInFrame() {
        val noShoulders = BodyPose(standing.joints - Joint.LEFT_SHOULDER)
        val bottomNoShoulders = BodyPose(squatBottom.joints - Joint.LEFT_SHOULDER)
        val c = SquatCounter()
        cycle(c, noShoulders, bottomNoShoulders, times = 3)
        assertEquals(0, c.reps)
        assertFalse(c.update(noShoulders, 10_000).bodyVisible)
    }

    @Test
    fun lowConfidenceLandmarksAreIgnored() {
        val blurry = BodyPose(standing.joints.mapValues { it.value.copy(likelihood = 0.2f) })
        val fb = SquatCounter().update(blurry, 0)
        assertFalse(fb.bodyVisible)
    }

    @Test
    fun jitterDoesNotDoubleCount() {
        val c = SquatCounter()
        var t = cycle(c, standing, squatBottom, times = 1)
        assertEquals(1, c.reps)
        // Ruido de un fotograma alrededor del umbral justo después de contar.
        repeat(10) {
            c.update(squatBottom, t); t += 33
            c.update(standing, t); t += 33
        }
        assertTrue("se contaron ${c.reps}", c.reps <= 3)
    }

    @Test
    fun usesTheMostVisibleSide() {
        // Lado derecho visible y correcto; lado izquierdo "adivinado" con baja confianza.
        fun withHiddenLeft(p: BodyPose) = BodyPose(
            p.joints.mapKeys { (j, _) -> Joint.valueOf(j.name.replace("LEFT", "RIGHT")) } +
                p.joints.mapValues { (_, l) -> Landmark(l.x + 300f, l.y - 200f, 0.3f) },
        )
        val c = SquatCounter()
        cycle(c, withHiddenLeft(standing), withHiddenLeft(squatBottom), times = 3)
        assertEquals(3, c.reps)
    }

    // ------------------------------------------------------------------ Flexiones

    private val pushupTop = pose(
        Joint.LEFT_SHOULDER to (300f to 460f),
        Joint.LEFT_ELBOW to (302f to 580f),
        Joint.LEFT_WRIST to (300f to 700f),
        Joint.LEFT_HIP to (550f to 500f),
        Joint.LEFT_KNEE to (750f to 540f),
        Joint.LEFT_ANKLE to (950f to 580f),
    )

    private val pushupBottom = pose(
        Joint.LEFT_SHOULDER to (250f to 580f),
        Joint.LEFT_ELBOW to (350f to 600f),
        Joint.LEFT_WRIST to (300f to 700f),
        Joint.LEFT_HIP to (550f to 560f),
        Joint.LEFT_KNEE to (750f to 580f),
        Joint.LEFT_ANKLE to (950f to 600f),
    )

    @Test
    fun countsPushups() {
        val c = PushupCounter()
        cycle(c, pushupTop, pushupBottom, times = 7)
        assertEquals(7, c.reps)
    }

    @Test
    fun ignoresShallowPushups() {
        val c = PushupCounter()
        cycle(c, pushupTop, lerp(pushupTop, pushupBottom, 0.3f), times = 5)
        assertEquals(0, c.reps)
    }

    @Test
    fun standingArmCurlsDoNotCount() {
        // De pie, torso vertical, doblando los codos.
        val armsStraight = pose(
            Joint.LEFT_SHOULDER to (300f to 200f),
            Joint.LEFT_ELBOW to (300f to 320f),
            Joint.LEFT_WRIST to (300f to 440f),
            Joint.LEFT_HIP to (300f to 500f),
        )
        val armsBent = pose(
            Joint.LEFT_SHOULDER to (300f to 200f),
            Joint.LEFT_ELBOW to (300f to 320f),
            Joint.LEFT_WRIST to (380f to 230f),
            Joint.LEFT_HIP to (300f to 500f),
        )
        val c = PushupCounter()
        cycle(c, armsStraight, armsBent, times = 5)
        assertEquals(0, c.reps)
    }

    @Test
    fun lyingOnBackMovingArmsDoesNotCount() {
        // Boca arriba "haciendo press": cuerpo horizontal pero manos por encima de los hombros.
        val armsUp = pose(
            Joint.LEFT_SHOULDER to (300f to 600f),
            Joint.LEFT_ELBOW to (302f to 480f),
            Joint.LEFT_WRIST to (300f to 360f),
            Joint.LEFT_HIP to (550f to 600f),
        )
        val armsDown = pose(
            Joint.LEFT_SHOULDER to (300f to 600f),
            Joint.LEFT_ELBOW to (400f to 580f),
            Joint.LEFT_WRIST to (350f to 480f),
            Joint.LEFT_HIP to (550f to 600f),
        )
        val c = PushupCounter()
        cycle(c, armsUp, armsDown, times = 5)
        assertEquals(0, c.reps)
    }

    @Test
    fun losingTrackingKeepsCountAndPhase() {
        val c = PushupCounter()
        val t = cycle(c, pushupTop, pushupBottom, times = 2)
        val fb = c.update(null, t)
        assertEquals(2, fb.reps)
        assertFalse(fb.bodyVisible)
        cycle(c, pushupTop, pushupBottom, times = 1, start = t + 1000)
        assertEquals(3, c.reps)
    }
}
