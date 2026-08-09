import math
from collections.abc import Iterable, Sequence

from .models import Point


def normalized_points_to_pixels(
    points: Iterable[Sequence[float]], width: int, height: int
) -> list[Point]:
    return [
        (round(float(point[0]) * width), round(float(point[1]) * height))
        for point in points
    ]


def point_in_polygon(point: Point, polygon: Sequence[Point]) -> bool:
    """Return true when a point is inside or on the edge of a polygon."""
    x, y = point
    inside = False
    count = len(polygon)
    for index in range(count):
        x1, y1 = polygon[index]
        x2, y2 = polygon[(index + 1) % count]
        if _point_on_segment(point, (x1, y1), (x2, y2)):
            return True
        intersects = (y1 > y) != (y2 > y)
        if intersects:
            crossing_x = (x2 - x1) * (y - y1) / (y2 - y1) + x1
            if x <= crossing_x:
                inside = not inside
    return inside


def _point_on_segment(
    point: Point, start: Point, end: Point, tolerance: float = 1.0
) -> bool:
    px, py = point
    x1, y1 = start
    x2, y2 = end
    cross = abs((px - x1) * (y2 - y1) - (py - y1) * (x2 - x1))
    if cross > tolerance * max(1.0, math.hypot(x2 - x1, y2 - y1)):
        return False
    return min(x1, x2) - tolerance <= px <= max(x1, x2) + tolerance and min(
        y1, y2
    ) - tolerance <= py <= max(y1, y2) + tolerance


def signed_line_side(point: Point, line_start: Point, line_end: Point) -> float:
    x, y = point
    x1, y1 = line_start
    x2, y2 = line_end
    return (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)


def displacement(start: Point, end: Point) -> tuple[float, float]:
    return float(end[0] - start[0]), float(end[1] - start[1])


def vector_length(vector: Sequence[float]) -> float:
    return math.hypot(float(vector[0]), float(vector[1]))


def cosine_similarity(first: Sequence[float], second: Sequence[float]) -> float:
    denominator = vector_length(first) * vector_length(second)
    if denominator == 0:
        return 1.0
    return (
        float(first[0]) * float(second[0])
        + float(first[1]) * float(second[1])
    ) / denominator
