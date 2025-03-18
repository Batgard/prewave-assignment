package fr.batgard.prewave_assignment.edge.model

import com.fasterxml.jackson.annotation.JsonProperty

data class EdgePageLink(
    @JsonProperty("first")
    val first: String,
    @JsonProperty("last")
    val last: String,
    @JsonProperty("next")
    val next: String?,
    @JsonProperty("previous")
    val previous: String?,
)
