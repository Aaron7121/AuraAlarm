package com.auraalarm.pose

import com.auraalarm.data.Exercise
import kotlin.math.max

enum class Phase { UNKNOWN, UP, DOWN }

data class RepFeedback(
    val reps: Int,
    /** ¿Se ven las articulaciones necesarias para este ejercicio? */
    val bodyVisible: Boolean,
    /** Profundidad del movimiento actual, 0 = arriba, 1 = abajo del todo. */
    val depth: Float,
    val phase: Phase,
    val message: String,
)

/**
 * Máquina de estados común: hay que pasar por ARRIBA → ABAJO → ARRIBA para contar una repetición.
 * Las subclases solo traducen la postura a una "profundidad" 0..1 y deciden si la postura es válida.
 */
abstract class RepCounter {
    var reps: Int = 0
        private set
    var phase: Phase = Phase.UNKNOWN
        private set

    private var smoothedDepth: Double? = null
    private var lastRepAtMs: Long = Long.MIN_VALUE / 2

    protected abstract val downThreshold: Double
    protected abstract val upThreshold: Double

    protected data class Reading(
        val depth: Double,
        /** Postura válida para empezar/terminar una repetición (de pie, en plancha...). */
        val validAtTop: Boolean,
        /** Postura válida para el punto bajo. */
        val validAtBottom: Boolean,
        val invalidMessage: String?,
    )

    /** null = no se ven las articulaciones necesarias. */
    protected abstract fun read(pose: BodyPose): Reading?

    protected abstract val notVisibleMessage: String
    protected abstract val startMessage: String
    protected abstract val goDownMessage: String
    protected abstract val almostMessage: String
    protected abstract val goUpMessage: String

    fun update(pose: BodyPose?, timeMs: Long): RepFeedback {
        val reading = pose?.let { read(it) }
            ?: return RepFeedback(reps, false, (smoothedDepth ?: 0.0).toFloat(), phase, notVisibleMessage)

        val depth = smoothedDepth?.let { it + SMOOTHING * (reading.depth - it) } ?: reading.depth
        smoothedDepth = depth

        when (phase) {
            Phase.UNKNOWN -> if (depth <= upThreshold && reading.validAtTop) phase = Phase.UP
            Phase.UP -> if (depth >= downThreshold && reading.validAtBottom) phase = Phase.DOWN
            Phase.DOWN -> if (depth <= upThreshold && reading.validAtTop && timeMs - lastRepAtMs >= MIN_REP_INTERVAL_MS) {
                reps++
                lastRepAtMs = timeMs
                phase = Phase.UP
            }
        }

        val message = when {
            reading.invalidMessage != null -> reading.invalidMessage
            phase == Phase.UNKNOWN -> startMessage
            phase == Phase.DOWN -> goUpMessage
            depth >= 0.4 -> almostMessage
            else -> goDownMessage
        }
        return RepFeedback(reps, true, depth.toFloat().coerceIn(0f, 1f), phase, message)
    }

    companion object {
        private const val SMOOTHING = 0.5
        /** Nadie hace una repetición real en menos de esto; evita contar dos veces por ruido. */
        const val MIN_REP_INTERVAL_MS = 600L

        fun forExercise(exercise: Exercise): RepCounter = when (exercise) {
            Exercise.PUSHUPS -> PushupCounter()
            Exercise.SQUATS, Exercise.RANDOM -> SquatCounter()
        }
    }
}

/**
 * Sentadillas. Combina dos señales para funcionar tanto de perfil como de frente:
 *  - el ángulo de la rodilla (cadera–rodilla–tobillo), fiable de perfil;
 *  - cuánto baja la cadera respecto a la rodilla, medido en "largos de espinilla", fiable de frente.
 * Arriba exige estar de pie con el torso vertical: no se puede contar tumbado en la cama.
 */
class SquatCounter : RepCounter() {
    override val downThreshold = 0.75
    override val upThreshold = 0.25

    override val notVisibleMessage = "No te veo entero: aléjate hasta que se vean hombros, cadera, rodillas y pies"
    override val startMessage = "Ponte de pie para empezar"
    override val goDownMessage = "Baja"
    override val almostMessage = "Más abajo…"
    override val goUpMessage = "¡Ahora sube!"

    override fun read(pose: BodyPose): Reading? {
        val side = pose.bestSide(Side::hip, Side::knee, Side::ankle) ?: return null
        val hip = pose.visible(side.hip)!!
        val knee = pose.visible(side.knee)!!
        val ankle = pose.visible(side.ankle)!!
        val shoulder = pose.visible(side.shoulder) ?: return null

        val kneeAngle = Geometry.angle(hip, knee, ankle)
        val shin = max(Geometry.distance(knee, ankle), 1.0)
        val hipAboveKnee = (knee.y - hip.y) / shin // ≈1 de pie, ≈0 con muslos paralelos al suelo

        val angleDepth = ((170.0 - kneeAngle) / 75.0).coerceIn(0.0, 1.0) // 170° → 0, 95° → 1
        val hipDepth = ((1.0 - hipAboveKnee) / 0.8).coerceIn(0.0, 1.0)   // 1.0 → 0, 0.2 → 1
        val depth = max(angleDepth, hipDepth)

        val torsoUpright = Geometry.angleFromVertical(shoulder, hip) <= 35.0
        val stackedVertically = shoulder.y < hip.y && hip.y < knee.y && knee.y < ankle.y
        val standing = torsoUpright && stackedVertically
        // Abajo el torso se inclina hacia delante, pero los hombros siguen por encima de la cadera.
        val crouched = shoulder.y < hip.y && Geometry.angleFromVertical(shoulder, hip) <= 70.0

        return Reading(
            depth = depth,
            validAtTop = standing,
            validAtBottom = crouched,
            invalidMessage = if (!standing && !crouched) "Ponte de pie, con el cuerpo recto" else null,
        )
    }
}

/**
 * Flexiones. Se mide el ángulo del codo (hombro–codo–muñeca) y se exige:
 *  - torso horizontal (posición de plancha), así no valen flexiones de brazo de pie;
 *  - manos por debajo de los hombros, así no vale tumbarse boca arriba y mover los brazos.
 * Funciona mejor con el teléfono de lado, a la altura del suelo.
 */
class PushupCounter : RepCounter() {
    override val downThreshold = 0.8 // codo ≈ 104°
    override val upThreshold = 0.2   // codo ≈ 146°

    override val notVisibleMessage = "No te veo: pon el teléfono de lado, a 1–2 m, que se vean hombro, codo, mano y cadera"
    override val startMessage = "Ponte en plancha con los brazos estirados"
    override val goDownMessage = "Baja el pecho"
    override val almostMessage = "Más abajo…"
    override val goUpMessage = "¡Empuja!"

    override fun read(pose: BodyPose): Reading? {
        val side = pose.bestSide(Side::shoulder, Side::elbow, Side::wrist, Side::hip) ?: return null
        val shoulder = pose.visible(side.shoulder)!!
        val elbow = pose.visible(side.elbow)!!
        val wrist = pose.visible(side.wrist)!!
        val hip = pose.visible(side.hip)!!

        val elbowAngle = Geometry.angle(shoulder, elbow, wrist)
        val depth = ((160.0 - elbowAngle) / 70.0).coerceIn(0.0, 1.0) // 160° → 0, 90° → 1

        val upperArm = max(Geometry.distance(shoulder, elbow), 1.0)
        val plank = Geometry.angleFromHorizontal(shoulder, hip) <= 45.0
        val handsBelowShoulders = (wrist.y - shoulder.y) >= 0.2 * upperArm
        val valid = plank && handsBelowShoulders

        return Reading(
            depth = depth,
            validAtTop = valid,
            validAtBottom = valid,
            invalidMessage = when {
                !plank -> "Ponte en posición de flexión: cuerpo horizontal"
                !handsBelowShoulders -> "Boca abajo, con las manos apoyadas en el suelo"
                else -> null
            },
        )
    }
}
