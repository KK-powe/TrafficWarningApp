from copy import deepcopy
from pathlib import Path

import yaml


def _deep_merge(base: dict, override: dict) -> dict:
    merged = deepcopy(base)
    for key, value in override.items():
        if key in merged and isinstance(merged[key], dict) and isinstance(value, dict):
            merged[key] = _deep_merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def load_config(path: Path, defaults: dict) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"配置文件不存在：{path}")
    with path.open("r", encoding="utf-8") as file:
        user_config = yaml.safe_load(file) or {}
    config = _deep_merge(defaults, user_config)
    _validate(config)
    return config


def _validate(config: dict) -> None:
    model = config["model"]
    if not 0 < float(model["confidence"]) <= 1:
        raise ValueError("model.confidence 必须在0到1之间")
    if int(model["image_size"]) <= 0:
        raise ValueError("model.image_size 必须大于0")
    direction = config["rules"]["wrong_way"]["expected_direction"]
    if len(direction) != 2 or (
        float(direction[0]) == 0 and float(direction[1]) == 0
    ):
        raise ValueError("wrong_way.expected_direction 必须是非零二维向量")
    if len(config["rules"]["restricted_zone"]["polygon"]) < 3:
        raise ValueError("restricted_zone.polygon 至少需要3个点")
