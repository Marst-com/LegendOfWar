package com.ieum.legendofwar.world

import android.content.Context

class WorldCreationService(context: Context) {

    private val appContext = context.applicationContext
    private val database = WorldDatabase(appContext)

    fun createWorld(seed: Long): WorldData {
        val world = if (seed == 1L) {
            GeoJsonWorldImporter(appContext).importEarth()
        } else {
            WorldSeedGenerator.generate(seed)
        }

        database.saveWorld(world)
        return world
    }

    fun loadWorld(worldId: String): WorldData? {
        return database.loadWorld(worldId)
    }

    fun listWorlds(): List<WorldSummary> {
        return database.listWorlds()
    }

    fun close() {
        database.close()
    }
}
