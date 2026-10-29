
package com.ieum.legendofwar.world

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * LegendOfWar 월드 데이터베이스.
 *
 * 저장 대상:
 * - worlds: 월드 메타데이터 및 전체 월드 JSON
 * - countries: 국가
 * - regions: 지역
 * - provinces: 프로빈스
 * - diplomacy_proposals: 외교 제안
 * - treaties: 체결된 조약
 *
 * 패키지:
 * com.ieum.legendofwar.world
 */
class WorldDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "legend_of_war.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_WORLDS = "worlds"
        private const val TABLE_COUNTRIES = "countries"
        private const val TABLE_REGIONS = "regions"
        private const val TABLE_PROVINCES = "provinces"
        private const val TABLE_PROPOSALS = "diplomacy_proposals"
        private const val TABLE_TREATIES = "treaties"
    }

    data class WorldSummary(
        val id: String,
        val seed: Long,
        val name: String,
        val kind: WorldKind,
        val day: Long,
        val provinceCount: Int,
        val countryCount: Int
    )

    // ---------------------------------------------------------
    // 데이터베이스 초기화
    // ---------------------------------------------------------

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createWorldTables(db)
        createDiplomacyTables(db)
        createIndexes(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createTreatiesTable(db)
        }
    }

    private fun createWorldTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE worlds (
                id TEXT PRIMARY KEY NOT NULL,
                seed INTEGER NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                day INTEGER NOT NULL DEFAULT 1,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE countries (
                id TEXT NOT NULL,
                world_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                manpower INTEGER NOT NULL,
                political_power INTEGER NOT NULL,
                treasury INTEGER NOT NULL,
                PRIMARY KEY (world_id, id),
                FOREIGN KEY (world_id)
                    REFERENCES worlds(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE regions (
                id TEXT NOT NULL,
                world_id TEXT NOT NULL,
                name TEXT NOT NULL,
                country_id TEXT NOT NULL,
                province_ids TEXT NOT NULL,
                capital_province_id INTEGER,
                PRIMARY KEY (world_id, id),
                FOREIGN KEY (world_id)
                    REFERENCES worlds(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE provinces (
                id INTEGER NOT NULL,
                world_id TEXT NOT NULL,
                name TEXT NOT NULL,
                country_id TEXT NOT NULL,
                region_id TEXT NOT NULL,
                terrain TEXT NOT NULL,
                population INTEGER NOT NULL,
                resources TEXT NOT NULL,
                polygon TEXT NOT NULL,
                neighbors TEXT NOT NULL,
                PRIMARY KEY (world_id, id),
                FOREIGN KEY (world_id)
                    REFERENCES worlds(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun createDiplomacyTables(db: SQLiteDatabase) {
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
                FOREIGN KEY (world_id)
                    REFERENCES worlds(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        createTreatiesTable(db)
    }

    private fun createTreatiesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS treaties (
                id TEXT PRIMARY KEY NOT NULL,
                world_id TEXT NOT NULL,
                country_a TEXT NOT NULL,
                country_b TEXT NOT NULL,
                type TEXT NOT NULL,
                terms TEXT NOT NULL,
                created_day INTEGER NOT NULL,
                expires_day INTEGER,
                active INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (world_id)
                    REFERENCES worlds(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_worlds_seed ON worlds(seed)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_regions_country ON regions(world_id, country_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_provinces_country ON provinces(world_id, country_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_provinces_region ON provinces(world_id, region_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_proposals_world ON diplomacy_proposals(world_id, status)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_treaties_world ON treaties(world_id, active)"
        )
    }

    // ---------------------------------------------------------
    // 월드 저장
    // ---------------------------------------------------------

    /**
     * 월드 전체를 하나의 트랜잭션으로 저장한다.
     * 기존 월드 행을 UPDATE하므로 외교 제안과 조약이 유지된다.
     */
    fun saveWorld(world: WorldData) {
        WorldSeedGenerator.validate(world)

        val db = writableDatabase
        val now = System.currentTimeMillis()

        db.beginTransaction()
        try {
            val existing = db.query(
                TABLE_WORLDS,
                arrayOf("created_at"),
                "id = ?",
                arrayOf(world.id),
                null,
                null,
                null
            )

            val createdAt = existing.use {
                if (it.moveToFirst()) it.getLong(0) else now
            }

            val values = ContentValues().apply {
                put("id", world.id)
                put("seed", world.seed)
                put("name", world.name)
                put("kind", world.kind.name)
                put("day", world.day)
                put("payload", encodeWorld(world).toString())
                put("created_at", createdAt)
                put("updated_at", now)
            }

            val updated = db.update(
                TABLE_WORLDS,
                values,
                "id = ?",
                arrayOf(world.id)
            )

            if (updated == 0) {
                db.insertOrThrow(TABLE_WORLDS, null, values)
            }

            db.delete(TABLE_COUNTRIES, "world_id = ?", arrayOf(world.id))
            db.delete(TABLE_REGIONS, "world_id = ?", arrayOf(world.id))
            db.delete(TABLE_PROVINCES, "world_id = ?", arrayOf(world.id))

            saveCountries(db, world)
            saveRegions(db, world)
            saveProvinces(db, world)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun saveCountries(db: SQLiteDatabase, world: WorldData) {
        world.countries.forEach { country ->
            val values = ContentValues().apply {
                put("id", country.id)
                put("world_id", world.id)
                put("name", country.name)
                put("color", country.color)
                put("manpower", country.manpower)
                put("political_power", country.politicalPower)
                put("treasury", country.treasury)
            }

            db.insertOrThrow(TABLE_COUNTRIES, null, values)
        }
    }

    private fun saveRegions(db: SQLiteDatabase, world: WorldData) {
        world.regions.forEach { region ->
            val values = ContentValues().apply {
                put("id", region.id)
                put("world_id", world.id)
                put("name", region.name)
                put("country_id", region.countryId)
                put(
                    "province_ids",
                    JSONArray(region.provinceIds).toString()
                )

                if (region.capitalProvinceId == null) {
                    putNull("capital_province_id")
                } else {
                    put("capital_province_id", region.capitalProvinceId)
                }
            }

            db.insertOrThrow(TABLE_REGIONS, null, values)
        }
    }

    private fun saveProvinces(db: SQLiteDatabase, world: WorldData) {
        world.provinces.forEach { province ->
            val resourcesJson = JSONObject()
            province.resources.forEach { (name, amount) ->
                resourcesJson.put(name, amount)
            }

            val polygonJson = JSONArray()
            province.polygon.forEach { point ->
                polygonJson.put(
                    JSONObject()
                        .put("x", point.x)
                        .put("y", point.y)
                )
            }

            val values = ContentValues().apply {
                put("id", province.id)
                put("world_id", world.id)
                put("name", province.name)
                put("country_id", province.countryId)
                put("region_id", province.regionId)
                put("terrain", province.terrain)
                put("population", province.population)
                put("resources", resourcesJson.toString())
                put("polygon", polygonJson.toString())
                put("neighbors", JSONArray(province.neighbors).toString())
            }

            db.insertOrThrow(TABLE_PROVINCES, null, values)
        }
    }

    // ---------------------------------------------------------
    // 월드 불러오기
    // ---------------------------------------------------------

    fun loadWorld(worldId: String): WorldData? {
        val db = readableDatabase

        db.query(
            TABLE_WORLDS,
            arrayOf("payload"),
            "id = ?",
            arrayOf(worldId),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null

            return decodeWorld(cursor.getString(0))
        }
    }

    fun listWorlds(): List<WorldSummary> {
        val result = mutableListOf<WorldSummary>()

        readableDatabase.query(
            TABLE_WORLDS,
            arrayOf("id", "seed", "name", "kind", "day", "payload"),
            null,
            null,
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = decodeWorld(cursor.getString(5))

                result += WorldSummary(
                    id = cursor.getString(0),
                    seed = cursor.getLong(1),
                    name = cursor.getString(2),
                    kind = WorldKind.valueOf(cursor.getString(3)),
                    day = cursor.getLong(4),
                    provinceCount = payload.provinces.size,
                    countryCount = payload.countries.size
                )
            }
        }

        return result
    }

    fun deleteWorld(worldId: String): Boolean {
        return writableDatabase.delete(
            TABLE_WORLDS,
            "id = ?",
            arrayOf(worldId)
        ) > 0
    }

    // ---------------------------------------------------------
    // 외교 제안
    // ---------------------------------------------------------

    fun saveProposal(proposal: DiplomacyProposal) {
        require(proposal.id.isNotBlank()) { "제안 ID가 비어 있어." }
        require(proposal.worldId.isNotBlank()) { "월드 ID가 비어 있어." }

        val values = ContentValues().apply {
            put("id", proposal.id)
            put("world_id", proposal.worldId)
            put("proposer_country_id", proposal.proposerCountryId)
            put("target_country_id", proposal.targetCountryId)
            put("title", proposal.title)
            put("terms", encodeTerms(proposal.terms).toString())
            put("status", proposal.status)
            put("created_day", proposal.createdDay)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_PROPOSALS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadProposals(worldId: String): List<DiplomacyProposal> {
        val result = mutableListOf<DiplomacyProposal>()

        readableDatabase.query(
            TABLE_PROPOSALS,
            null,
            "world_id = ?",
            arrayOf(worldId),
            null,
            null,
            "created_day DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursorToProposal(cursor)
            }
        }

        return result
    }

    fun loadProposal(proposalId: String): DiplomacyProposal? {
        readableDatabase.query(
            TABLE_PROPOSALS,
            null,
            "id = ?",
            arrayOf(proposalId),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursorToProposal(cursor)
            } else {
                null
            }
        }
    }

    fun updateProposalStatus(id: String, status: String): Boolean {
        require(
            status in setOf(
                "PENDING",
                "ACCEPTED",
                "REJECTED",
                "COUNTERED"
            )
        ) {
            "허용되지 않는 외교 제안 상태: $status"
        }

        val values = ContentValues().apply {
            put("status", status)
        }

        return writableDatabase.update(
            TABLE_PROPOSALS,
            values,
            "id = ?",
            arrayOf(id)
        ) > 0
    }

    fun deleteProposal(proposalId: String): Boolean {
        return writableDatabase.delete(
            TABLE_PROPOSALS,
            "id = ?",
            arrayOf(proposalId)
        ) > 0
    }

    private fun cursorToProposal(cursor: Cursor): DiplomacyProposal {
        return DiplomacyProposal(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            worldId = cursor.getString(
                cursor.getColumnIndexOrThrow("world_id")
            ),
            proposerCountryId = cursor.getString(
                cursor.getColumnIndexOrThrow("proposer_country_id")
            ),
            targetCountryId = cursor.getString(
                cursor.getColumnIndexOrThrow("target_country_id")
            ),
            title = cursor.getString(
                cursor.getColumnIndexOrThrow("title")
            ),
            terms = decodeTerms(
                cursor.getString(cursor.getColumnIndexOrThrow("terms"))
            ),
            status = cursor.getString(
                cursor.getColumnIndexOrThrow("status")
            ),
            createdDay = cursor.getLong(
                cursor.getColumnIndexOrThrow("created_day")
            )
        )
    }

    // ---------------------------------------------------------
    // 조약
    // ---------------------------------------------------------

    fun saveTreaty(treaty: TreatyRecord) {
        require(treaty.id.isNotBlank()) { "조약 ID가 비어 있어." }
        require(treaty.worldId.isNotBlank()) { "월드 ID가 비어 있어." }

        val values = ContentValues().apply {
            put("id", treaty.id)
            put("world_id", treaty.worldId)
            put("country_a", treaty.countryA)
            put("country_b", treaty.countryB)
            put("type", treaty.type)
            put("terms", encodeTerms(treaty.terms).toString())
            put("created_day", treaty.createdDay)

            if (treaty.expiresDay == null) {
                putNull("expires_day")
            } else {
                put("expires_day", treaty.expiresDay)
            }

            put("active", if (treaty.active) 1 else 0)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_TREATIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadTreaties(
        worldId: String,
        activeOnly: Boolean = false
    ): List<TreatyRecord> {
        val result = mutableListOf<TreatyRecord>()
        val selection = if (activeOnly) {
            "world_id = ? AND active = 1"
        } else {
            "world_id = ?"
        }

        readableDatabase.query(
            TABLE_TREATIES,
            null,
            selection,
            arrayOf(worldId),
            null,
            null,
            "created_day DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursorToTreaty(cursor)
            }
        }

        return result
    }

    fun loadTreaty(treatyId: String): TreatyRecord? {
        readableDatabase.query(
            TABLE_TREATIES,
            null,
            "id = ?",
            arrayOf(treatyId),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursorToTreaty(cursor)
            } else {
                null
            }
        }
    }

    fun setTreatyActive(treatyId: String, active: Boolean): Boolean {
        val values = ContentValues().apply {
            put("active", if (active) 1 else 0)
        }

        return writableDatabase.update(
            TABLE_TREATIES,
            values,
            "id = ?",
            arrayOf(treatyId)
        ) > 0
    }

    private fun cursorToTreaty(cursor: Cursor): TreatyRecord {
        val expiresIndex = cursor.getColumnIndexOrThrow("expires_day")
        val expiresDay = if (cursor.isNull(expiresIndex)) {
            null
        } else {
            cursor.getLong(expiresIndex)
        }

        return TreatyRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            worldId = cursor.getString(
                cursor.getColumnIndexOrThrow("world_id")
            ),
            countryA = cursor.getString(
                cursor.getColumnIndexOrThrow("country_a")
            ),
            countryB = cursor.getString(
                cursor.getColumnIndexOrThrow("country_b")
            ),
            type = cursor.getString(
                cursor.getColumnIndexOrThrow("type")
            ),
            terms = decodeTerms(
                cursor.getString(cursor.getColumnIndexOrThrow("terms"))
            ),
            createdDay = cursor.getLong(
                cursor.getColumnIndexOrThrow("created_day")
            ),
            expiresDay = expiresDay,
            active = cursor.getInt(
                cursor.getColumnIndexOrThrow("active")
            ) == 1
        )
    }

    // ---------------------------------------------------------
    // JSON 직렬화
    // ---------------------------------------------------------

    private fun encodeWorld(world: WorldData): JSONObject {
        val root = JSONObject()
            .put("id", world.id)
            .put("seed", world.seed)
            .put("name", world.name)
            .put("kind", world.kind.name)
            .put("day", world.day)

        val countries = JSONArray()
        world.countries.forEach { country ->
            countries.put(
                JSONObject()
                    .put("id", country.id)
                    .put("name", country.name)
                    .put("color", country.color)
                    .put("manpower", country.manpower)
                    .put("politicalPower", country.politicalPower)
                    .put("treasury", country.treasury)
            )
        }
        root.put("countries", countries)

        val regions = JSONArray()
        world.regions.forEach { region ->
            val item = JSONObject()
                .put("id", region.id)
                .put("name", region.name)
                .put("countryId", region.countryId)
                .put("provinceIds", JSONArray(region.provinceIds))

            if (region.capitalProvinceId != null) {
                item.put("capitalProvinceId", region.capitalProvinceId)
            } else {
                item.put("capitalProvinceId", JSONObject.NULL)
            }

            regions.put(item)
        }
        root.put("regions", regions)

        val provinces = JSONArray()
        world.provinces.forEach { province ->
            val resources = JSONObject()
            province.resources.forEach { (name, amount) ->
                resources.put(name, amount)
            }

            val polygon = JSONArray()
            province.polygon.forEach { point ->
                polygon.put(
                    JSONObject()
                        .put("x", point.x)
                        .put("y", point.y)
                )
            }

            provinces.put(
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
        root.put("provinces", provinces)

        return root
    }

    private fun decodeWorld(json: String): WorldData {
        val root = JSONObject(json)

        val countries = mutableListOf<WorldCountry>()
        val countryArray = root.getJSONArray("countries")

        for (i in 0 until countryArray.length()) {
            val item = countryArray.getJSONObject(i)

            countries += WorldCountry(
                id = item.getString("id"),
                name = item.getString("name"),
                color = item.getLong("color"),
                manpower = item.getLong("manpower"),
                politicalPower = item.getInt("politicalPower"),
                treasury = item.getLong("treasury")
            )
        }

        val regions = mutableListOf<WorldRegion>()
        val regionArray = root.getJSONArray("regions")

        for (i in 0 until regionArray.length()) {
            val item = regionArray.getJSONObject(i)
            val provinceIdsJson = item.getJSONArray("provinceIds")
            val provinceIds = buildList {
                for (j in 0 until provinceIdsJson.length()) {
                    add(provinceIdsJson.getInt(j))
                }
            }

            val capital = if (
                item.isNull("capitalProvinceId")
            ) {
                null
            } else {
                item.getInt("capitalProvinceId")
            }

            regions += WorldRegion(
                id = item.getString("id"),
                name = item.getString("name"),
                countryId = item.getString("countryId"),
                provinceIds = provinceIds,
                capitalProvinceId = capital
            )
        }

        val provinces = mutableListOf<WorldProvince>()
        val provinceArray = root.getJSONArray("provinces")

        for (i in 0 until provinceArray.length()) {
            val item = provinceArray.getJSONObject(i)

            val resourcesJson = item.getJSONObject("resources")
            val resources = mutableMapOf<String, Int>()
            val resourceKeys = resourcesJson.keys()

            while (resourceKeys.hasNext()) {
                val key = resourceKeys.next()
                resources[key] = resourcesJson.getInt(key)
            }

            val polygonJson = item.getJSONArray("polygon")
            val polygon = buildList {
                for (j in 0 until polygonJson.length()) {
                    val point = polygonJson.getJSONObject(j)
                    add(
                        MapPoint(
                            x = point.getDouble("x"),
                            y = point.getDouble("y")
                        )
                    )
                }
            }

            val neighborsJson = item.getJSONArray("neighbors")
            val neighbors = buildList {
                for (j in 0 until neighborsJson.length()) {
                    add(neighborsJson.getInt(j))
                }
            }

            provinces += WorldProvince(
                id = item.getInt("id"),
                name = item.getString("name"),
                countryId = item.getString("countryId"),
                regionId = item.getString("regionId"),
                terrain = item.getString("terrain"),
                population = item.getLong("population"),
                resources = resources,
                polygon = polygon,
                neighbors = neighbors
            )
        }

        return WorldData(
            id = root.getString("id"),
            seed = root.getLong("seed"),
            name = root.getString("name"),
            kind = WorldKind.valueOf(root.getString("kind")),
            day = root.optLong("day", 1L),
            provinces = provinces,
            regions = regions,
            countries = countries
        )
    }

    private fun encodeTerms(terms: List<TreatyTerm>): JSONArray {
        val result = JSONArray()

        terms.forEach { term ->
            result.put(
                JSONObject()
                    .put("type", term.type)
                    .put("value", term.value)
            )
        }

        return result
    }

    private fun decodeTerms(json: String): List<TreatyTerm> {
        val array = JSONArray(json)

        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                add(
                    TreatyTerm(
                        type = item.getString("type"),
                        value = item.getString("value")
                    )
                )
            }
        }
    }
}
