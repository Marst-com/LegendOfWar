
package com.ieum.legendofwar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.ieum.legendofwar.game.ArmyDivision
import com.ieum.legendofwar.game.Country
import com.ieum.legendofwar.game.Province
import com.ieum.legendofwar.game.War
import com.ieum.legendofwar.game.WarStatus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val Ocean = Color(0xFF101E2B)
private val OceanGrid = Color(0xFF233B4A)
private val BorderColor = Color(0xFF09131B)
private val SelectedGold = Color(0xFFFFD778)
private val FriendlyGreen = Color(0xFF8CE4AC)
private val EnemyRed = Color(0xFFFF7777)

private data class MapPoint(
    val x: Float,
    val y: Float
)

@Composable
fun StrategicMap(
    provinces: List<Province>,
    countries: List<Country>,
    selectedProvinceId: Int,
    onProvinceSelected: (Int) -> Unit,
    onProvinceCommand: (Int) -> Unit,
    modifier: Modifier = Modifier,
    divisions: List<ArmyDivision> = emptyList(),
    wars: List<War> = emptyList(),
    selectedDivisionId: String? = null
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val sites = remember(provinces) {
        normalizeSites(provinces)
    }

    val polygons = remember(sites) {
        sites.mapValues { (id, site) ->
            id to voronoiCell(site, sites.values.filter { it.id != id })
        }
    }

    val countryById = remember(countries) {
        countries.associateBy { it.id }
    }

    val provinceById = remember(provinces) {
        provinces.associateBy { it.id }
    }

    Canvas(
        modifier = modifier
            .pointerInput(provinces, zoom, pan) {
                detectTapGestures { screenPosition ->
                    val localX =
                        (screenPosition.x - pan.x) / zoom / size.width
                    val localY =
                        (screenPosition.y - pan.y) / zoom / size.height

                    val hit = polygons.entries.firstOrNull { entry ->
                        pointInPolygon(
                            MapPoint(localX, localY),
                            entry.value.second
                        )
                    }

                    if (hit != null) {
                        onProvinceSelected(hit.key)
                        onProvinceCommand(hit.key)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val nextZoom = (zoom * zoomChange).coerceIn(0.8f, 4f)

                    pan = Offset(
                        x = pan.x + panChange.x +
                            centroid.x * (zoom - nextZoom),
                        y = pan.y + panChange.y +
                            centroid.y * (zoom - nextZoom)
                    )

                    zoom = nextZoom
                }
            }
    ) {
        val w = size.width
        val h = size.height

        fun screen(p: MapPoint): Offset = Offset(
            pan.x + p.x * w * zoom,
            pan.y + p.y * h * zoom
        )

        fun drawMapPath(
            points: List<MapPoint>,
            color: Color,
            strokeWidth: Float = 1f,
            fill: Boolean = false
        ) {
            if (points.size < 3) return

            val path = Path().apply {
                val first = screen(points.first())
                moveTo(first.x, first.y)

                points.drop(1).forEach { p ->
                    val point = screen(p)
                    lineTo(point.x, point.y)
                }

                close()
            }

            if (fill) {
                drawPath(path, color)
            } else {
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        // 1. Deep-ocean background
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF172A38),
                    Ocean,
                    Color(0xFF0B1721)
                )
            )
        )

        // 2. Subtle cartographic grid
        val gridSpacing = 42f * zoom.coerceAtLeast(0.8f)
        val gridStartX = pan.x % gridSpacing
        val gridStartY = pan.y % gridSpacing

        var gx = gridStartX
        while (gx < w) {
            if (gx >= 0f) {
                drawLine(
                    OceanGrid.copy(alpha = 0.24f),
                    Offset(gx, 0f),
                    Offset(gx, h),
                    strokeWidth = 0.7f
                )
            }
            gx += gridSpacing
        }

        var gy = gridStartY
        while (gy < h) {
            if (gy >= 0f) {
                drawLine(
                    OceanGrid.copy(alpha = 0.24f),
                    Offset(0f, gy),
                    Offset(w, gy),
                    strokeWidth = 0.7f
                )
            }
            gy += gridSpacing
        }

        // 3. Province fills and irregular borders
        polygons.forEach { (provinceId, entry) ->
            val polygon = entry.second
            val province = provinceById[provinceId] ?: return@forEach
            val country = countryById[province.ownerId]

            val baseColor = country?.let {
                Color(it.color.toInt())
            } ?: Color(0xFF53616D)

            drawMapPath(
                polygon,
                baseColor.copy(alpha = 0.76f),
                fill = true
            )

            // Soft terrain-like relief lines.
            val center = sites[provinceId] ?: return@forEach
            repeat(3) { layer ->
                val radius = 0.012f + layer * 0.008f
                val contour = (0 until 28).map { index ->
                    val angle = index * (6.283185f / 28f)
                    val variation =
                        1f + 0.15f * kotlin.math.sin(
                            angle * 3f + provinceId * 0.7f
                        )

                    MapPoint(
                        (center.x + kotlin.math.cos(angle) * radius * variation)
                            .coerceIn(0f, 1f),
                        (center.y + kotlin.math.sin(angle) * radius * variation)
                            .coerceIn(0f, 1f)
                    )
                }

                drawMapPath(
                    contour,
                    Color.White.copy(alpha = 0.035f),
                    strokeWidth = 1f
                )
            }

            // Thin dark province boundaries.
            drawMapPath(
                polygon,
                BorderColor.copy(alpha = 0.94f),
                strokeWidth = 2.2f
            )

            // Highlight selected province.
            if (provinceId == selectedProvinceId) {
                drawMapPath(
                    polygon,
                    SelectedGold.copy(alpha = 0.24f),
                    strokeWidth = 9f
                )
                drawMapPath(
                    polygon,
                    SelectedGold,
                    strokeWidth = 3.2f
                )
            }
        }

        // 4. National borders: emphasize boundaries between countries.
        provinces.forEach { province ->
            val site = sites[province.id] ?: return@forEach

            province.neighbors.forEach { neighborId ->
                if (province.id >= neighborId) return@forEach

                val neighbor = provinceById[neighborId]
                    ?: return@forEach

                if (province.ownerId == neighbor.ownerId) {
                    return@forEach
                }

                val other = sites[neighborId] ?: return@forEach
                val mid = MapPoint(
                    (site.x + other.x) / 2f,
                    (site.y + other.y) / 2f
                )

                val at = screen(site)
                val bt = screen(other)
                val mt = screen(mid)

                drawLine(
                    Color(0xFF11151A).copy(alpha = 0.85f),
                    at,
                    mt,
                    strokeWidth = 4.5f
                )
                drawLine(
                    Color(0xFFE4D4A6).copy(alpha = 0.85f),
                    at,
                    mt,
                    strokeWidth = 1.5f
                )
                drawLine(
                    Color(0xFF11151A).copy(alpha = 0.85f),
                    mt,
                    bt,
                    strokeWidth = 4.5f
                )
                drawLine(
                    Color(0xFFE4D4A6).copy(alpha = 0.85f),
                    mt,
                    bt,
                    strokeWidth = 1.5f
                )
            }
        }

        // 5. Active war fronts between neighboring hostile countries.
        val activeWars = wars.filter { it.status == WarStatus.ACTIVE }

        if (activeWars.isNotEmpty()) {
            provinces.forEach { province ->
                province.neighbors.forEach { neighborId ->
                    if (province.id >= neighborId) return@forEach

                    val other = provinceById[neighborId]
                        ?: return@forEach

                    val hostile = activeWars.any { war ->
                        (province.ownerId == war.attackerId &&
                            other.ownerId == war.defenderId) ||
                        (province.ownerId == war.defenderId &&
                            other.ownerId == war.attackerId)
                    }

                    if (!hostile) return@forEach

                    val a = sites[province.id] ?: return@forEach
                    val b = sites[neighborId] ?: return@forEach

                    drawLine(
                        EnemyRed.copy(alpha = 0.9f),
                        screen(a),
                        screen(b),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.7f),
                        screen(a),
                        screen(b),
                        strokeWidth = 1f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 6. Province centers, city markers and unit counters.
        provinces.forEach { province ->
            val site = sites[province.id] ?: return@forEach
            val point = screen(site)

            if (point.x < -40f || point.x > w + 40f ||
                point.y < -40f || point.y > h + 40f
            ) {
                return@forEach
            }

            val owner = countryById[province.ownerId]
            val isSelected = province.id == selectedProvinceId

            // Province marker.
            drawCircle(
                Color(0xFF07111A).copy(alpha = 0.85f),
                radius = if (isSelected) 6f else 3.8f,
                center = point
            )

            drawCircle(
                if (isSelected) SelectedGold
                else Color(0xFFE3EAF0),
                radius = if (isSelected) 3.5f else 1.8f,
                center = point
            )

            // Native canvas text keeps labels crisp while zooming.
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = android.graphics.Color.WHITE
                    textSize = (10f * zoom).coerceIn(7f, 22f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-medium",
                        android.graphics.Typeface.BOLD
                    )
                    setShadowLayer(
                        3f,
                        0f,
                        1f,
                        android.graphics.Color.BLACK
                    )
                }

                if (zoom >= 0.95f) {
                    drawText(
                        province.name,
                        point.x,
                        point.y - 9f * zoom,
                        paint
                    )
                }

                if (owner != null && zoom >= 1.4f) {
                    paint.textSize = (8f * zoom).coerceIn(8f, 15f)
                    paint.color = android.graphics.Color.LTGRAY

                    drawText(
                        owner.name,
                        point.x,
                        point.y + 17f * zoom,
                        paint
                    )
                }
            }
        }

        // 7. Army counters, when supplied by the game session.
        divisions.forEach { division ->
            val site = sites[division.provinceId] ?: return@forEach
            val point = screen(site)

            val isSelected = division.id == selectedDivisionId
            val markerColor = if (isSelected) {
                SelectedGold
            } else {
                FriendlyGreen
            }

            val counterSize = if (isSelected) 17f else 14f

            drawRoundRect(
                color = Color(0xFF101820),
                topLeft = Offset(
                    point.x + 7f,
                    point.y + 4f
                ),
                size = Size(counterSize + 2f, counterSize + 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )

            drawRoundRect(
                color = markerColor,
                topLeft = Offset(
                    point.x + 8f,
                    point.y + 5f
                ),
                size = Size(counterSize, counterSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )

            drawLine(
                Color(0xFF14202A),
                Offset(point.x + 11f, point.y + 9f),
                Offset(point.x + counterSize + 5f, point.y + 9f),
                strokeWidth = 1.5f
            )
        }

        // 8. Map frame and corner accents.
        val frameInset = 5f
        drawRect(
            Color(0xFF7890A3).copy(alpha = 0.25f),
            topLeft = Offset(frameInset, frameInset),
            size = Size(
                max(0f, w - frameInset * 2),
                max(0f, h - frameInset * 2)
            ),
            style = Stroke(width = 1f)
        )

        val cornerLength = 18f
        val cornerColor = SelectedGold.copy(alpha = 0.7f)

        listOf(
            Offset(frameInset, frameInset),
            Offset(w - frameInset, frameInset),
            Offset(frameInset, h - frameInset),
            Offset(w - frameInset, h - frameInset)
        ).forEachIndexed { index, corner ->
            val sx = if (index % 2 == 0) 1f else -1f
            val sy = if (index < 2) 1f else -1f

            drawLine(
                cornerColor,
                corner,
                Offset(corner.x + cornerLength * sx, corner.y),
                strokeWidth = 2f
            )
            drawLine(
                cornerColor,
                corner,
                Offset(corner.x, corner.y + cornerLength * sy),
                strokeWidth = 2f
            )
        }
    }
}

private data class Site(
    val id: Int,
    val x: Float,
    val y: Float
)

private fun normalizeSites(
    provinces: List<Province>
): Map<Int, MapPoint> {
    if (provinces.isEmpty()) return emptyMap()

    val minX = provinces.minOf { it.x }
    val maxX = provinces.maxOf { it.x }
    val minY = provinces.minOf { it.y }
    val maxY = provinces.maxOf { it.y }

    val rangeX = (maxX - minX).coerceAtLeast(0.001f)
    val rangeY = (maxY - minY).coerceAtLeast(0.001f)

    return provinces.associate { province ->
        val normalizedX =
            0.06f + ((province.x - minX) / rangeX) * 0.88f
        val normalizedY =
            0.07f + ((province.y - minY) / rangeY) * 0.86f

        province.id to MapPoint(
            normalizedX,
            normalizedY
        )
    }
}

private fun voronoiCell(
    site: MapPoint,
    otherSites: Collection<MapPoint>
): List<MapPoint> {
    var polygon = mutableListOf(
        MapPoint(0f, 0f),
        MapPoint(1f, 0f),
        MapPoint(1f, 1f),
        MapPoint(0f, 1f)
    )

    for (other in otherSites) {
        if (polygon.isEmpty()) break

        val nx = 2f * (other.x - site.x)
        val ny = 2f * (other.y - site.y)
        val limit =
            other.x * other.x + other.y * other.y -
            site.x * site.x - site.y * site.y

        val output = mutableListOf<MapPoint>()

        for (i in polygon.indices) {
            val current = polygon[i]
            val next = polygon[(i + 1) % polygon.size]

            val currentValue =
                nx * current.x + ny * current.y - limit
            val nextValue =
                nx * next.x + ny * next.y - limit

            val currentInside = currentValue <= 0f
            val nextInside = nextValue <= 0f

            if (currentInside && nextInside) {
                output.add(next)
            } else if (currentInside && !nextInside) {
                output.add(
                    intersection(
                        current,
                        next,
                        currentValue,
                        nextValue
                    )
                )
            } else if (!currentInside && nextInside) {
                output.add(
                    intersection(
                        current,
                        next,
                        currentValue,
                        nextValue
                    )
                )
                output.add(next)
            }
        }

        polygon = output
    }

    return polygon
}

private fun intersection(
    a: MapPoint,
    b: MapPoint,
    valueA: Float,
    valueB: Float
): MapPoint {
    val denominator = valueA - valueB
    val t = if (abs(denominator) < 0.000001f) {
        0f
    } else {
        (valueA / denominator).coerceIn(0f, 1f)
    }

    return MapPoint(
        a.x + (b.x - a.x) * t,
        a.y + (b.y - a.y) * t
    )
}

private fun pointInPolygon(
    point: MapPoint,
    polygon: List<MapPoint>
): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var j = polygon.lastIndex

    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]

        val crosses =
            (a.y > point.y) != (b.y > point.y) &&
            point.x < (
                (b.x - a.x) * (point.y - a.y) /
                    ((b.y - a.y).takeIf { abs(it) > 0.000001f }
                        ?: 0.000001f) + a.x
                )

        if (crosses) inside = !inside
        j = i
    }

    return inside
}
