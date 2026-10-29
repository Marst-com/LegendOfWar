package com.ieum.legendofwar.world

import kotlin.math.max
import kotlin.random.Random

object WorldSeedGenerator {

    fun generate(
        seed: Long,
        columns: Int = 48,
        rows: Int = 24
    ): WorldData {
        require(columns in 8..200) {
            "columns는 8~200 사이여야 해."
        }
        require(rows in 6..100) {
            "rows는 6~100 사이여야 해."
        }
        require(seed != 1L) {
            "시드 1은 현실 지도 전용이야. 가상 세계에는 다른 시드를 사용해."
        }

        val random = Random(seed)
        val countryNames = listOf(
            "오로라 공화국",
            "보레알 제국",
            "신더 연방",
            "버던트 연합",
            "솔리스 왕국",
            "노바 공화국",
            "아르덴 연맹",
            "테라 연방"
        )

        val colors = listOf(
            0xFF4D91FF,
            0xFFE15D5D,
            0xFFFFB84D,
            0xFF43B581,
            0xFFB07CDE,
            0xFF4DC5C5,
            0xFFE58AAE,
            0xFFB7C95B
        )

        val countryCount = 4 + random.nextInt(5)
        val countries = (0 until countryCount).map { index ->
            WorldCountry(
                id = "country_$index",
                name = countryNames[index],
                color = colors[index],
                manpower = 500_000L + random.nextInt(8_000_000),
                politicalPower = random.nextInt(40, 101),
                treasury = random.nextLong(100_000, 2_000_000)
            )
        }

        val provinceOwners = mutableMapOf<Int, String>()
        val provinces = mutableListOf<WorldProvince>()
        val regions = mutableListOf<WorldRegion>()

        val regionWidth = max(3, columns / 8)
        val regionHeight = max(3, rows / 4)

        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val id = y * columns + x

                // 지역을 여러 영토로 묶고, 같은 지역 안에서는
                // 일반적으로 같은 국가가 소유하도록 배치한다.
                val regionX = x / regionWidth
                val regionY = y / regionHeight
                val regionKey = "region_${regionX}_${regionY}"

                val countryIndex = (
                    regionX * 3 + regionY * 5 +
                        random.nextInt(0, 3)
                    ).mod(countryCount)

                provinceOwners[id] = countries[countryIndex].id
            }
        }

        val regionProvinceIds = mutableMapOf<String, MutableList<Int>>()
        val regionOwners = mutableMapOf<String, String>()

        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val id = y * columns + x
                val regionId = "region_${x / regionWidth}_${y / regionHeight}"
                val ownerId = provinceOwners.getValue(id)

                regionProvinceIds.getOrPut(regionId) { mutableListOf() }
                    .add(id)
                regionOwners.putIfAbsent(regionId, ownerId)

                val left = x.toDouble() / columns
                val right = (x + 1.0) / columns
                val top = y.toDouble() / rows
                val bottom = (y + 1.0) / rows

                val jitterX = (random.nextDouble() - 0.5) / columns * 0.28
                val jitterY = (random.nextDouble() - 0.5) / rows * 0.28

                val polygon = listOf(
                    MapPoint(left, top),
                    MapPoint((left + right) / 2 + jitterX, top + jitterY),
                    MapPoint(right, top),
                    MapPoint(right, bottom),
                    MapPoint((left + right) / 2 - jitterX, bottom - jitterY),
                    MapPoint(left, bottom)
                )

                val neighbors = buildList {
                    if (x > 0) add(id - 1)
                    if (x < columns - 1) add(id + 1)
                    if (y > 0) add(id - columns)
                    if (y < rows - 1) add(id + columns)
                }

                val terrain = when (random.nextInt(100)) {
                    in 0..9 -> "산악"
                    in 10..18 -> "사막"
                    in 19..25 -> "숲"
                    in 26..30 -> "습지"
                    else -> "평원"
                }

                provinces += WorldProvince(
                    id = id,
                    name = "${regionX + 1}-${regionY + 1} 영토 ${id + 1}",
                    countryId = ownerId,
                    regionId = regionId,
                    terrain = terrain,
                    population = random.nextLong(10_000, 2_500_000),
                    resources = mapOf(
                        "food" to random.nextInt(0, 100),
                        "iron" to random.nextInt(0, 80),
                        "oil" to random.nextInt(0, 50),
                        "industry" to random.nextInt(0, 100)
                    ),
                    polygon = polygon,
                    neighbors = neighbors
                )
            }
        }

        for ((regionId, ids) in regionProvinceIds) {
            val firstProvince = provinces.first { it.id == ids.first() }
            regions += WorldRegion(
                id = regionId,
                name = "${regionId.substringAfter("region_").replace("_", "-")} 지역",
                countryId = regionOwners.getValue(regionId),
                provinceIds = ids,
                capitalProvinceId = firstProvince.id
            )
        }

        return WorldData(
            id = "world_$seed",
            seed = seed,
            name = "세계 $seed",
            kind = WorldKind.PROCEDURAL,
            provinces = provinces,
            regions = regions,
            countries = countries
        )
    }

    fun validate(world: WorldData) {
        require(world.provinces.map { it.id }.distinct().size == world.provinces.size) {
            "중복된 영토 ID가 있어."
        }

        val provinceIds = world.provinces.map { it.id }.toSet()
        val countryIds = world.countries.map { it.id }.toSet()
        val regionIds = world.regions.map { it.id }.toSet()

        world.provinces.forEach { province ->
            require(province.countryId in countryIds) {
                "존재하지 않는 국가: ${province.countryId}"
            }
            require(province.regionId in regionIds) {
                "존재하지 않는 지역: ${province.regionId}"
            }
            require(province.neighbors.all { it in provinceIds }) {
                "존재하지 않는 인접 영토가 있어."
            }
            require(province.polygon.size >= 3) {
                "영토 경계는 꼭짓점이 3개 이상이어야 해."
            }
        }

        world.provinces.forEach { province ->
            province.neighbors.forEach { neighborId ->
                require(
                    world.provinces.first { it.id == neighborId }
                        .neighbors.contains(province.id)
                ) {
                    "인접 관계가 서로 일치하지 않아: ${province.id}, $neighborId"
                }
            }
        }
    }
}
