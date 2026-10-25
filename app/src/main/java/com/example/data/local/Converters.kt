package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.SubtitleSegment
import org.json.JSONArray
import org.json.JSONObject

class SubtitleConverters {

    @TypeConverter
    fun fromSegmentList(segments: List<SubtitleSegment>?): String {
        if (segments == null) return "[]"
        val array = JSONArray()
        for (seg in segments) {
            val obj = JSONObject().apply {
                put("id", seg.id)
                put("startTimeMs", seg.startTimeMs)
                put("endTimeMs", seg.endTimeMs)
                put("frenchText", seg.frenchText)
                put("arabicText", seg.arabicText)
                if (seg.speaker != null) put("speaker", seg.speaker)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toSegmentList(jsonString: String?): List<SubtitleSegment> {
        if (jsonString.isNullOrBlank()) return emptyList()
        val list = mutableListOf<SubtitleSegment>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SubtitleSegment(
                        id = obj.optInt("id", i + 1),
                        startTimeMs = obj.optLong("startTimeMs", 0L),
                        endTimeMs = obj.optLong("endTimeMs", 0L),
                        frenchText = obj.optString("frenchText", ""),
                        arabicText = obj.optString("arabicText", ""),
                        speaker = if (obj.has("speaker")) obj.getString("speaker") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
