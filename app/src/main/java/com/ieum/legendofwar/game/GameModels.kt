
package com.ieum.legendofwar.game

data class WorldState(
    val seed: Long,
    val worldName: String,
    val day: Long,
    val countries: List<Country>,
    val provinces: List<Province>,
    val wars: List<War>,
    val paused: Boolean = false
)

data class Country(
    val id: String,
    val name: String,
    val color: Long,
    val manpower: Long,
    val politicalPower: Int,
    val factories: Int,
    val resources: Map<String, Int>
)

data class Province(
    val id: Int,
    val name: String,
    val ownerId: String,
    val x: Float,
    val y: Float,
    val neighbors: List<Int>
)

enum class WarStatus {
    JUSTIFYING,
    PREPARING,
    ACTIVE,
    PEACE
}

data class War(
    val id: String,
    val attackerId: String,
    val defenderId: String,
    val justificationStartedAt: Long,
    val declaredAt: Long?,
    val status: WarStatus
)

data class ArmyDivision(
    val id: String,
    val countryId: String,
    val name: String,
    val provinceId: Int,
    val targetProvinceId: Int?,
    val manpower: Int,
    val organization: Float,
    val equipment: Map<String, Int>
)

data class Equipment(
    val id: String,
    val name: String,
    val category: String,
    val productionCost: Int,
    val attack: Float,
    val defense: Float
)

data class Factory(
    val id: String,
    val countryId: String,
    val provinceId: Int,
    val type: FactoryType,
    val level: Int
)

enum class FactoryType {
    CIVILIAN,
    MILITARY,
    SHIPYARD
}

data class NationalFocus(
    val id: String,
    val countryId: String,
    val title: String,
    val description: String,
    val prerequisiteIds: List<String>,
    val durationDays: Int,
    val completed: Boolean = false
)
