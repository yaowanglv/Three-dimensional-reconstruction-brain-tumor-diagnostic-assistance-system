from __future__ import annotations

import json
import os
import time
import traceback
import importlib.util
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import numpy as np
from flask import Flask, jsonify, request
from flask_cors import CORS
from scipy.ndimage import binary_closing, binary_fill_holes, binary_opening, zoom


app = Flask(__name__)
CORS(app)

DEFAULT_MODALITIES = ("t1c", "t1n", "t2f", "t2w")
LABEL_STYLES = {
    1: {"name": "tumor_label_1", "color": "#ff2e3d", "opacity": 0.95},
    2: {"name": "tumor_label_2", "color": "#0dadff", "opacity": 0.88},
    3: {"name": "tumor_label_3", "color": "#ffad14", "opacity": 0.95},
    4: {"name": "tumor_label_4", "color": "#d947ff", "opacity": 0.95},
}

FACES = [
    ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
    ((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)]),
    ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
    ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]),
    ((0, 0, -1), [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)]),
    ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]),
]


def api_success(data: Any = None, message: str = "success"):
    return jsonify({"code": 0, "message": message, "data": data or {}})


def api_error(message: str, status: int = 500):
    return jsonify({"code": 1, "message": message, "data": {}}), status


def natural_key(path: Path):
    import re

    parts = re.split(r"(\d+)", path.name)
    return [int(part) if part.isdigit() else part.lower() for part in parts]


def read_metadata(case_dir: Path) -> Dict[str, Any]:
    metadata_path = case_dir / "metadata.json"
    if not metadata_path.exists():
        return {}
    try:
        return json.loads(metadata_path.read_text(encoding="utf-8"))
    except Exception:
        traceback.print_exc()
        return {}


def resolve_case_file(case_dir: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else case_dir / path


def ordered_slice_pairs(case_dir: Path, metadata: Dict[str, Any]) -> List[Tuple[Path, Path]]:
    pairs: List[Tuple[Path, Path]] = []
    for record in metadata.get("slices", []):
        image_value = record.get("image") or record.get("imagePath")
        mask_value = record.get("mask") or record.get("maskPath")
        if not image_value or not mask_value:
            continue
        image_path = resolve_case_file(case_dir, str(image_value))
        mask_path = resolve_case_file(case_dir, str(mask_value))
        if image_path.is_file() and mask_path.is_file():
            pairs.append((image_path, mask_path))
    if pairs:
        return pairs

    image_files = sorted((case_dir / "images").glob("*.npy"), key=natural_key)
    mask_files = sorted((case_dir / "masks").glob("*.npy"), key=natural_key)
    if len(image_files) != len(mask_files):
        raise RuntimeError(f"Image/mask slice count mismatch: {len(image_files)} vs {len(mask_files)}")
    return list(zip(image_files, mask_files))


def modality_index(modality: str, modalities: Sequence[str], sample_channels: Optional[int] = None) -> int:
    if modality.isdigit():
        index = int(modality)
    else:
        names = [item.lower() for item in modalities]
        alias = {"flair": "t2f", "t1": "t1n", "t2": "t2w"}
        name = alias.get(modality.lower(), modality.lower())
        if name not in names:
            if sample_channels in (None, 1):
                return 0
            raise ValueError(f"Unknown modality '{modality}'. Available: {', '.join(modalities)}")
        index = names.index(name)

    limit = sample_channels or len(modalities)
    if index < 0 or index >= limit:
        raise ValueError(f"Modality index {index} is outside 0..{limit - 1}")
    return index


def load_image_slice(path: Path, channel: int) -> np.ndarray:
    image = np.asarray(np.load(path))
    image = np.squeeze(image)
    if image.ndim == 3:
        if channel >= image.shape[-1]:
            raise ValueError(f"Channel {channel} outside image slice shape {image.shape}: {path}")
        image = image[..., channel]
    if image.ndim != 2:
        raise ValueError(f"Expected 2D or HxWxC image slice, got {image.shape}: {path}")
    return image.astype(np.float32, copy=False)


def load_mask_slice(path: Path) -> np.ndarray:
    mask = np.asarray(np.load(path))
    mask = np.squeeze(mask)
    if mask.ndim == 3:
        mask = mask[..., 0]
    if mask.ndim != 2:
        raise ValueError(f"Expected 2D mask slice, got {mask.shape}: {path}")
    return mask.astype(np.uint8, copy=False)


def rebuild_volumes(case_dir: Path, metadata: Dict[str, Any], modality: str) -> Tuple[np.ndarray, np.ndarray, str]:
    pairs = ordered_slice_pairs(case_dir, metadata)
    if not pairs:
        raise FileNotFoundError(f"No slices found under {case_dir}")

    first = np.asarray(np.load(pairs[0][0]))
    first = np.squeeze(first)
    sample_channels = first.shape[-1] if first.ndim == 3 else 1
    modalities = metadata.get("modalities") or list(DEFAULT_MODALITIES)
    channel = modality_index(modality, modalities, sample_channels)
    modality_name = modalities[channel] if channel < len(modalities) else str(channel)

    images = []
    masks = []
    for image_path, mask_path in pairs:
        images.append(load_image_slice(image_path, channel))
        masks.append(load_mask_slice(mask_path))
    return np.stack(images, axis=-1), np.stack(masks, axis=-1), modality_name


def percentile_scale_intensity(volume: np.ndarray) -> np.ndarray:
    arr = np.asarray(volume, dtype=np.float32)
    finite = np.isfinite(arr)
    if not finite.any():
        return np.zeros_like(arr, dtype=np.float32)

    valid = arr[finite]
    low, high = np.percentile(valid, [1, 99])
    if high <= low:
        low = float(valid.min())
        high = float(valid.max())
    clipped = np.clip(arr, low, high)
    if high > low:
        scaled = (clipped - low) / (high - low)
    else:
        scaled = np.zeros_like(clipped, dtype=np.float32)
    scaled[~np.isfinite(scaled)] = 0
    return scaled.astype(np.float32, copy=False)


def normalize_volume(volume: np.ndarray, mode: str) -> Tuple[np.ndarray, str]:
    if mode == "monai":
        if importlib.util.find_spec("monai") is None:
            return percentile_scale_intensity(volume), "percentile"
        try:
            from monai.transforms import ScaleIntensityRangePercentiles

            transform = ScaleIntensityRangePercentiles(
                lower=1,
                upper=99,
                b_min=0.0,
                b_max=1.0,
                clip=True,
                dtype=np.float32,
            )
            scaled = np.asarray(transform(volume.astype(np.float32)), dtype=np.float32)
            scaled[~np.isfinite(scaled)] = 0
            return scaled, "monai"
        except Exception:
            traceback.print_exc()
    return percentile_scale_intensity(volume), "percentile"


def downsample_binary(mask: np.ndarray, scale: float) -> np.ndarray:
    scale = float(scale)
    if scale >= 0.999:
        return mask.astype(bool, copy=False)
    if scale <= 0:
        raise ValueError("scale must be greater than 0")
    factors = (scale, scale, scale)
    downsampled = zoom(mask.astype(np.float32), factors, order=0)
    return downsampled > 0.5


def prepare_brain_mask(scaled_volume: np.ndarray, threshold: float, scale: float) -> np.ndarray:
    mask = scaled_volume > float(threshold)
    mask = binary_fill_holes(mask)
    mask = binary_closing(mask, iterations=2)
    mask = binary_opening(mask, iterations=1)
    return downsample_binary(mask, scale)


def prepare_label_mask(mask_volume: np.ndarray, label: int, scale: float) -> np.ndarray:
    label_mask = mask_volume == label
    if not np.any(label_mask):
        return label_mask.astype(bool, copy=False)
    label_mask = binary_closing(label_mask, iterations=1)
    return downsample_binary(label_mask, scale)


def vertex_to_position(vertex: Tuple[int, int, int], shape: Sequence[int], original_shape: Sequence[int]) -> Tuple[float, float, float]:
    scale = np.asarray(original_shape, dtype=np.float32) / np.asarray(shape, dtype=np.float32)
    center = (np.asarray(original_shape, dtype=np.float32) - 1.0) / 2.0
    point = (np.asarray(vertex, dtype=np.float32) * scale) - center
    y = -point[0]
    x = point[1]
    z = point[2]
    return float(x), float(y), float(z)


def choose_faces(faces: np.ndarray, max_faces: int, rng: np.random.Generator) -> np.ndarray:
    if len(faces) <= max_faces:
        return faces
    selected = np.sort(rng.choice(len(faces), size=max_faces, replace=False))
    return faces[selected]


def compact_mesh(vertices: List[Tuple[float, float, float]], faces: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    if len(faces) == 0:
        return np.zeros((0, 3), dtype=np.float32), np.zeros((0, 3), dtype=np.int32)

    used = np.unique(faces.reshape(-1))
    remap = np.full(len(vertices), -1, dtype=np.int32)
    remap[used] = np.arange(len(used), dtype=np.int32)
    compact_vertices = np.asarray([vertices[index] for index in used], dtype=np.float32)
    compact_faces = remap[faces].astype(np.int32, copy=False)
    return compact_vertices, compact_faces


def simplify_mesh(mesh: Dict[str, Any], max_faces: int, rng: np.random.Generator) -> Dict[str, Any]:
    if mesh["face_count"] <= max_faces:
        return mesh

    vertices = np.asarray(mesh["vertices"], dtype=np.float32).reshape(-1, 3)
    faces = np.asarray(mesh["faces"], dtype=np.int32).reshape(-1, 3)
    faces = choose_faces(faces, max_faces, rng)
    compact_vertices, compact_faces = compact_mesh(vertices.tolist(), faces)
    simplified = dict(mesh)
    simplified["vertices"] = np.round(compact_vertices.reshape(-1), decimals=2).tolist()
    simplified["faces"] = compact_faces.reshape(-1).tolist()
    simplified["vertex_count"] = int(len(compact_vertices))
    simplified["face_count"] = int(len(compact_faces))
    simplified["pre_simplify_face_count"] = int(mesh["face_count"])
    return simplified


def cap_total_faces(
    brain_mesh: Dict[str, Any],
    tumors: Dict[str, Dict[str, Any]],
    max_total_faces: int,
    rng: np.random.Generator,
) -> Tuple[Dict[str, Any], Dict[str, Dict[str, Any]]]:
    total_faces = brain_mesh["face_count"] + sum(item["face_count"] for item in tumors.values())
    if total_faces <= max_total_faces:
        return brain_mesh, tumors

    brain_target = min(brain_mesh["face_count"], max(1, int(max_total_faces * 0.4)))
    remaining = max_total_faces - brain_target
    tumor_faces = {label: item["face_count"] for label, item in tumors.items()}
    tumor_total = sum(tumor_faces.values())

    brain_mesh = simplify_mesh(brain_mesh, brain_target, rng)
    capped_tumors = {}
    for label, item in tumors.items():
        target = max(1, int(remaining * tumor_faces[label] / max(1, tumor_total)))
        capped_tumors[label] = simplify_mesh(item, target, rng)

    return brain_mesh, capped_tumors


def extract_voxel_surface_mesh(
    mask: np.ndarray,
    original_shape: Sequence[int],
    max_faces: int,
    rng: np.random.Generator,
) -> Dict[str, Any]:
    mask = np.asarray(mask, dtype=bool)
    coords = np.argwhere(mask)
    if coords.size == 0:
        return {
            "vertices": [],
            "faces": [],
            "vertex_count": 0,
            "face_count": 0,
            "source_voxels": 0,
            "unsimplified_face_count": 0,
        }

    vertex_map: Dict[Tuple[int, int, int], int] = {}
    vertices: List[Tuple[float, float, float]] = []
    faces: List[Tuple[int, int, int]] = []
    shape = mask.shape

    def is_inside(coord: Tuple[int, int, int]) -> bool:
        r, c, z = coord
        return 0 <= r < shape[0] and 0 <= c < shape[1] and 0 <= z < shape[2] and bool(mask[r, c, z])

    def vertex_index(coord: Tuple[int, int, int]) -> int:
        existing = vertex_map.get(coord)
        if existing is not None:
            return existing
        index = len(vertices)
        vertex_map[coord] = index
        vertices.append(vertex_to_position(coord, shape, original_shape))
        return index

    for r, c, z in coords:
        for neighbor_offset, corners in FACES:
            neighbor = (
                int(r + neighbor_offset[0]),
                int(c + neighbor_offset[1]),
                int(z + neighbor_offset[2]),
            )
            if is_inside(neighbor):
                continue

            corner_indices = [
                vertex_index((int(r + dr), int(c + dc), int(z + dz))) for dr, dc, dz in corners
            ]
            faces.append((corner_indices[0], corner_indices[1], corner_indices[2]))
            faces.append((corner_indices[0], corner_indices[2], corner_indices[3]))

    face_array = np.asarray(faces, dtype=np.int32)
    total_faces = int(len(face_array))
    face_array = choose_faces(face_array, max_faces, rng)
    vertex_array, face_array = compact_mesh(vertices, face_array)

    return {
        "vertices": np.round(vertex_array.reshape(-1), decimals=2).tolist(),
        "faces": face_array.reshape(-1).tolist(),
        "vertex_count": int(len(vertex_array)),
        "face_count": int(len(face_array)),
        "source_voxels": int(len(coords)),
        "unsimplified_face_count": total_faces,
    }


def get_float(payload: Dict[str, Any], *names: str, default: float) -> float:
    for name in names:
        value = payload.get(name)
        if value is not None:
            return float(value)
    return float(default)


def get_int(payload: Dict[str, Any], *names: str, default: int) -> int:
    for name in names:
        value = payload.get(name)
        if value is not None:
            return int(value)
    return int(default)


def build_mesh_data(payload: Dict[str, Any]) -> Dict[str, Any]:
    started = time.time()
    case_path = Path(str(payload.get("casePath") or payload.get("case_dir") or ""))
    normalization_mode = str(payload.get("normalizationMode") or payload.get("normalization_mode") or "monai").lower()
    modality = str(payload.get("modality") or "t1c")
    brain_threshold = get_float(payload, "brainThreshold", "brain_threshold", default=0.05)
    brain_scale = get_float(payload, "brainScale", "brain_scale", default=0.85)
    tumor_scale = get_float(payload, "tumorScale", "tumor_scale", default=1.0)
    max_total_faces = get_int(payload, "maxTotalFaces", "max_total_faces", default=300000)
    max_brain_faces = get_int(payload, "maxBrainFaces", "max_brain_faces", default=220000)
    max_tumor_faces = get_int(payload, "maxTumorFaces", "max_tumor_faces", default=240000)
    seed = get_int(payload, "seed", default=11)

    if not case_path.exists() or not case_path.is_dir():
        raise RuntimeError(f"casePath does not exist: {case_path}")

    metadata = read_metadata(case_path)
    image_volume, mask_volume, resolved_modality = rebuild_volumes(case_path, metadata, modality)
    scaled_volume, applied_normalization = normalize_volume(image_volume, normalization_mode)
    rng = np.random.default_rng(seed)

    brain_mask = prepare_brain_mask(scaled_volume, brain_threshold, brain_scale)
    brain_mesh = extract_voxel_surface_mesh(
        brain_mask,
        original_shape=image_volume.shape,
        max_faces=min(max_brain_faces, max_total_faces),
        rng=rng,
    )

    label_map = metadata.get("labelMap") or metadata.get("label_map") or {}
    tumor_labels = [int(label) for label in sorted(np.unique(mask_volume)) if int(label) > 0]
    tumors: Dict[str, Dict[str, Any]] = {}
    for label in tumor_labels:
        label_mask = prepare_label_mask(mask_volume, label, tumor_scale)
        mesh = extract_voxel_surface_mesh(
            label_mask,
            original_shape=mask_volume.shape,
            max_faces=max_tumor_faces,
            rng=rng,
        )
        style = LABEL_STYLES.get(label, {"name": f"tumor_label_{label}", "color": "#ffffff", "opacity": 0.95})
        tumors[str(label)] = {
            "label": str(label),
            "name": str(label_map.get(str(label), style["name"])),
            "color": style["color"],
            "opacity": style["opacity"],
            "voxel_count": int(np.sum(mask_volume == label)),
            **mesh,
        }

    brain_mesh, tumors = cap_total_faces(brain_mesh, tumors, max_total_faces, rng)

    elapsed_ms = round((time.time() - started) * 1000, 2)
    total_tumor_faces = sum(mesh["face_count"] for mesh in tumors.values())
    total_faces = int(brain_mesh["face_count"] + total_tumor_faces)
    return {
        "casePath": str(case_path),
        "caseId": metadata.get("case_id", case_path.name),
        "modality": resolved_modality,
        "normalizationMode": applied_normalization,
        "generatedAt": datetime.now().isoformat(),
        "stats": {
            "shape": list(mask_volume.shape),
            "slices": int(mask_volume.shape[2]),
            "labels": tumor_labels,
            "brainVertices": int(brain_mesh["vertex_count"]),
            "brainFaces": int(brain_mesh["face_count"]),
            "brainSourceVoxels": int(brain_mesh["source_voxels"]),
            "brainUnsimplifiedFaces": int(brain_mesh["unsimplified_face_count"]),
            "tumorFaces": int(total_tumor_faces),
            "totalFaces": total_faces,
            "maxTotalFaces": max_total_faces,
            "brainScale": brain_scale,
            "tumorScale": tumor_scale,
            "brainThreshold": brain_threshold,
            "processingTimeMs": elapsed_ms,
        },
        "brain": brain_mesh,
        "tumors": tumors,
    }


@app.get("/health")
@app.get("/mesh/health")
def health():
    return jsonify({
        "status": "ok",
        "message": "mesh service is running",
        "monaiAvailable": _is_module_available("monai"),
        "timestamp": datetime.now().isoformat(),
    })


def _is_module_available(name: str) -> bool:
    try:
        __import__(name)
        return True
    except Exception:
        return False


@app.post("/mesh/build")
def build_mesh():
    payload = request.get_json(silent=True) or {}
    try:
        return api_success(build_mesh_data(payload), "mesh build success")
    except Exception as exc:
        traceback.print_exc()
        return api_error(str(exc), 500)


if __name__ == "__main__":
    port = int(os.environ.get("MESH_API_PORT", "5002"))
    app.run(host="0.0.0.0", port=port, debug=False, threaded=True)
