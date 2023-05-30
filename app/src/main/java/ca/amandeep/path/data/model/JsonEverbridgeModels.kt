package ca.amandeep.path.data.model

import androidx.compose.runtime.Stable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@Stable
@JsonClass(generateAdapter = true)
data class IncidentMessage(
    @Json(name = "formVariableItems") val formVariableItems: List<FormVariableItem>? = emptyList(),
    @Json(name = "preMessage") val preMessage: String?,
    @Json(name = "subject") val subject: String?,
    @Json(name = "sysVarCurrentTimeFormat") val sysVarCurrentTimeFormat: String? = null,
    @Json(name = "sysVarTodayDateFormat") val sysVarTodayDateFormat: String? = null,
)

@Stable
@JsonClass(generateAdapter = true)
data class FormVariableItem(
    @Json(name = "isRequired") val isRequired: Boolean?,
    @Json(name = "prefixName") val prefixName: String?,
    @Json(name = "seq") val seq: Int?,
    @Json(name = "val") val valX: List<String>?,
    @Json(name = "variableId") val variableId: Long?,
    @Json(name = "variableName") val variableName: String?,
)

@Stable
@JsonClass(generateAdapter = true)
data class AlertData(
    @Json(name = "CreatedDate") val createdDate: Date,
    @Json(name = "ModifiedDate") val modifiedDate: Date,
    @Json(name = "incidentMessage") val incidentMessage: IncidentMessage,
)

@Stable
@JsonClass(generateAdapter = true)
data class Alerts(
    @Json(name = "status") val status: String,
    @Json(name = "data") val alertDatas: List<AlertData>,
)