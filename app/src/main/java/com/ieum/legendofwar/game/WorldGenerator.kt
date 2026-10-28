
package com.ieum.legendofwar.game

import kotlin.random.Random

object WorldGenerator {

    fun generate(seed: Long): WorldState {
        val random = Random(seed)

        val countries = listOf(
            Country(
                id = "aurora",
                name = "오로라 공화국",
                color = 0xFF4385F5,
                manpower = 1_000_000,
                politicalPower = 100,
                factories = 8,
                resources = mapOf(
                    "steel" to 120,
                    "oil" to 50,
                    "aluminum" to 40
                )
            ),
            Country(
                id = "borealis",
                name = "보레알 제국",
                color = 0xFFEF5350,
                manpower = 850_000,
                politicalPower = 100,
                factories = 7,
                resources = mapOf(
                    "steel" to 90,
                    "oil" to 80,
                    "aluminum" to 30
                )
            ),
            Country(
                id = "cinder",
                name = "신더 연방",
                color = 0xFFFFB74D,
                manpower = 650_000,
                politicalPower = 100,
                factories = 5,
                resources = mapOf(
                    "steel" to 70,
                    "oil" to 30,
                    "aluminum" to 90
                )
            ),
            Country(
                id = "verdant",
                name = "버던트 연합",
                color = 0xFF66BB6A,
                manpower = 700_000,
                politicalPower = 100,
                factories = 6,
                resources = mapOf(
                    "steel" to 80,
                    "oil" to 45,
                    "aluminum" to 60
                )
            )
        )

        val provinces = mutableListOf<Province>()

        for (row in 0 until 6) {
            for (column in 0 until 8) {
                val id = row * 8 + column

                val ownerIndex = when {
                    column < 2 -> 0
                    column < 4 -> 1
                    column < 6 -> 2
                    else -> 3
                }

                val owner = countries[ownerIndex]

                val neighbors = buildList {
                    if (column > 0) add(id - 1)
                    if (column < 7) add(id + 1)
                    if (row > 0) add(id - 8)
                    if (row < 5) add(id + 8)
                }

                provinces.add(
                    Province(
                        id = id,
                        name = "영토 ${id + 1}",
                        ownerId = owner.id,
                        x = column / 7f,
                        y = row / 5f,
                        neighbors = neighbors
                    )
                )
            }
        }

        // Seed-based world metadata.
        val worldName = listOf(
            "새벽의 대륙",
            "분열된 세계",
            "강철의 시대",
            "네 국가의 운명"
        )[random.nextInt(4)]

        return WorldState(
            seed = seed,
            worldName = worldName,
            day = 1,
            countries = countries,
            provinces = provinces,
            wars = emptyList()
        )
    }
}
