from pathlib import Path

from backend.illegal_operation import PermitRegistry, evaluate_illegal_operation_risk


def test_registry_and_combined_risk_score() -> None:
    registry = PermitRegistry.from_json(
        Path(__file__).parents[1] / "data" / "permit_registry.json"
    )
    assert registry.lookup(" demo-a001 ")["status"] == "valid"
    assert registry.lookup("unknown")["status"] == "not_found"

    result = evaluate_illegal_operation_risk(
        permit_status="expired",
        repeated_pickup_count=3,
        roadside_stop_seconds=240,
        passenger_interaction=True,
        operating_area_match=False,
    )
    assert result["riskScore"] == 100
    assert result["riskLevel"] == 3
    assert result["reviewRequired"] is True
    assert result["legalConclusion"] is False
