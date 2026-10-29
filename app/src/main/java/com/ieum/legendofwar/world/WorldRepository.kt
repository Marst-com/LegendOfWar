package com.ieum.legendofwar.world

import android.content.Context

class WorldRepository(context: Context) {
    private val database = WorldDatabase(context.applicationContext)

    fun createProceduralWorld(
        seed: Long,
        columns: Int = 48,
        rows: Int = 24
    ): WorldData {
        val world = WorldSeedGenerator.generate(seed, columns, rows)
        database.saveWorld(world)
        return world
    }

    fun loadWorld(worldId: String): WorldData? {
        return database.loadWorld(worldId)
    }

    fun savedWorlds(): List<WorldSummary> {
        return database.listWorlds()
    }

    fun deleteWorld(worldId: String): Boolean {
        return database.deleteWorld(worldId)
    }

    fun submitProposal(proposal: DiplomacyProposal) {
        require(database.loadWorld(proposal.worldId) != null) {
            "협상 대상 월드가 존재하지 않아."
        }

        database.saveProposal(proposal)
    }

    fun proposals(worldId: String): List<DiplomacyProposal> {
        return database.loadProposals(worldId)
    }

    fun close() {
        database.close()
    }
}
