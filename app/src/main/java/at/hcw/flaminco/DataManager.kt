package at.hcw.flaminco

import at.hcw.flaminco.model.MeasurementSession
import java.util.Date
import java.util.UUID

/**
 * Hält die Daten während der Sitzung im Speicher (lokal, flüchtig).
 */
object DataManager {
    var session: MeasurementSession = MeasurementSession(
        sessionId = UUID.randomUUID().toString(),
        startedAt = Date()
    )

    // Helper accessors to maintain compatibility during migration or for convenience
    var baseline
        get() = session.baseline
        set(value) { session.baseline = value }
    
    val references get() = session.references
    val samples get() = session.samples

    fun resetSession() {
        session = MeasurementSession(
            sessionId = UUID.randomUUID().toString(),
            startedAt = Date()
        )
    }
}
