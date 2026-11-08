package com.example.myapplication.tracking

fun routeSections(points: List<RoutePoint>): List<List<RoutePoint>> {
    val sections = mutableListOf<MutableList<RoutePoint>>()
    points.forEachIndexed { index, point ->
        if (index == 0 || points[index - 1].segment != point.segment) sections.add(mutableListOf())
        sections.last().add(point)
    }
    return sections
}
