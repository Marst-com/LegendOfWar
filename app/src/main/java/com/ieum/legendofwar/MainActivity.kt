
package com.ieum.legendofwar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ieum.legendofwar.game.Country
import com.ieum.legendofwar.game.Province
import com.ieum.legendofwar.game.WorldGenerator
import com.ieum.legendofwar.game.WorldState
import kotlinx.coroutines.delay

private val Background = Color(0xFF101820)
private val Panel = Color(0xFF19252C)
private val PanelLight = Color(0xFF24343A)
private val Accent = Color(0xFFD7E86A)
private val TextMain = Color(0xFFF0F3E8)
private val TextMuted = Color(0xFF9EADA8)
private val BorderColor = Color(0xFF34464A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 32)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Background,
                    background = Background,
                    surface = Panel,
                    onSurface = TextMain
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    StrategyGameScreen()
                }
            }
        }
    }
}

@Composable
private fun StrategyGameScreen() {
    var world by remember {
        mutableStateOf(WorldGenerator.generate(20261029L))
    }
    var selectedProvinceId by remember { mutableIntStateOf(18) }
    var gameSpeed by remember { mutableIntStateOf(1) }
    var activeTab by remember { mutableStateOf("지도") }
    var showProvincePanel by remember { mutableStateOf(false) }

    val selectedProvince = world.provinces
        .find { it.id == selectedProvinceId }

    val selectedCountry = selectedProvince?.let { province ->
        world.countries.find { it.id == province.ownerId }
    }

    LaunchedEffect(gameSpeed, world.paused) {
        while (gameSpeed > 0 && !world.paused) {
            delay((1000L / gameSpeed).coerceAtLeast(100L))
            world = world.copy(day = world.day + 1L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(8.dp)
    ) {
        TopStatusBar(
            world = world,
            gameSpeed = gameSpeed,
            onSpeedChange = { speed ->
                gameSpeed = speed
                world = world.copy(paused = speed == 0)
            }
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 지도는 화면의 주인공이다.
            Box(
                modifier = Modifier
                    .weight(4.7f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        BorderColor,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                StrategicMap(
                    provinces = world.provinces,
                    countries = world.countries,
                    selectedProvinceId = selectedProvinceId,
                    onProvinceSelected = { id ->
                        selectedProvinceId = id
                        showProvincePanel = true
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        "전략 지도",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "지역을 선택해 정보를 확인해",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MapLegend(Color(0xFF789B55), "아군")
                    MapLegend(Color(0xFFB18B58), "중립·타국")
                    MapLegend(Color(0xFFE2D9B7), "국경")
                }

                // 선택한 지역의 정보는 지도 위에 작게 표시한다.
                if (showProvincePanel && selectedProvince != null) {
                    ProvinceInfoPanel(
                        province = selectedProvince,
                        country = selectedCountry,
                        onClose = { showProvincePanel = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }

            // 보조 패널은 지도보다 훨씬 작게 유지한다.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "국가 상황",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "현재 국가",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Text(
                    world.countries.firstOrNull()?.name ?: "국가 없음",
                    color = Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                DividerLine()

                StatLine("게임 날짜", "제${world.day}일")
                StatLine("국가 수", "${world.countries.size}")
                StatLine("지역 수", "${world.provinces.size}")
                StatLine("전쟁", "${world.wars.size}개")

                Spacer(Modifier.height(4.dp))

                Text(
                    "국가 목록",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                world.countries.forEach { country ->
                    CountryRow(country)
                }

                Spacer(Modifier.weight(1f))

                Text(
                    "선택 지역",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    selectedProvince?.name ?: "지역을 선택해",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        BottomCommandBar(
            activeTab = activeTab,
            onTabSelected = { tab -> activeTab = tab }
        )
    }
}

@Composable
private fun TopStatusBar(
    world: WorldState,
    gameSpeed: Int,
    onSpeedChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                "전설의 전쟁",
                color = TextMain,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp
            )
            Text(
                "IEUM GRAND STRATEGY",
                color = Accent,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(Modifier.weight(1f))

        TopMetric("날짜", "제${world.day}일")
        TopMetric("정치력", "72")
        TopMetric("인력", "120K")
        TopMetric("공장", "12")

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 1, 2, 4).forEach { speed ->
                val label = when (speed) {
                    0 -> "Ⅱ"
                    1 -> "▶"
                    2 -> "▶▶"
                    else -> "▶▶▶"
                }

                Button(
                    onClick = { onSpeedChange(speed) },
                    modifier = Modifier.height(34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameSpeed == speed) Accent else PanelLight,
                        contentColor = if (gameSpeed == speed) Background else TextMain
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TopMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(
            value,
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StrategicMap(
    provinces: List<Province>,
    countries: List<Country>,
    selectedProvinceId: Int,
    onProvinceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val countryColors = remember(countries) {
        countries.associate { country ->
            country.id to Color(country.color.toInt())
        }
    }

    Canvas(
        modifier = modifier.pointerInput(provinces) {
            detectTapGestures { offset ->
                val columns = 8
                val rows = 6
                val cellWidth = size.width / columns
                val cellHeight = size.height / rows

                val column = (offset.x / cellWidth).toInt()
                val row = (offset.y / cellHeight).toInt()

                if (column in 0 until columns && row in 0 until rows) {
                    val id = row * columns + column
                    if (provinces.any { it.id == id }) {
                        onProvinceSelected(id)
                    }
                }
            }
        }
    ) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF263B40),
                    Color(0xFF17292E),
                    Color(0xFF203438)
                )
            )
        )

        val columns = 8
        val rows = 6
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val gap = 2.5.dp.toPx()

        // 바다의 은은한 격자: 지도 전체에만 적용한다.
        for (x in 0..columns) {
            val px = x * cellWidth
            drawLine(
                color = Color(0x183C6861),
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (y in 0..rows) {
            val py = y * cellHeight
            drawLine(
                color = Color(0x183C6861),
                start = Offset(0f, py),
                end = Offset(size.width, py),
                strokeWidth = 1.dp.toPx()
            )
        }

        provinces.forEach { province ->
            val column = province.id % columns
            val row = province.id / columns
            val left = column * cellWidth + gap
            val top = row * cellHeight + gap
            val right = (column + 1) * cellWidth - gap
            val bottom = (row + 1) * cellHeight - gap

            val ownerColor = countryColors[province.ownerId]
                ?: Color(0xFF66756A)

            val selected = province.id == selectedProvinceId

            val path = Path().apply {
                moveTo(left + cellWidth * 0.08f, top)
                lineTo(right - cellWidth * 0.12f, top + cellHeight * 0.04f)
                lineTo(right, top + cellHeight * 0.22f)
                lineTo(right - cellWidth * 0.04f, bottom - cellHeight * 0.12f)
                lineTo(right - cellWidth * 0.2f, bottom)
                lineTo(left + cellWidth * 0.12f, bottom - cellHeight * 0.03f)
                lineTo(left, top + cellHeight * 0.28f)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ownerColor.copy(alpha = 0.97f),
                        ownerColor.copy(alpha = 0.76f)
                    ),
                    startY = top,
                    endY = bottom
                )
            )

            // 선택 지역은 IEUM 특유의 라임색 테두리로 표시한다.
            drawPath(
                path = path,
                color = if (selected) Accent else Color(0xFF26362F),
                style = Stroke(
                    width = if (selected) 3.dp.toPx() else 1.1.dp.toPx()
                )
            )

            // 지역의 수도·거점 마커
            val markerX = left + cellWidth * (0.35f + (province.id % 3) * 0.12f)
            val markerY = top + cellHeight * (0.35f + (province.id % 2) * 0.12f)

            if (province.id % 4 == 0) {
                drawCircle(
                    color = Color(0x55303B2B),
                    radius = 6.dp.toPx(),
                    center = Offset(markerX, markerY + 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFF1E6C4),
                    radius = 3.dp.toPx(),
                    center = Offset(markerX, markerY)
                )
                drawCircle(
                    color = Color(0xFF35433A),
                    radius = 1.2.dp.toPx(),
                    center = Offset(markerX, markerY)
                )
            }

            // 일부 지역에 배치된 부대 표시
            if (province.id % 7 == 0) {
                val unitX = left + cellWidth * 0.68f
                val unitY = top + cellHeight * 0.66f
                drawRoundRect(
                    color = Color(0xEE172127),
                    topLeft = Offset(unitX - 8.dp.toPx(), unitY - 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        16.dp.toPx(),
                        12.dp.toPx()
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                drawLine(
                    color = Accent,
                    start = Offset(unitX - 4.dp.toPx(), unitY),
                    end = Offset(unitX + 4.dp.toPx(), unitY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 가상의 주요 강: 국가 경계와 구별되는 지도 장식
        val river = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.18f)
            cubicTo(
                size.width * 0.23f, size.height * 0.25f,
                size.width * 0.18f, size.height * 0.47f,
                size.width * 0.36f, size.height * 0.53f
            )
            cubicTo(
                size.width * 0.51f, size.height * 0.58f,
                size.width * 0.48f, size.height * 0.76f,
                size.width * 0.7f, size.height * 0.83f
            )
        }

        drawPath(
            path = river,
            color = Color(0x8868AEB0),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun ProvinceInfoPanel(
    province: Province,
    country: Country?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(width = 210.dp, height = 176.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF2142026))
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                province.name,
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onClose,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 9.dp,
                    vertical = 0.dp
                ),
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelLight,
                    contentColor = TextMain
                )
            ) {
                Text("닫기", fontSize = 10.sp)
            }
        }

        DividerLine()

        StatLine("소유 국가", country?.name ?: "미확인")
        StatLine("지역 번호", "#${province.id + 1}")
        StatLine("인접 지역", "${province.neighbors.size}곳")

        Text(
            "지역을 선택하면 상세 명령을 추가할 수 있어.",
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CountryRow(country: Country) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PanelLight.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(country.color.toInt()))
        )
        Spacer(Modifier.size(6.dp))
        Text(
            country.name,
            color = TextMain,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MapLegend(color: Color, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xD9111C21))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, color = TextMain, fontSize = 10.sp)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(
            value,
            color = TextMain,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderColor)
    )
}

@Composable
private fun BottomCommandBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        "지도" to "WORLD",
        "군대" to "ARMY",
        "외교" to "DIPLOMACY",
        "산업" to "INDUSTRY",
        "연구" to "RESEARCH",
        "중점" to "FOCUS"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tabs.forEach { (label, subtitle) ->
            val selected = activeTab == label

            Button(
                onClick = { onTabSelected(label) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                shape = RoundedCornerShape(7.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Accent else Panel,
                    contentColor = if (selected) Background else TextMuted
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        fontSize = 8.sp,
                        letterSpacing = 0.7.sp
                    )
                }
            }
        }
    }
}
