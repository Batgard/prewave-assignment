package fr.batgard.prewave_assignment.edge.model

import com.fasterxml.jackson.annotation.JsonProperty

data class EdgeResponse(
    @JsonProperty("edges")
    val edges: List<Array<Int>>,
    @JsonProperty("links")
    val links: EdgePageLink,
)
