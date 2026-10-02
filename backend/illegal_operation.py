from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


STATUS_LABELS = {
    "valid": "许可有效",
    "expired": "许可已过期",
    "suspended": "许可已暂停",
    "not_found": "本地名录未查询到",
    "unknown": "许可状态未知",
}


def normalize_plate(value: str | None) -> str | None:
    if not value:
        return None
    normalized = "".join(value.upper().split())
    return normalized or None


@dataclass(frozen=True)
class PermitRecord:
    plate_number: str
    status: str
    permit_number: str | None = None
    operating_area: str | None = None
    expires_on: str | None = None


class PermitRegistry:
    """读取可替换的本地营运许可名录；仓库默认只放演示数据。"""

    def __init__(self, records: dict[str, PermitRecord] | None = None):
        self._records = records or {}

    @classmethod
    def from_json(cls, path: Path | None) -> "PermitRegistry":
        if path is None or not path.is_file():
            return cls()
        raw = json.loads(path.read_text(encoding="utf-8"))
        records: dict[str, PermitRecord] = {}
        for item in raw.get("records", []):
            plate = normalize_plate(item.get("plateNumber"))
            if not plate:
                continue
            records[plate] = PermitRecord(
                plate_number=plate,
                status=str(item.get("status", "unknown")),
                permit_number=item.get("permitNumber"),
                operating_area=item.get("operatingArea"),
                expires_on=item.get("expiresOn"),
            )
        return cls(records)

    def lookup(self, plate_number: str | None) -> dict[str, Any]:
        plate = normalize_plate(plate_number)
        record = self._records.get(plate) if plate else None
        status = record.status if record else "not_found" if plate else "unknown"
        return {
            "plateNumber": plate,
            "status": status,
            "statusLabel": STATUS_LABELS.get(status, status),
            "record": asdict(record) if record else None,
            "source": "local_registry",
        }


def evaluate_illegal_operation_risk(
    *,
    permit_status: str = "unknown",
    repeated_pickup_count: int = 0,
    roadside_stop_seconds: float = 0,
    passenger_interaction: bool = False,
    operating_area_match: bool | None = None,
) -> dict[str, Any]:
    """把许可与行为线索组合成风险分，结果只用于人工筛查。"""

    score = 0
    evidence: list[str] = []

    if permit_status in {"expired", "suspended"}:
        score += 55
        evidence.append(STATUS_LABELS[permit_status])
    elif permit_status == "not_found":
        score += 35
        evidence.append("本地许可名录未查询到该车辆，需人工核验")

    if repeated_pickup_count >= 3:
        score += 30
        evidence.append(f"观察窗口内出现 {repeated_pickup_count} 次疑似上下客行为")
    elif repeated_pickup_count > 0:
        score += 12
        evidence.append(f"观察到 {repeated_pickup_count} 次疑似上下客行为")

    if passenger_interaction:
        score += 15
        evidence.append("车辆停留期间检测到人员接近线索")
    if roadside_stop_seconds >= 180:
        score += 15
        evidence.append(f"道路侧停留约 {round(roadside_stop_seconds)} 秒")
    elif roadside_stop_seconds >= 30:
        score += 8
        evidence.append(f"道路侧短时停留约 {round(roadside_stop_seconds)} 秒")
    if operating_area_match is False:
        score += 15
        evidence.append("观察地点与许可经营区域不一致")

    score = min(score, 100)
    risk_level = 3 if score >= 70 else 2 if score >= 40 else 1
    if not evidence:
        evidence.append("当前信息不足，未形成有效非法营运风险线索")
    return {
        "riskType": "suspected_illegal_operation",
        "riskName": "疑似非法营运",
        "riskScore": score,
        "riskLevel": risk_level,
        "riskLabel": {1: "低风险", 2: "中风险", 3: "高风险"}[risk_level],
        "evidence": evidence,
        "reviewRequired": score >= 40,
        "legalConclusion": False,
        "conclusion": (
            "存在疑似非法营运风险线索，建议结合车辆许可、驾驶员资质和现场记录人工复核。"
            if score >= 40
            else "当前线索不足以判断非法营运，可继续观察或补充许可信息。"
        ),
        "notice": "该结果仅用于巡查线索筛选，不能替代交通运输主管部门的调查与执法认定。",
    }

