package com.ieum.legendofwar.world

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

class GeoJsonWorldImporter(private val context: Context) {

    fun importEarth(assetPath: String = "maps/earth-provinces.geojson"): WorldData {
        val json = context.assets.open(assetPath)
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(json)
        require(root.optString("type") == "FeatureCollection") {
            "GeoJSON 파일은 FeatureCollection이어야 해."
        }

        val features = root.getJSONArray("features")
        require(features.length() > 0) {
            "지도 데이터에 Feature가 없어."
        }

        val countries = linkedMapOf<String, WorldCountry>()
        val regionProvinceIds = linkedMapOf<String, MutableList<Int>>()
        val regionCountryIds = linkedMapOf<String, String>()
        val provinceDrafts = mutableListOf<Draft>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val geometry = feature.getJSONObject("geometry")

            val provinceId = properties.optInt("province_id", i)
            val countryId = properties.optString("country_id", "unknown")
            val countryName = properties.optString("country_name", countryId)
            val regionId = properties.optString("region_id", "region_$countryId")
            val regionName = properties.optString("region_name", regionId)

            require(provinceDrafts.none { it.id == provinceId }) {
                "중복된 province_id: $provinceId"
            }

            val polygon = readOuterRing(geometry)
            require(polygon.size >= 3) {
                "영토 $provinceId 의 경계 데이터가 올바르지 않아."
            }

            countries.putIfAbsent(
                countryId,
                WorldCountry(
                    id = countryId,
                    name = countryName,
                    color = parseColor(properties.optString("color", "#4D91FF")),
                    manpower = properties.optLong("manpower", 1_000_000L),
                    politicalPower = properties.optInt("political_power", 50),
                    treasury = properties.optLong("treasury", 100_000L)
                )
            )

            regionProvinceIds.getOrPut(regionId) { mutableListOf() }
                .add(provinceId)
            regionCountryIds[regionId] = countryId

            provinceDrafts += Draft(
                id = provinceId,
                name = properties.optString("name", "영토 $provinceId"),
                countryId = countryId,
                regionId = regionId,
                regionName = regionName,
                terrain = properties.optString("terrain", "unknown"),
                population = properties.optLong("population", 0L),
                resources = emptyMap(),
                polygon = polygon
            )
        }

        val edgeOwners = mutableMapOf<String, MutableSet<Int>>()

        provinceDrafts.forEach { province ->
            province.polygon.indices.forEach { index ->
                val a = province.polygon[index]
                val b = province.polygon[(index + 1) % province.polygon.size]
                val edge = edgeKey(a, b)
                edgeOwners.getOrPut(edge) { mutableSetOf() }.add(province.id)
            }
        }

        val neighbors = provinceDrafts.associate { it.id to mutableSetOf<Int>() }

        edgeOwners.values.forEach { owners ->
            if (owners.size > 1) {
                val ids = owners.toList()
                ids.forEach { a ->
                    ids.filter { it != a }.forEach { b ->
                        neighbors.getValue(a).add(b)
                    }
                }
            }
        }

        val provinces = provinceDrafts.map { draft ->
            WorldProvince(
                id = draft.id,
                name = draft.name,
                countryId = draft.countryId,
                regionId = draft.regionId,
                terrain = draft.terrain,
                population = draft.population,
                resources = draft.resources,
                polygon = draft.polygon,
                neighbors = neighbors.getValue(draft.id).sorted()
            )
        }

        val regions = regionProvinceIds.map { (regionId, provinceIds) ->
            val capitalId = provinceIds.firstOrNull()
            WorldRegion(
                id = regionId,
                name = provinceDrafts.first { it.regionId == regionId }.regionName,
                countryId = regionCountryIds.getValue(regionId),
                provinceIds = provinceIds,
                capitalProvinceId = capitalId
            )
        }

        val world = WorldData(
            id = "world_1",
            seed = 1L,
            name = "현실 세계",
            kind = WorldKind.REAL_EARTH,
            provinces = provinces,
            regions = regions,
            countries = countries.values.toList()
        )

        WorldSeedGenerator.validate(world)
        return world
    }

    private fun readOuterRing(geometry: JSONObject): List<MapPoint> {
        val type = geometry.getString("type")
        val coordinates = geometry.getJSONArray("coordinates")

        val ring = when (type) {
            "Polygon" -> coordinates.getJSONArray(0)
            "MultiPolygon" -> coordinates
                .getJSONArray(0)
                .getJSONArray(0)
            else -> error("지원하지 않는 지오메트리: $type")
        }

        return (0 until ring.length()).map { index ->
            val point = ring.getJSONArray(index)
            val longitude = point.getDouble(0)
            val latitude = point.getDouble(1)

            MapPoint(
                x = ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0),
                y = ((90.0 - latitude) / 180.0).coerceIn(0.0, 1.0)
            )
        }.dropLastWhile {
            // GeoJSON의 닫힌 링은 마지막 점이 첫 점과 같아.
            false
        }.let { points ->
            if (points.size > 1 &&
                abs(points.first().x - points.last().x) < 1e-9 &&
                abs(points.first().y - points.last().y) < 1e-9
            ) points.dropLast(1) else points
        }
    }

    private fun edgeKey(a: MapPoint, b: MapPoint): String {
        fun pointKey(point: MapPoint) =
            "${(point.x * 1_000_000).toLong()}," +
                "${(point.y * 1_000_000).toLong()}"

        val first = pointKey(a)
        val second = pointKey(b)

        return if (first <= second) "$first|$second" else "$second|$first"
    }

    private fun parseColor(value: String): Long {
        return try {
            val hex = value.removePrefix("#")
            val rgb = hex.toLong(16)
            if (hex.length == 6) 0xFF000000L or rgb else rgb
        } catch (_: Exception) {
            0xFF4D91FF
        }
    }

    private data class Draft(
        val id: Int,
        val name: String,
        val countryId: String,
        val regionId: String,
        val regionName: String,
        val terrain: String,
        val population: Long,
        val resources: Map<String, Int>,
        val polygon: List<MapPoint>
    )
}
