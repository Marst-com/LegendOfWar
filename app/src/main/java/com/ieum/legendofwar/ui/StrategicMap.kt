
package com.ieum.legendofwar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ieum.legendofwar.game.Country
import com.ieum.legendofwar.game.Province
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private data class Point(val x: Float, val y: Float) {
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun times(scale: Float) = Point(x * scale, y * scale)
}

@Composable
fun StrategicMap(
    provinces: List<Province>,
    countries: List<Country>,
    selectedProvinceId: Int?,
    onProvinceSelected: (Int) -> Unit,
    onProvinceCommand: ((Int) -> Unit)? = null,
    showProvinceNames: Boolean = true,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mapSize by remember { mutableStateOf(Offset.Zero) }

    val sites = remember(provinces) {
        provinces.map { province ->
            // 결정론적 변형: 같은 세계에서는 같은 지도 모양을 유지한다.
            val seed = province.id * 7919 + 104729
            val jitterX = (((seed * 17) % 101) / 100f - 0.5f) * 0.035f
            val jitterY = (((seed * 31) % 97) / 96f - 0.5f) * 0.035f

            Point(
                (province.x + jitterX).coerceIn(0.025f, 0.975f),
                (province.y + jitterY).coerceIn(0.025f, 0.975f)
            )
        }
    }

    val polygons = remember(sites) {
        sites.mapIndexed { index, site ->
            var polygon = listOf(
                Point(0f, 0f),
                Point(1f, 0f),
                Point(1f, 1f),
                Point(0f, 1f)
            )

            sites.forEachIndexed { otherIndex, other ->
                if (otherIndex != index && polygon.isNotEmpty()) {
                    // 점 P가 site에 other보다 가까운 반평면에 남도록 자른다.
                    val nx = other.x - site.x
                    val ny = other.y - site.y
                    val limit =
                        (other.x * other.x + other.y * other.y -
                         site.x * site.x - site.y * site.y) / 2f

                    polygon = clipPolygon(
                        polygon = polygon,
                        nx = nx,
                        ny = ny,
                        limit = limit
                    )
                }
            }
            polygon
        }
    }

    val provinceIndexById = remember(provinces) {
        provinces.mapIndexed { index, province -> province.id to index }.toMap()
    }

    val countryById = remember(countries) {
        countries.associateBy { it.id }
    }

    val transformModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures { centroid, panChange, zoomChange, _ ->
            val nextZoom = (zoom * zoomChange).coerceIn(0.75f, 5f)

            // 확대 중심이 화면에서 크게 튀지 않도록 보정한다.
            val ratio = nextZoom / zoom
            pan = Offset(
                x = centroid.x - (centroid.x - pan.x) * ratio + panChange.x,
                y = centroid.y - (centroid.y - pan.y) * ratio + panChange.y
            )
            zoom = nextZoom
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(transformModifier)
            .pointerInput(polygons, pan, zoom, mapSize) {
                detectTapGestures { tap ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    val worldX =
                        ((tap.x - centerX - pan.x) / zoom + centerX) / size.width
                    val worldY =
                        ((tap.y - centerY - pan.y) / zoom + centerY) / size.height

                    if (worldX !in 0f..1f || worldY !in 0f..1f) {
                        return@detectTapGestures
                    }

                    val point = Point(worldX, worldY)
                    val index = polygons.indexOfFirst { polygon ->
                        pointInPolygon(point, polygon)
                    }

                    if (index >= 0) {
                        val id = provinces[index].id
                        if (selectedProvinceId == id && onProvinceCommand != null) {
                            onProvinceCommand(id)
                        } else {
                            onProvinceSelected(id)
                        }
                    }
                }
            }
    ) {
        mapSize = Offset(size.width, size.height)

        val center = Offset(size.width / 2f, size.height / 2f)

        drawContext.transform.translate(pan.x, pan.y)
        drawContext.transform.scale(zoom, zoom, center)

        val width = size.width
        val height = size.height

        // 배경은 바다로 유지한다.
        drawRect(Color(0xFF172B32))

        // 얇은 바다 격자
        for (i in 0..20) {
            val x = width * i / 20f
            drawLine(
                Color(0x183D6665),
                Offset(x, 0f),
                Offset(x, height),
                1.dp.toPx()
            )
        }
        for (i in 0..14) {
            val y = height * i / 14f
            drawLine(
                Color(0x183D6665),
                Offset(0f, y),
                Offset(width, y),
                1.dp.toPx()
            )
        }

        // 영토 면과 지역 경계
        provinces.forEachIndexed { index, province ->
            val polygon = polygons[index]
            if (polygon.size < 3) return@forEachIndexed

            val path = Path().apply {
                polygon.forEachIndexed { i, point ->
                    val x = point.x * width
                    val y = point.y * height
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }

            val baseColor = countryById[province.ownerId]?.let {
                Color(it.color.toInt())
            } ?: Color(0xFF68796B)

            drawPath(
                path,
                color = baseColor.copy(alpha = 0.94f)
            )

            // 소유 국가가 다른 이웃과 접하면 굵은 국경선을 그린다.
            val hasForeignNeighbor = province.neighbors.any { neighborId ->
                val neighbor = provinces.find { it.id == neighborId }
                neighbor != null && neighbor.ownerId != province.ownerId
            }

            drawPath(
                path,
                color = if (province.id == selectedProvinceId) {
                    Color(0xFFE5F38A)
                } else if (hasForeignNeighbor) {
                    Color(0xFF17231F)
                } else {
                    Color(0x66515F4C)
                },
                style = Stroke(
                    width = when {
                        province.id == selectedProvinceId -> 3.dp.toPx()
                        hasForeignNeighbor -> 2.dp.toPx()
                        else -> 0.8.dp.toPx()
                    }
                )
            )

            val centroid = polygon.centroid()
            val cx = centroid.x * width
            val cy = centroid.y * height

            // 도시/거점
            drawCircle(
                color = Color(0xFF18211D),
                radius = 5.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFFF0E6C6),
                radius = 2.5.dp.toPx(),
                center = Offset(cx, cy)
            )

            // 선택 지역의 중심 마커
            if (province.id == selectedProvinceId) {
                drawCircle(
                    color = Color(0xFFD7E86A),
                    radius = 11.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        drawContext.transform.scale(1f / zoom, 1f / zoom, center)
        drawContext.transform.translate(-pan.x, -pan.y)
    }
}

private fun clipPolygon(
    polygon: List<Point>,
    nx: Float,
    ny: Float,
    limit: Float
): List<Point> {
    if (polygon.isEmpty()) return emptyList()

    fun value(p: Point) = p.x * nx + p.y * ny - limit

    val output = mutableListOf<Point>()
    var previous = polygon.last()
    var previousValue = value(previous)

    for (current in polygon) {
        val currentValue = value(current)
        val previousInside = previousValue <= 0f
        val currentInside = currentValue <= 0f

        if (previousInside != currentInside) {
            val denominator = previousValue - currentValue
            if (abs(denominator) > 0.000001f) {
                val t = previousValue / denominator
                output += previous + (current - previous) * t
            }
        }

        if (currentInside) output += current

        previous = current
        previousValue = currentValue
    }

    return output
}

private fun pointInPolygon(point: Point, polygon: List<Point>): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var j = polygon.lastIndex

    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]

        val crosses = (a.y > point.y) != (b.y > point.y)
        if (crosses) {
            val x = (b.x - a.x) * (point.y - a.y) /
                (b.y - a.y) + a.x
            if (point.x < x) inside = !inside
        }
        j = i
    }

    return inside
}

private fun List<Point>.centroid(): Point {
    if (isEmpty()) return Point(0.5f, 0.5f)

    return Point(
        map { it.x }.average().toFloat(),
        map { it.y }.average().toFloat()
    )
}
