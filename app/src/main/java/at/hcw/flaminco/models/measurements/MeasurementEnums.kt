package at.hcw.flaminco.models.measurements

enum class MeasurementType {
    BASELINE,
    REFERENCE,
    SAMPLE
}

enum class ColorSpace {
    HSV,
    CIELAB,
    RGB
}

enum class SimilarityMetric {
    EUCLIDEAN_DISTANCE,
    COSINE_SIMILARITY
}
