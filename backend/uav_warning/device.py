def select_device(requested: str = "auto") -> str:
    """Select CUDA, Apple MPS, or CPU without hard-coding one computer."""
    if requested and requested.lower() != "auto":
        return requested

    import torch

    if torch.cuda.is_available():
        return "cuda:0"
    if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        return "mps"
    return "cpu"
