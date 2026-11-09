package com.example.myapplication.tracking

import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

data class StoredJourneys(val journeys: List<Journey>, val active: Journey?)

object JourneyCodec {
    private fun encode(journey: Journey) =
        JSONObject().apply {
            put("id", journey.id)
            put("startedAt", journey.startedAt)
            put("durationMillis", journey.durationMillis)
            put("distanceMeters", journey.distanceMeters)
            put("maxSpeed", journey.maxSpeed)
            put("interrupted", journey.interrupted)
            put("name", journey.name)
            put("notes", journey.notes)
            put(
                "samples",
                JSONArray().apply {
                    journey.samples.forEach {
                        put(
                            JSONArray()
                                .put(it.recordedMillis)
                                .put(it.metersPerSecond)
                                .put(it.segment)
                        )
                    }
                },
            )
            put(
                "points",
                JSONArray().apply {
                    journey.points.forEach { p ->
                        put(
                            JSONArray()
                                .put(p.latitude)
                                .put(p.longitude)
                                .put(p.wallMillis)
                                .put(p.segment)
                        )
                    }
                },
            )
        }

    private fun decode(json: JSONObject): Journey {
        val points = json.getJSONArray("points")
        require(points.length() <= 6000) { "Too many route points" }
        val samples = if (json.has("samples")) json.getJSONArray("samples") else null
        require((samples?.length() ?: 0) <= 12000) { "Too many speed samples" }
        return Journey(
                json.getString("id"),
                json.getLong("startedAt"),
                json.getLong("durationMillis"),
                json.getDouble("distanceMeters"),
                json.getDouble("maxSpeed"),
                (0 until points.length()).map { i ->
                    val p = points.getJSONArray(i)
                    RoutePoint(p.getDouble(0), p.getDouble(1), p.getLong(2), p.getInt(3))
                },
                json.optBoolean("interrupted"),
                json.optString("name", ""),
                json.optString("notes", ""),
                samples?.let { samples ->
                    (0 until samples.length()).map { index ->
                        val s = samples.getJSONArray(index)
                        SpeedSample(s.getLong(0), s.getDouble(1), s.getInt(2))
                    }
                } ?: emptyList(),
            )
            .also(JourneyValidation::validate)
    }

    fun encodeAll(journeys: List<Journey>, active: Journey?): String =
        JSONObject()
            .apply {
                put("schema", 1)
                put("journeys", JSONArray().apply { journeys.forEach { put(encode(it)) } })
                if (active != null) put("active", encode(active))
            }
            .toString()

    fun decodeAll(text: String): StoredJourneys {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "Unsupported journey file" }
        val list = root.getJSONArray("journeys")
        val active =
            if (root.has("active") && !root.isNull("active")) root.getJSONObject("active") else null
        return StoredJourneys(
            (0 until list.length()).map { decode(list.getJSONObject(it)) },
            active?.let { decode(it) },
        )
    }

    fun gpx(journey: Journey): String {
        val date =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        return buildString {
            append(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Velocity\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>${xml(journey.name.ifBlank { "Velocity journey" })}</name><desc>${xml(journey.notes)}</desc>"
            )
            var segment: Int? = null
            journey.points.forEach { p ->
                if (segment != p.segment) {
                    if (segment != null) append("</trkseg>")
                    append("<trkseg>")
                    segment = p.segment
                }
                append(
                    "<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\"><time>${date.format(Date(p.wallMillis))}</time></trkpt>"
                )
            }
            if (segment != null) append("</trkseg>")
            append("</trk></gpx>")
        }
    }

    private fun xml(value: String) =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}

class JourneyStorage(private val directory: File) {
    private val file = AtomicFile(File(directory, "journeys.json"))

    fun load(): StoredJourneys =
        try {
            file.openRead().bufferedReader().use { JourneyCodec.decodeAll(it.readText()) }
        } catch (missing: java.io.FileNotFoundException) {
            // An unreadable existing file (or invalid parent) is not an empty history.
            if (
                file.baseFile.exists() ||
                    File(directory, "journeys.json.bak").exists() ||
                    (directory.exists() &&
                        (!directory.isDirectory || !directory.canRead() || !directory.canExecute()))
            ) {
                throw missing
            }
            StoredJourneys(emptyList(), null)
        }

    fun save(journeys: List<Journey>, active: Journey?) {
        journeys.forEach(JourneyValidation::validate)
        active?.let(JourneyValidation::validate)
        val bytes = JourneyCodec.encodeAll(journeys, active).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
