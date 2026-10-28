
package com.ieum.legendofwar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Army
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Swords
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieum.legendofwar.game.GameCommand
import com.ieum.legendofwar.game.GameEngine
import com.ieum.legendofwar.game.GameSession
import com.ieum.legendofwar.game.Province
import com.ieum.legendofwar.game.WarStatus
import com.ieum.legendofwar.ui.StrategicMap
import kotlinx.coroutines.delay

private val ScreenBackground = Color(0xFF10151C)
private val PanelBackground = Color(0xFF19222D)
private val PanelBorder = Color(0xFF303D4C)
private val Accent = Color(0xFFE8C66A)
private val SecondaryText = Color(0xFF9CAFC2)
private val MainText = Color(0xFFEAF0F6)
private val Danger = Color(0xFFE16D6D)
private val Success = Color(0xFF73C69A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(16, 21, 28)
        window.navigationBarColor = android.graphics.Color.rgb(16, 21, 28)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBackground
                ) {
                    LegendOfWarScreen()
                }
            }
        }
    }
}

@Composable
private fun LegendOfWarScreen() {
    var session by remember {
        mutableStateOf(GameEngine.initialSession(20261029L))
    }

    var selectedProvinceId by remember {
        mutableIntStateOf(
            session.world.provinces.firstOrNull()?.id ?: 0
        )
    }

    var selectedCountryId by remember {
        mutableStateOf(session.world.countries.firstOrNull()?.id ?: "")
    }

    var gameSpeed by remember { mutableIntStateOf(1) }
    var activePanel by remember { mutableStateOf("군사") }
    var showCountrySelector by remember { mutableStateOf(false) }

    val world = session.world
    val selectedProvince = world.provinces.firstOrNull {
        it.id == selectedProvinceId
    }
    val selectedCountry = world.countries.firstOrNull {
        it.id == selectedCountryId
    }
    val selectedDivision = session.divisions.firstOrNull {
        it.id == session.selectedDivisionId
    }
    val provinceOwner = selectedProvince?.let { province ->
        world.countries.firstOrNull { it.id == province.ownerId }
    }

    LaunchedEffect(gameSpeed, world.paused) {
        while (gameSpeed > 0 && !session.world.paused) {
            delay((1800L / gameSpeed).coerceAtLeast(250L))
            session = GameEngine.dispatch(
                session,
                GameCommand.AdvanceDay
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        TopStrategyBar(
            session = session,
            selectedCountryName = selectedCountry?.name ?: "국가 미선택",
            gameSpeed = gameSpeed,
            onCountryClick = { showCountrySelector = !showCountrySelector },
            onPauseClick = {
                val newPaused = !session.world.paused
                session = GameEngine.dispatch(
                    session,
                    GameCommand.SetPaused(newPaused)
                )
                if (newPaused) gameSpeed = 0
                else if (gameSpeed == 0) gameSpeed = 1
            },
            onSpeedChange = { newSpeed ->
                gameSpeed = newSpeed
                session = GameEngine.dispatch(
                    session,
                    GameCommand.SetPaused(newSpeed == 0)
                )
            }
        )

        if (showCountrySelector) {
            CountrySelector(
                countries = world.countries,
                selectedCountryId = selectedCountryId,
                onSelect = {
                    selectedCountryId = it
                    showCountrySelector = false
                }
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 왼쪽: 주요 전략 메뉴
            NavigationPanel(
                activePanel = activePanel,
                onPanelSelected = { activePanel = it }
            )

            // 중앙: 지도 영역
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        PanelBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .background(Color(0xFF111A21))
            ) {
                MapToolbar(
                    worldName = world.worldName,
                    provinceCount = world.provinces.size,
                    onResetSelection = {
                        selectedProvinceId =
                            world.provinces.firstOrNull()?.id ?: 0
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    StrategicMap(
                        provinces = world.provinces,
                        countries = world.countries,
                        selectedProvinceId = selectedProvinceId,
                        onProvinceSelected = { provinceId ->
                            selectedProvinceId = provinceId
                        },
                        onProvinceCommand = { provinceId ->
                            val divisionId = session.selectedDivisionId
                            if (divisionId != null) {
                                session = GameEngine.dispatch(
                                    session,
                                    GameCommand.MoveDivision(
                                        divisionId = divisionId,
                                        targetProvinceId = provinceId
                                    )
                                )
                            }
                        },
                        divisions = session.divisions,
                        wars = session.world.wars,
                        selectedDivisionId = session.selectedDivisionId
                        modifier = Modifier.fillMaxSize()
                    )

                    // 지도 하단 범례
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE619222D))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendDot(Accent, "선택 영토")
                        LegendDot(Success, "우호")
                        LegendDot(Danger, "적대")
                        Text(
                            "영토를 눌러 선택",
                            color = SecondaryText,
                            fontSize = 10.sp
                        )
                    }
                }

                MapStatusBar(
                    province = selectedProvince,
                    ownerName = provinceOwner?.name ?: "미점령",
                    divisionName = selectedDivision?.name
                )
            }

            // 오른쪽: 선택한 메뉴에 따라 시스템 패널 변경
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        PanelBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .background(PanelBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activePanel) {
                    "군사" -> MilitaryPanel(
                        session = session,
                        selectedProvince = selectedProvince,
                        selectedDivisionId = session.selectedDivisionId,
                        onSelectDivision = { id ->
                            session = GameEngine.dispatch(
                                session,
                                GameCommand.SelectDivision(id)
                            )
                        },
                        onMoveToSelectedProvince = {
                            val divisionId = session.selectedDivisionId
                            val provinceId = selectedProvince?.id
                            if (divisionId != null && provinceId != null) {
                                session = GameEngine.dispatch(
                                    session,
                                    GameCommand.MoveDivision(
                                        divisionId,
                                        provinceId
                                    )
                                )
                            }
                        }
                    )

                    "외교" -> DiplomacyPanel(
                        session = session,
                        selectedCountryId = selectedCountryId,
                        selectedProvince = selectedProvince,
                        onDeclareWar = { defenderId ->
                            session = GameEngine.dispatch(
                                session,
                                GameCommand.DeclareWar(
                                    attackerId = selectedCountryId,
                                    defenderId = defenderId
                                )
                            )
                        }
                    )

                    "국가" -> CountryPanel(
                        session = session,
                        selectedCountryId = selectedCountryId,
                        onSelectCountry = { selectedCountryId = it }
                    )

                    "전쟁" -> WarPanel(session = session)

                    "정보" -> WorldInfoPanel(session = session)

                    else -> MilitaryPanel(
                        session = session,
                        selectedProvince = selectedProvince,
                        selectedDivisionId = session.selectedDivisionId,
                        onSelectDivision = { id ->
                            session = GameEngine.dispatch(
                                session,
                                GameCommand.SelectDivision(id)
                            )
                        },
                        onMoveToSelectedProvince = {
                            val divisionId = session.selectedDivisionId
                            val provinceId = selectedProvince?.id
                            if (divisionId != null && provinceId != null) {
                                session = GameEngine.dispatch(
                                    session,
                                    GameCommand.MoveDivision(
                                        divisionId,
                                        provinceId
                                    )
                                )
                            }
                        }
                    )
                }

                EventLogPanel(session = session)
            }
        }
    }
}

@Composable
private fun TopStrategyBar(
    session: GameSession,
    selectedCountryName: String,
    gameSpeed: Int,
    onCountryClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSpeedChange: (Int) -> Unit
) {
    val world = session.world
    val country = world.countries.firstOrNull {
        it.name == selectedCountryName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151D27))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.width(150.dp)) {
            Text(
                "LEGEND OF WAR",
                color = Accent,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                maxLines = 1
            )
            Text(
                "전설의 전쟁 · 전략 사령부",
                color = SecondaryText,
                fontSize = 10.sp
            )
        }

        Divider(
            modifier = Modifier
                .height(34.dp)
                .width(1.dp),
            color = PanelBorder
        )

        ResourceStat(
            icon = Icons.Default.Schedule,
            label = "날짜",
            value = "제 ${world.day}일"
        )

        ResourceStat(
            icon = Icons.Default.AccountBalance,
            label = "정치력",
            value = "${country?.politicalPower ?: 0}"
        )

        ResourceStat(
            icon = Icons.Default.Groups,
            label = "국가",
            value = "${world.countries.size}개"
        )

        ResourceStat(
            icon = Icons.Default.Swords,
            label = "전쟁",
            value = "${world.wars.count { it.status == WarStatus.ACTIVE }}개"
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onCountryClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MainText
            )
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                selectedCountryName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }

        OutlinedButton(
            onClick = onPauseClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Accent
            )
        ) {
            Icon(
                if (world.paused) Icons.Default.PlayArrow
                else Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 1, 2, 3).forEach { speed ->
                val active = gameSpeed == speed
                TextButton(
                    onClick = { onSpeedChange(speed) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (active) Accent.copy(alpha = 0.18f)
                            else Color.Transparent
                        )
                ) {
                    Text(
                        if (speed == 0) "Ⅱ" else "${speed}×",
                        color = if (active) Accent else SecondaryText,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold
                        else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(label, color = SecondaryText, fontSize = 9.sp)
            Text(
                value,
                color = MainText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CountrySelector(
    countries: List<com.ieum.legendofwar.game.Country>,
    selectedCountryId: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground)
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        countries.forEach { country ->
            val selected = country.id == selectedCountryId
            OutlinedButton(
                onClick = { onSelect(country.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (selected) Accent else MainText
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(country.color.toInt()))
                )
                Spacer(Modifier.width(6.dp))
                Text(country.name, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NavigationPanel(
    activePanel: String,
    onPanelSelected: (String) -> Unit
) {
    val menuItems = listOf(
        "군사" to Icons.Default.Army,
        "외교" to Icons.Default.Public,
        "국가" to Icons.Default.AccountBalance,
        "전쟁" to Icons.Default.Swords,
        "정보" to Icons.Default.Assessment
    )

    Column(
        modifier = Modifier
            .width(82.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBackground)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        menuItems.forEach { (title, icon) ->
            val selected = activePanel == title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) Color(0xFF344052)
                        else Color.Transparent
                    )
                    .clickable { onPanelSelected(title) }
                    .padding(vertical = 12.dp, horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = if (selected) Accent else SecondaryText,
                    modifier = Modifier.size(23.dp)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    title,
                    color = if (selected) MainText else SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold
                    else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Icon(
            Icons.Default.Security,
            contentDescription = "IEUM",
            tint = Accent,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(25.dp)
        )
        Text(
            "IEUM",
            color = SecondaryText,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun MapToolbar(
    worldName: String,
    provinceCount: Int,
    onResetSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2632))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Terrain,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                worldName,
                color = MainText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "전략 지도 · $provinceCount개 영토",
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
        TextButton(onClick = onResetSelection) {
            Text("초기 선택", color = Accent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MapStatusBar(
    province: Province?,
    ownerName: String,
    divisionName: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2632))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Flag,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            province?.name ?: "영토를 선택해",
            color = MainText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "소유국: $ownerName",
            color = SecondaryText,
            fontSize = 11.sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            divisionName?.let { "선택 사단: $it" } ?: "선택 사단 없음",
            color = if (divisionName != null) Success else SecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MilitaryPanel(
    session: GameSession,
    selectedProvince: Province?,
    selectedDivisionId: String?,
    onSelectDivision: (String) -> Unit,
    onMoveToSelectedProvince: () -> Unit
) {
    PanelHeading(
        icon = Icons.Default.Army,
        title = "군사 지휘부",
        subtitle = "사단 선택 및 이동 명령"
    )

    StatLine("보유 사단", "${session.divisions.size}개")
    StatLine("선택 영토", selectedProvince?.name ?: "없음")

    Divider(color = PanelBorder)

    Text(
        "내 사단",
        color = MainText,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )

    if (session.divisions.isEmpty()) {
        Text("배치된 사단이 없어.", color = SecondaryText, fontSize = 11.sp)
    }

    session.divisions.forEach { division ->
        val selected = division.id == selectedDivisionId
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (selected) Color(0xFF344052)
                    else Color(0xFF202B37)
                )
                .clickable { onSelectDivision(division.id) }
                .padding(9.dp)
        ) {
            Text(
                division.name,
                color = if (selected) Accent else MainText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "병력 ${division.manpower} · 조직력 ${division.organization.toInt()}%",
                color = SecondaryText,
                fontSize = 10.sp
            )
            Text(
                "현재 영토: ${division.provinceId}",
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
    }

    Button(
        onClick = onMoveToSelectedProvince,
        enabled = selectedDivisionId != null && selectedProvince != null,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color(0xFF1A2028)
        )
    ) {
        Text("선택 영토로 이동", fontSize = 11.sp)
    }

    Text(
        "현재 엔진은 인접 영토 이동 명령을 지원해. 먼 영토로 가는 자동 경로 탐색은 별도 구현이 필요해.",
        color = SecondaryText,
        fontSize = 10.sp,
        lineHeight = 15.sp
    )
}

@Composable
private fun DiplomacyPanel(
    session: GameSession,
    selectedCountryId: String,
    selectedProvince: Province?,
    onDeclareWar: (String) -> Unit
) {
    PanelHeading(
        icon = Icons.Default.Public,
        title = "외교부",
        subtitle = "국가 관계와 전쟁 결정"
    )

    Text(
        "공격 국가",
        color = SecondaryText,
        fontSize = 10.sp
    )
    Text(
        session.world.countries.firstOrNull {
            it.id == selectedCountryId
        }?.name ?: "국가 미선택",
        color = MainText,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    )

    Divider(color = PanelBorder)

    Text(
        "대상 국가",
        color = SecondaryText,
        fontSize = 10.sp
    )
    Text(
        selectedProvince?.let { province ->
            session.world.countries.firstOrNull {
                it.id == province.ownerId
            }?.name ?: "미점령 영토"
        } ?: "지도에서 영토를 선택해",
        color = MainText,
        fontSize = 12.sp
    )

    session.world.countries
        .filter { it.id != selectedCountryId }
        .forEach { defender ->
            OutlinedButton(
                onClick = { onDeclareWar(defender.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${defender.name}에 선전포고",
                    fontSize = 10.sp
                )
            }
        }

    Divider(color = PanelBorder)

    Text(
        "전쟁 준비",
        color = Accent,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
    Text(
        "선전포고 후 3일의 준비 기간이 적용되도록 게임 엔진을 구성했어.",
        color = SecondaryText,
        fontSize = 10.sp,
        lineHeight = 15.sp
    )
}

@Composable
private fun CountryPanel(
    session: GameSession,
    selectedCountryId: String,
    onSelectCountry: (String) -> Unit
) {
    PanelHeading(
        icon = Icons.Default.AccountBalance,
        title = "국가 현황",
        subtitle = "플레이 국가 및 주요 지표"
    )

    session.world.countries.forEach { country ->
        val selected = country.id == selectedCountryId

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) Color(0xFF344052)
                    else Color(0xFF202B37)
                )
                .clickable { onSelectCountry(country.id) }
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(country.color.toInt()))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    country.name,
                    color = MainText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(7.dp))
            StatLine("정치력", "${country.politicalPower}")
            StatLine("인력", country.manpower.toString())
            StatLine("공장", "${country.factories}개")
        }
    }
}

@Composable
private fun WarPanel(session: GameSession) {
    PanelHeading(
        icon = Icons.Default.Swords,
        title = "전쟁 상황",
        subtitle = "진행 중인 전쟁과 준비 상태"
    )

    if (session.world.wars.isEmpty()) {
        Text(
            "현재 등록된 전쟁이 없어.",
            color = SecondaryText,
            fontSize = 11.sp
        )
    }

    session.world.wars.forEach { war ->
        val attacker = session.world.countries.firstOrNull {
            it.id == war.attackerId
        }?.name ?: war.attackerId

        val defender = session.world.countries.firstOrNull {
            it.id == war.defenderId
        }?.name ?: war.defenderId

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202B37))
                .padding(10.dp)
        ) {
            Text(
                "$attacker vs $defender",
                color = MainText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))

            val statusText = when (war.status) {
                WarStatus.JUSTIFYING -> "명분 확보 중"
                WarStatus.PREPARING -> "개전 준비 중"
                WarStatus.ACTIVE -> "전쟁 진행 중"
                WarStatus.PEACE -> "평화 상태"
            }

            Text(
                statusText,
                color = when (war.status) {
                    WarStatus.ACTIVE -> Danger
                    WarStatus.PREPARING -> Accent
                    else -> SecondaryText
                },
                fontSize = 11.sp
            )

            Text(
                "준비 시작: ${war.justificationStartedAt}일",
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun WorldInfoPanel(session: GameSession) {
    PanelHeading(
        icon = Icons.Default.Assessment,
        title = "세계 정보",
        subtitle = "현재 시뮬레이션 상태"
    )

    StatLine("세계", session.world.worldName)
    StatLine("현재 날짜", "${session.world.day}일")
    StatLine("국가", "${session.world.countries.size}개")
    StatLine("영토", "${session.world.provinces.size}개")
    StatLine("사단", "${session.divisions.size}개")
    StatLine("전쟁", "${session.world.wars.size}개")

    Divider(color = PanelBorder)

    Text(
        "자원 현황",
        color = MainText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )

    session.world.countries.forEach { country ->
        Text(
            country.name,
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        country.resources.forEach { (resource, amount) ->
            StatLine(resource, amount.toString())
        }
    }
}

@Composable
private fun EventLogPanel(session: GameSession) {
    Divider(color = PanelBorder)

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "작전 기록",
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        val recentLogs = session.eventLog.takeLast(5).reversed()

        if (recentLogs.isEmpty()) {
            Text(
                "아직 기록된 사건이 없어.",
                color = SecondaryText,
                fontSize = 10.sp
            )
        } else {
            recentLogs.forEach { entry ->
                Text(
                    "› $entry",
                    color = SecondaryText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PanelHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                title,
                color = MainText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = SecondaryText,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = MainText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(label, color = SecondaryText, fontSize = 9.sp)
    }
}
