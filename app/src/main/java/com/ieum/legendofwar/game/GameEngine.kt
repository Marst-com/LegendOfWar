
package com.ieum.legendofwar.game

data class GameSession(
    val world: WorldState,
    val divisions: List<ArmyDivision> = emptyList(),
    val selectedDivisionId: String? = null,
    val eventLog: List<String> = emptyList()
)

sealed interface GameCommand {
    data class SelectProvince(val provinceId: Int) : GameCommand
    data class SelectDivision(val divisionId: String) : GameCommand
    data class MoveDivision(
        val divisionId: String,
        val targetProvinceId: Int
    ) : GameCommand
    data class DeclareWar(
        val attackerId: String,
        val defenderId: String
    ) : GameCommand
    data object AdvanceDay : GameCommand
    data class SetPaused(val paused: Boolean) : GameCommand
}

object GameEngine {

    fun initialSession(seed: Long): GameSession {
        val world = WorldGenerator.generate(seed)

        val armies = world.countries.mapIndexed { index, country ->
            val province = world.provinces.firstOrNull {
                it.ownerId == country.id
            }

            ArmyDivision(
                id = "division-${index + 1}",
                countryId = country.id,
                name = "${country.name} 제1군",
                provinceId = province?.id ?: 0,
                targetProvinceId = null,
                manpower = 10000,
                organization = 1f,
                equipment = mapOf("rifle" to 1000)
            )
        }

        return GameSession(
            world = world,
            divisions = armies,
            eventLog = listOf("새로운 세계가 생성되었습니다.")
        )
    }

    fun dispatch(
        session: GameSession,
        command: GameCommand
    ): GameSession {
        return when (command) {
            is GameCommand.SelectProvince -> {
                if (session.world.provinces.none {
                        it.id == command.provinceId
                    }) return session

                session
            }

            is GameCommand.SelectDivision -> {
                if (session.divisions.none {
                        it.id == command.divisionId
                    }) return session

                session.copy(selectedDivisionId = command.divisionId)
            }

            is GameCommand.MoveDivision -> moveDivision(
                session,
                command.divisionId,
                command.targetProvinceId
            )

            is GameCommand.DeclareWar -> declareWar(
                session,
                command.attackerId,
                command.defenderId
            )

            GameCommand.AdvanceDay -> advanceDay(session)

            is GameCommand.SetPaused -> session.copy(
                world = session.world.copy(paused = command.paused)
            )
        }
    }

    private fun moveDivision(
        session: GameSession,
        divisionId: String,
        targetProvinceId: Int
    ): GameSession {
        val division = session.divisions.find {
            it.id == divisionId
        } ?: return log(session, "부대를 찾을 수 없습니다.")

        val current = session.world.provinces.find {
            it.id == division.provinceId
        } ?: return session

        val target = session.world.provinces.find {
            it.id == targetProvinceId
        } ?: return log(session, "목적지가 존재하지 않습니다.")

        if (current.id == target.id) {
            return log(session, "부대가 이미 해당 지역에 있습니다.")
        }

        if (target.id !in current.neighbors) {
            return log(session, "인접한 지역으로만 이동 명령을 내릴 수 있습니다.")
        }

        val hostile = session.world.wars.any { war ->
            war.status == WarStatus.ACTIVE &&
                (
                    (war.attackerId == division.countryId &&
                        war.defenderId == target.ownerId) ||
                    (war.defenderId == division.countryId &&
                        war.attackerId == target.ownerId)
                )
        }

        if (target.ownerId != division.countryId && !hostile) {
            return log(session, "전쟁 중이 아닌 국가의 영토에는 진입할 수 없습니다.")
        }

        val updated = session.divisions.map {
            if (it.id == divisionId) {
                it.copy(targetProvinceId = targetProvinceId)
            } else it
        }

        return log(
            session.copy(divisions = updated),
            "${division.name}: ${current.name}에서 ${target.name}(으)로 이동 명령"
        )
    }

    private fun declareWar(
        session: GameSession,
        attackerId: String,
        defenderId: String
    ): GameSession {
        if (attackerId == defenderId) {
            return log(session, "자국에는 선전포고할 수 없습니다.")
        }

        val attacker = session.world.countries.find {
            it.id == attackerId
        } ?: return session

        val defender = session.world.countries.find {
            it.id == defenderId
        } ?: return session

        val alreadyAtWar = session.world.wars.any { war ->
            war.status != WarStatus.PEACE &&
                setOf(war.attackerId, war.defenderId) ==
                setOf(attackerId, defenderId)
        }

        if (alreadyAtWar) {
            return log(session, "이미 전쟁 또는 전쟁 준비 상태입니다.")
        }

        val war = War(
            id = "war-${session.world.day}-${session.world.wars.size + 1}",
            attackerId = attacker.id,
            defenderId = defender.id,
            justificationStartedAt = session.world.day,
            declaredAt = session.world.day,
            status = WarStatus.PREPARING
        )

        val updatedWorld = session.world.copy(
            wars = session.world.wars + war
        )

        return log(
            session.copy(world = updatedWorld),
            "${attacker.name}이(가) ${defender.name}에 선전포고했습니다. 3일간 전쟁 준비 기간이 시작됩니다."
        )
    }

    private fun advanceDay(session: GameSession): GameSession {
        if (session.world.paused) return session

        val nextDay = session.world.day + 1L

        val updatedWars = session.world.wars.map { war ->
            if (
                war.status == WarStatus.PREPARING &&
                war.declaredAt != null &&
                nextDay >= war.declaredAt + 3L
            ) {
                war.copy(status = WarStatus.ACTIVE)
            } else {
                war
            }
        }

        // 부대는 하루마다 인접한 한 지역으로 이동한다.
        val movedDivisions = session.divisions.map { division ->
            val targetId = division.targetProvinceId ?: return@map division

            val currentProvince = session.world.provinces.find {
                it.id == division.provinceId
            } ?: return@map division

            if (targetId !in currentProvince.neighbors) {
                return@map division.copy(targetProvinceId = null)
            }

            val target = session.world.provinces.find {
                it.id == targetId
            } ?: return@map division

            val warActive = updatedWars.any { war ->
                war.status == WarStatus.ACTIVE &&
                    (
                        (war.attackerId == division.countryId &&
                            war.defenderId == target.ownerId) ||
                        (war.defenderId == division.countryId &&
                            war.attackerId == target.ownerId)
                    )
            }

            if (target.ownerId != division.countryId && !warActive) {
                return@map division.copy(targetProvinceId = null)
            }

            division.copy(
                provinceId = target.id,
                targetProvinceId = null
            )
        }

        // 매일 정치력과 생산 자원을 갱신한다.
        val updatedCountries = session.world.countries.map { country ->
            country.copy(
                politicalPower = (country.politicalPower + 1)
                    .coerceAtMost(999)
            )
        }

        val updatedWorld = session.world.copy(
            day = nextDay,
            wars = updatedWars,
            countries = updatedCountries
        )

        val activatedCount = updatedWars.count { war ->
            war.status == WarStatus.ACTIVE &&
                session.world.wars.find { it.id == war.id }?.status !=
                WarStatus.ACTIVE
        }

        val nextSession = session.copy(
            world = updatedWorld,
            divisions = movedDivisions
        )

        return if (activatedCount > 0) {
            log(nextSession, "전쟁 준비 기간 종료: 전투가 가능한 상태가 되었습니다.")
        } else {
            nextSession
        }
    }

    private fun log(
        session: GameSession,
        message: String
    ): GameSession {
        return session.copy(
            eventLog = (listOf(message) + session.eventLog).take(100)
        )
    }
}
