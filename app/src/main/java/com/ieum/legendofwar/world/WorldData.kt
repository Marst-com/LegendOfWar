package com.ieum.legendofwar.world

data class WorldData(
    val id: String,
    val seed: Long,
    val name: String,
    val kind: WorldKind,
    val day: Long = 1L,
    val provinces: List<WorldProvince>,
    val regions: List<WorldRegion>,
    val countries: List<WorldCountry>
)

enum class WorldKind {
    REAL_EARTH,
    PROCEDURAL
}

data class MapPoint(
    val x: Double,
    val y: Double
)

data class WorldProvince(
    val id: Int,
    val name: String,
    val countryId: String,
    val regionId: String,
    val terrain: String,
    val population: Long,
    val resources: Map<String, Int>,
    val polygon: List<MapPoint>,
    val neighbors: List<Int>
)

data class WorldRegion(
    val id: String,
    val name: String,
    val countryId: String,
    val provinceIds: List<Int>,
    val capitalProvinceId: Int?
)

data class WorldCountry(
    val id: String,
    val name: String,
    val color: Long,
    val manpower: Long,
    val politicalPower: Int,
    val treasury: Long
)

data class TreatyTerm(
    val type: String,
    val value: String
)

data class DiplomacyProposal(
    val id: String,
    val worldId: String,
    val proposerCountryId: String,
    val targetCountryId: String,
    val title: String,
    val terms: List<TreatyTerm>,
    val status: String,
    val createdDay: Long
)
