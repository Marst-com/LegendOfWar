package com.ieum.legendofwar.world

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class WorldDatabase(context: Context) :
    SQLiteOpenHelper(context, "legend_of_war.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE worlds (
                id TEXT PRIMARY KEY NOT NULL,
                seed INTEGER NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                day INTEGER NOT NULL,
                payload TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE countries (
                world_id TEXT NOT NULL,
                country_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                manpower INTEGER NOT NULL,
                political_power INTEGER NOT NULL,
                treasury INTEGER NOT NULL,
                PRIMARY KEY(world_id, country_id),
                FOREIGN KEY(world_id) REFERENCES worlds(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE regions (
                world_id TEXT NOT NULL,
                region_id TEXT NOT NULL,
                name TEXT NOT NULL,
                country_id TEXT NOT NULL,
                province_ids TEXT NOT NULL,
                capital_province_id INTEGER,
                PRIMARY KEY(world_id, region_id),
                FOREIGN KEY(world_id) REFERENCES worlds(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE provinces (
                world_id TEXT NOT NULL,
                province_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                country_id TEXT NOT NULL,
                region_id TEXT NOT NULL,
                terrain TEXT NOT NULL,
                population INTEGER NOT NULL,
                resources TEXT NOT NULL,
                polygon TEXT NOT NULL,
                neighbors TEXT NOT NULL,
                PRIMARY KEY(world_id, province_id),
                FOREIGN KEY(world_id) REFERENCES worlds(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE diplomacy_proposals (
                id TEXT PRIMARY KEY NOT NULL,
                world_id TEXT NOT NULL,
                proposer_country_id TEXT NOT NULL,
                target_country_id TEXT NOT NULL,
                title TEXT NOT NULL,
                terms TEXT NOT NULL,
                status TEXT NOT NULL,
                created_day INTEGER NOT NULL,
                FOREIGN KEY(world_id) REFERENCES worlds(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_provinces_owner
            ON provinces(world_id, country_id)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_provinces_region
            ON provinces(world_id, region_id)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_proposals_world
            ON diplomacy_proposals(world_id, created_day)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // 배포 후에는 기존 데이터를 보존하는 마이그레이션을 추가해야 해.
        if (oldVersion < 2 && newVersion >= 2) {
            // 버전 2 스키마 변경을 여기에 추가.
        }
    }

    fun saveWorld(world: WorldData) {
        WorldSeedGenerator.validate(world)

        val payload = encodeWorld(world)
        val db = writableDatabase

        db.beginTransaction()
        try {
            val worldValues = ContentValues().apply {
                put("id", world.id)
                put("seed", world.seed)
                put("name", world.name)
                put("kind", world.kind.name)
                put("day", world.day)
                put("payload", payload)
                put("updated_at", System.currentTimeMillis())
            }

            db.insertWithOnConflict(
                "worlds",
                null,
                worldValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            // 월드의 하위 데이터를 원자적으로 교체해.
            db.delete("countries", "world_id = ?", arrayOf(world.id))
            db.delete("regions", "world_id = ?", arrayOf(world.id))
            db.delete("provinces", "world_id = ?", arrayOf(world.id))

            world.countries.forEach { country ->
                val values = ContentValues().apply {
                    put("world_id", world.id)
                    put("country_id", country.id)
                    put("name", country.name)
                    put("color", country.color)
                    put("manpower", country.manpower)
                    put("political_power", country.politicalPower)
                    put("treasury", country.treasury)
                }
                db.insertOrThrow("countries", null, values)
            }

            world.regions.forEach { region ->
                val values = ContentValues().apply {
                    put("world_id", world.id)
                    put("region_id", region.id)
                    put("name", region.name)
                    put("country_id", region.countryId)
                    put("province_ids", JSONArray(region.provinceIds).toString())
                    if (region.capitalProvinceId == null) {
                        putNull("capital_province_id")
                    } else {
                        put("capital_province_id", region.capitalProvinceId)
                    }
                }
                db.insertOrThrow("regions", null, values)
            }

            world.provinces.forEach { province ->
                val polygon = JSONArray().apply {
                    province.polygon.forEach { point ->
                        put(
                            JSONObject()
                                .put("x", point.x)
                                .put("y", point.y)
                        )
                    }
                }

                val resources = JSONObject()
                province.resources.forEach { (key, value) ->
                    resources.put(key, value)
                }

                val values = ContentValues().apply {
                    put("world_id", world.id)
                    put("province_id", province.id)
                    put("name", province.name)
                    put("country_id", province.countryId)
                    put("region_id", province.regionId)
                    put("terrain", province.terrain)
                    put("population", province.population)
                    put("resources", resources.toString())
                    put("polygon", polygon.toString())
                    put("neighbors", JSONArray(province.neighbors).toString())
                }

                db.insertOrThrow("provinces", null, values)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadWorld(worldId: String): WorldData? {
        val db = readableDatabase

        val payload = db.rawQuery(
            "SELECT payload FROM worlds WHERE id = ?",
            arrayOf(worldId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null

        return decodeWorld(payload)
    }

    fun listWorlds(): List<WorldSummary> {
        val result = mutableListOf<WorldSummary>()

        readableDatabase.rawQuery(
            """
            SELECT id, seed, name, kind, day
            FROM worlds
            ORDER BY updated_at DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += WorldSummary(
                    id = cursor.getString(0),
                    seed = cursor.getLong(1),
                    name = cursor.getString(2),
                    kind = WorldKind.valueOf(cursor.getString(3)),
                    day = cursor.getLong(4)
                )
            }
        }

        return result
    }

    fun deleteWorld(worldId: String): Boolean {
        val db = writableDatabase
        return db.delete("worlds", "id = ?", arrayOf(worldId)) > 0
    }

    fun saveProposal(proposal: DiplomacyProposal) {
        val terms = JSONArray().apply {
            proposal.terms.forEach { term ->
                put(
                    JSONObject()
                        .put("type", term.type)
                        .put("value", term.value)
                )
            }
        }

        val values = ContentValues().apply {
            put("id", proposal.id)
            put("world_id", proposal.worldId)
            put("proposer_country_id", proposal.proposerCountryId)
            put("target_country_id", proposal.targetCountryId)
            put("title", proposal.title)
            put("terms", terms.toString())
            put("status", proposal.status)
            put("created_day", proposal.createdDay)
        }

        writableDatabase.insertWithOnConflict(
            "diplomacy_proposals",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadProposals(worldId: String): List<DiplomacyProposal> {
        val result = mutableListOf<DiplomacyProposal>()

        readableDatabase.rawQuery(
            """
            SELECT id, proposer_country_id, target_country_id,
                   title, terms, status, created_day
            FROM diplomacy_proposals
            WHERE world_id = ?
            ORDER BY created_day DESC
            """.trimIndent(),
            arrayOf(worldId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val termsJson = JSONArray(cursor.getString(4))
                val terms = (0 until termsJson.length()).map { index ->
                    val item = termsJson.getJSONObject(index)
                    TreatyTerm(
                        type = item.getString("type"),
                        value = item.getString("value")
                    )
                }

                result += DiplomacyProposal(
                    id = cursor.getString(0),
                    worldId = worldId,
                    proposerCountryId = cursor.getString(1),
                    targetCountryId = cursor.getString(2),
                    title = cursor.getString(3),
                    terms = terms,
                    status = cursor.getString(5),
                    createdDay = cursor.getLong(6)
                )
            }
        }

        return result
    }

    private fun encodeWorld(world: WorldData): String {
        val countries = JSONArray().apply {
            world.countries.forEach { country ->
                put(
                    JSONObject()
                        .put("id", country.id)
                        .put("name", country.name)
                        .put("color", country.color)
                        .put("manpower", country.manpower)
                        .put("politicalPower", country.politicalPower)
                        .put("treasury", country.treasury)
                )
            }
        }

        val regions = JSONArray().apply {
            world.regions.forEach { region ->
                put(
                    JSONObject()
                        .put("id", region.id)
                        .put("name", region.name)
                        .put("countryId", region.countryId)
                        .put("provinceIds", JSONArray(region.provinceIds))
                        .put("capitalProvinceId", region.capitalProvinceId ?: JSONObject.NULL)
                )
            }
        }

        val provinces = JSONArray().apply {
            world.provinces.forEach { province ->
                val resources = JSONObject()
                province.resources.forEach { (key, value) ->
                    resources.put(key, value)
                }

                val polygon = JSONArray().apply {
                    province.polygon.forEach { point ->
                        put(JSONObject().put("x", point.x).put("y", point.y))
                    }
                }

                put(
                    JSONObject()
                        .put("id", province.id)
                        .put("name", province.name)
                        .put("countryId", province.countryId)
                        .put("regionId", province.regionId)
                        .put("terrain", province.terrain)
                        .put("population", province.population)
                        .put("resources", resources)
                        .put("polygon", polygon)
                        .put("neighbors", JSONArray(province.neighbors))
                )
            }
        }

        return JSONObject()
            .put("id", world.id)
            .put("seed", world.seed)
            .put("name", world.name)
            .put("kind", world.kind.name)
            .put("day", world.day)
            .put("countries", countries)
            .put("regions", regions)
            .put("provinces", provinces)
            .toString()
    }

    private fun decodeWorld(json: String): WorldData {
        val root = JSONObject(json)

        val countriesJson = root.getJSONArray("countries")
        val countries = (0 until countriesJson.length()).map { index ->
            val item = countriesJson.getJSONObject(index)
            WorldCountry(
                id = item.getString("id"),
                name = item.getString("name"),
                color = item.getLong("color"),
                manpower = item.getLong("manpower"),
                politicalPower = item.getInt("politicalPower"),
                treasury = item.getLong("treasury")
            )
        }

        val regionsJson = root.getJSONArray("regions")
        val regions = (0 until regionsJson.length()).map { index ->
            val item = regionsJson.getJSONObject(index)
            val ids = item.getJSONArray("provinceIds")
            val capital = item.opt("capitalProvinceId")

            WorldRegion(
                id = item.getString("id"),
                name = item.getString("name"),
                countryId = item.getString("countryId"),
                provinceIds = (0 until ids.length()).map { ids.getInt(it) },
                capitalProvinceId =
                    if (capital == null || capital == JSONObject.NULL) null
                    else (capital as Number).toInt()
            )
        }

        val provincesJson = root.getJSONArray("provinces")
        val provinces = (0 until provincesJson.length()).map { index ->
            val item = provincesJson.getJSONObject(index)
            val resourceJson = item.getJSONObject("resources")
            val resources = resourceJson.keys().asSequence().associateWith {
                resourceJson.getInt(it)
            }

            val polygonJson = item.getJSONArray("polygon")
            val polygon = (0 until polygonJson.length()).map { pointIndex ->
                val point = polygonJson.getJSONObject(pointIndex)
                MapPoint(point.getDouble("x"), point.getDouble("y"))
            }

            val neighborsJson = item.getJSONArray("neighbors")

            WorldProvince(
                id = item.getInt("id"),
                name = item.getString("name"),
                countryId = item.getString("countryId"),
                regionId = item.getString("regionId"),
                terrain = item.getString("terrain"),
                population = item.getLong("population"),
                resources = resources,
                polygon = polygon,
                neighbors = (0 until neighborsJson.length()).map {
                    neighborsJson.getInt(it)
                }
            )
        }

        return WorldData(
            id = root.getString("id"),
            seed = root.getLong("seed"),
            name = root.getString("name"),
            kind = WorldKind.valueOf(root.getString("kind")),
            day = root.getLong("day"),
            countries = countries,
            regions = regions,
            provinces = provinces
        )
    }
}

data class WorldSummary(
    val id: String,
    val seed: Long,
    val name: String,
    val kind: WorldKind,
    val day: Long
)
