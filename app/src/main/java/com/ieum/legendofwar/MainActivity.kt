
package com.ieum.legendofwar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieum.legendofwar.game.Country
import com.ieum.legendofwar.game.Province
import com.ieum.legendofwar.game.WorldGenerator
import com.ieum.legendofwar.game.WorldState

private val Background = Color(0xFF111820)
private val Panel = Color(0xFF1B2632)
private val PanelLight = Color(0xFF263544)
private val Accent = Color(0xFFFFC857)
private val TextPrimary = Color(0xFFEAF0F6)
private val TextSecondary = Color(0xFF9CAEC0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Background,
                    surface = Panel,
                    onPrimary = Background,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                GameApp()
            }
        }
    }
}

@Composable
private fun GameApp() {
    var world by remember {
        mutableStateOf(WorldGenerator.generate(20261029L))
    }

    var selectedCountryId by remember {
        mutableStateOf("aurora")
    }

    var selectedProvinceId by remember {
        mutableIntStateOf(0)
    }

    var activePage by remember {
        mutableStateOf("세계 지도")
    }

    var showNewWorldDialog by remember {
        mutableStateOf(false)
    }

    var seedInput by remember {
        mutableStateOf("20261029")
    }

    if (showNewWorldDialog) {
        AlertDialog(
            onDismissRequest = { showNewWorldDialog = false },
            title = { Text("새로운 세계 생성") },
            text = {
                OutlinedTextField(
                    value = seedInput,
                    onValueChange = { seedInput = it },
                    label = { Text("세계 시드") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val seed = seedInput.toLongOrNull()
                        if (seed != null) {
                            world = WorldGenerator.generate(seed)
                            selectedCountryId = "aurora"
                            selectedProvinceId = 0
                            showNewWorldDialog = false
                        }
                    }
                ) {
                    Text("생성")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewWorldDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        NavigationRail(
            containerColor = Panel,
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(Modifier.height(12.dp))

            NavigationRailItem(
                selected = activePage == "세계 지도",
                onClick = { activePage = "세계 지도" },
                icon = { Icon(Icons.Default.Map, null) },
                label = { Text("세계") }
            )

            NavigationRailItem(
                selected = activePage == "군대",
                onClick = { activePage = "군대" },
                icon = { Icon(Icons.Default.Shield, null) },
                label = { Text("군대") }
            )

            NavigationRailItem(
                selected = activePage == "산업",
                onClick = { activePage = "산업" },
                icon = { Icon(Icons.Default.Factory, null) },
                label = { Text("산업") }
            )

            NavigationRailItem(
                selected = activePage == "외교",
                onClick = { activePage = "외교" },
                icon = { Icon(Icons.Default.Handshake, null) },
                label = { Text("외교") }
            )

            NavigationRailItem(
                selected = activePage == "연구",
                onClick = { activePage = "연구" },
                icon = { Icon(Icons.Default.AccountTree, null) },
                label = { Text("연구") }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            TopBar(
                world = world,
                onNewWorld = { showNewWorldDialog = true }
            )

            when (activePage) {
                "세계 지도" -> {
                    WorldDashboard(
                        world = world,
                        selectedCountryId = selectedCountryId,
                        selectedProvinceId = selectedProvinceId,
                        onCountrySelected = { selectedCountryId = it },
                        onProvinceSelected = { selectedProvinceId = it }
                    )
                }

                else -> {
                    PlaceholderPage(
                        title = activePage,
                        world = world
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    world: WorldState,
    onNewWorld: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "전설의 전쟁",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${world.worldName} · 시드 ${world.seed}",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "세계 제 ${world.day}일",
                color = Accent,
                fontWeight = FontWeight.Bold
            )

            Text(
                "싱글플레이 · 개발 버전",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        FilledTonalButton(onClick = onNewWorld) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text("새 세계")
        }
    }
}

@Composable
private fun WorldDashboard(
    world: WorldState,
    selectedCountryId: String,
    selectedProvinceId: Int,
    onCountrySelected: (String) -> Unit,
    onProvinceSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.7f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                world.countries.take(4).forEach { country ->
                    CountryCard(
                        country = country,
                        selected = country.id == selectedCountryId,
                        modifier = Modifier.weight(1f),
                        onClick = { onCountrySelected(country.id) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "전략 지도",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "영토를 선택해 정보를 확인해.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            "${world.provinces.size}개 영토",
                            color = Accent
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    WorldMap(
                        world = world,
                        selectedProvinceId = selectedProvinceId,
                        onProvinceSelected = onProvinceSelected,
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .width(245.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CountryDetails(
                country = world.countries.first {
                    it.id == selectedCountryId
                }
            )

            ProvinceDetails(
                province = world.provinces.first {
                    it.id == selectedProvinceId
                },
                countries = world.countries
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "세계 상황",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "현재 진행 중인 전쟁: ${world.wars.count { it.status.name == "ACTIVE" }}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        "참가 국가: ${world.countries.size}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Text(
                        "전쟁 시스템: 준비 중",
                        color = Accent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryCard(
    country: Country,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) PanelLight else Panel
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            Color(country.color),
                            RoundedCornerShape(3.dp)
                        )
                )

                Spacer(Modifier.width(6.dp))

                Text(
                    country.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Text(
                "공장 ${country.factories}",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Text(
                "인력 ${country.manpower / 1000}K",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CountryDetails(country: Country) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "국가 정보",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Text(
                country.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(color = PanelLight)

            StatRow("인력", country.manpower.toString())
            StatRow("정치력", country.politicalPower.toString())
            StatRow("공장", country.factories.toString())

            HorizontalDivider(color = PanelLight)

            Text(
                "자원 보유량",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            country.resources.forEach { (resource, amount) ->
                StatRow(resource.uppercase(), amount.toString())
            }
        }
    }
}

@Composable
private fun ProvinceDetails(
    province: Province,
    countries: List<Country>
) {
    val owner = countries.firstOrNull {
        it.id == province.ownerId
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "선택한 영토",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Text(
                province.name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Text(
                "소유국: ${owner?.name ?: "없음"}",
                fontSize = 12.sp
            )

            Text(
                "인접 영토: ${province.neighbors.size}개",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            value,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun WorldMap(
    world: WorldState,
    selectedProvinceId: Int,
    onProvinceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(
                Color(0xFF101D29),
                RoundedCornerShape(10.dp)
            )
            .clickable {
                // Initial map interaction.
                // Detailed hit testing is added in the next iteration.
                val next = (selectedProvinceId + 1) %
                    world.provinces.size
                onProvinceSelected(next)
            }
    ) {
        val columns = 8
        val rows = 6

        val cellWidth = size.width / columns
        val cellHeight = size.height / rows

        val byId = world.provinces.associateBy { it.id }

        // Draw connections first.
        world.provinces.forEach { province ->
            val startX = (province.x * 0.8f + 0.1f) * size.width
            val startY = (province.y * 0.8f + 0.1f) * size.height

            province.neighbors
                .filter { it > province.id }
                .forEach { neighborId ->
                    val neighbor = byId[neighborId] ?: return@forEach

                    val endX =
                        (neighbor.x * 0.8f + 0.1f) * size.width
                    val endY =
                        (neighbor.y * 0.8f + 0.1f) * size.height

                    drawLine(
                        color = Color(0xFF5B7185),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5f
                    )
                }
        }

        world.provinces.forEach { province ->
            val column = province.id % columns
            val row = province.id / columns

            val left = column * cellWidth + 3.dp.toPx()
            val top = row * cellHeight + 3.dp.toPx()

            val right = (column + 1) * cellWidth - 3.dp.toPx()
            val bottom = (row + 1) * cellHeight - 3.dp.toPx()

            val country = world.countries.firstOrNull {
                it.id == province.ownerId
            }

            val fillColor = country?.let {
                Color(it.color)
            } ?: Color.Gray

            drawRect(
                color = fillColor.copy(alpha = 0.78f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    right - left,
                    bottom - top
                )
            )

            if (province.id == selectedProvinceId) {
                drawRect(
                    color = Accent,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(
                        right - left,
                        bottom - top
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Draw a subtle province boundary.
            drawRect(
                color = Color(0xFF0B1420),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    right - left,
                    bottom - top
                ),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
private fun PlaceholderPage(
    title: String,
    world: WorldState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "이 시스템은 모듈별로 구현할 예정이야.",
            color = TextSecondary
        )

        when (title) {
            "군대" -> {
                Text("군대 이동 · 편제 · 보급 · 전선")
            }

            "산업" -> {
                Text("공장 · 자원 · 생산 대기열")
            }

            "외교" -> {
                Text("전쟁 정당화 · 동맹 · 거래 · 평화")
            }

            "연구" -> {
                Text("기술 연구 · 무기 개발 · 국가 중점")
            }
        }

        Text(
            "현재 세계: ${world.worldName}",
            color = Accent
        )
    }
}
