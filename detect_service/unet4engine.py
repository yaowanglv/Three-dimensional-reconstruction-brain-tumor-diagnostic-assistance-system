from __future__ import annotations

import argparse
import base64
import gc
import io
import json
import os
import sys
import threading
import time
import traceback
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple, Union

import numpy as np
from flask import Flask, jsonify, request
from flask_cors import CORS
from PIL import Image, ImageDraw, ImageFont

try:
    import cv2
except Exception:
    cv2 = None

try:
    import torch
except Exception:
    torch = None

try:
    import tensorrt as trt
except Exception:
    trt = None

try:
    import pycuda.driver as cuda  # type: ignore
except Exception:
    cuda = None

try:
    from scipy import ndimage
except Exception:
    ndimage = None


app = Flask(__name__)
CORS(app)

VERSION = "1.1.1"
CHANNEL_MODALITIES = {
    1: ["t1c"],
    3: ["t1c", "t1n", "t2f"],
    4: ["t1c", "t1n", "t2f", "t2w"],
}
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".npy"}
MASK_LABEL_MAP = {
    0: {"name": "background", "cn": "背景", "abbr": "-"},
    1: {"name": "glioma", "cn": "胶质瘤", "abbr": "gl"},
    2: {"name": "meningioma", "cn": "脑膜瘤", "abbr": "me"},
    3: {"name": "pituitary", "cn": "垂体瘤", "abbr": "pi"},
}
MULTI_LABEL_MAP = {
    "1": "坏死肿瘤核心",
    "2": "瘤周水肿",
    "3": "增强肿瘤",
}
LABEL_COLORS = {
    1: (220, 60, 60),
    2: (60, 180, 95),
    3: (70, 105, 220),
}
SEGMENT_FILL_ALPHA = 96
JOBS: Dict[str, Dict[str, Any]] = {}
STOP_FLAGS: set[str] = set()
PYTORCH_MODEL_CACHE: Dict[Tuple[str, int, int, bool, str], Any] = {}
TENSORRT_MODEL_CACHE: Dict[Tuple[str, int, int], Any] = {}
CUDA_DEVICE = None


def log_runtime_diagnostics():
    print(f"[startup] python={sys.executable}")
    print(f"[startup] cwd={os.getcwd()}")
    print(f"[startup] torch={'ok' if torch is not None else 'missing'} cuda_available={bool(torch is not None and torch.cuda.is_available())}")
    print(f"[startup] tensorrt={'ok' if trt is not None else 'missing'}")
    print(f"[startup] pycuda={'ok' if cuda is not None else 'missing'}")


def ensure_cuda_driver():
    global CUDA_DEVICE
    if cuda is None:
        raise RuntimeError("pycuda is not installed")
    if CUDA_DEVICE is None:
        cuda.init()
        CUDA_DEVICE = cuda.Device(0)
    return CUDA_DEVICE


if torch is not None:
    class DoubleConv(torch.nn.Module):
        def __init__(self, in_channels: int, out_channels: int, mid_channels: Optional[int] = None):
            super().__init__()
            if not mid_channels:
                mid_channels = out_channels
            self.double_conv = torch.nn.Sequential(
                torch.nn.Conv2d(in_channels, mid_channels, kernel_size=3, padding=1, bias=False),
                torch.nn.BatchNorm2d(mid_channels),
                torch.nn.ReLU(inplace=True),
                torch.nn.Conv2d(mid_channels, out_channels, kernel_size=3, padding=1, bias=False),
                torch.nn.BatchNorm2d(out_channels),
                torch.nn.ReLU(inplace=True),
            )

        def forward(self, x):
            return self.double_conv(x)


    class Down(torch.nn.Module):
        def __init__(self, in_channels: int, out_channels: int):
            super().__init__()
            self.maxpool_conv = torch.nn.Sequential(torch.nn.MaxPool2d(2), DoubleConv(in_channels, out_channels))

        def forward(self, x):
            return self.maxpool_conv(x)


    class Up(torch.nn.Module):
        def __init__(self, in_channels: int, out_channels: int, bilinear: bool = True):
            super().__init__()
            if bilinear:
                self.up = torch.nn.Upsample(scale_factor=2, mode="bilinear", align_corners=True)
                self.conv = DoubleConv(in_channels, out_channels, in_channels // 2)
            else:
                self.up = torch.nn.ConvTranspose2d(in_channels, in_channels // 2, kernel_size=2, stride=2)
                self.conv = DoubleConv(in_channels, out_channels)

        def forward(self, x1, x2):
            x1 = self.up(x1)
            diff_y = x2.size()[2] - x1.size()[2]
            diff_x = x2.size()[3] - x1.size()[3]
            x1 = torch.nn.functional.pad(x1, [diff_x // 2, diff_x - diff_x // 2, diff_y // 2, diff_y - diff_y // 2])
            x = torch.cat([x2, x1], dim=1)
            return self.conv(x)


    class OutConv(torch.nn.Module):
        def __init__(self, in_channels: int, out_channels: int):
            super().__init__()
            self.conv = torch.nn.Conv2d(in_channels, out_channels, kernel_size=1)

        def forward(self, x):
            return self.conv(x)


    class UNet(torch.nn.Module):
        def __init__(self, n_channels: int, n_classes: int, bilinear: bool = True):
            super().__init__()
            self.n_channels = n_channels
            self.n_classes = n_classes
            self.bilinear = bilinear
            self.inc = DoubleConv(n_channels, 64)
            self.down1 = Down(64, 128)
            self.down2 = Down(128, 256)
            self.down3 = Down(256, 512)
            factor = 2 if bilinear else 1
            self.down4 = Down(512, 1024 // factor)
            self.up1 = Up(1024, 512 // factor, bilinear)
            self.up2 = Up(512, 256 // factor, bilinear)
            self.up3 = Up(256, 128 // factor, bilinear)
            self.up4 = Up(128, 64, bilinear)
            self.outc = OutConv(64, n_classes)

        def forward(self, x):
            x1 = self.inc(x)
            x2 = self.down1(x1)
            x3 = self.down2(x2)
            x4 = self.down3(x3)
            x5 = self.down4(x4)
            x = self.up1(x5, x4)
            x = self.up2(x, x3)
            x = self.up3(x, x2)
            x = self.up4(x, x1)
            return self.outc(x)


def api_success(data: Any = None, message: str = "success"):
    return jsonify({"code": 0, "message": message, "data": data or {}})


def api_error(message: str, status: int = 500):
    return jsonify({"code": 1, "message": message, "data": {}}), status


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def get_device() -> str:
    preferred = os.environ.get("UNET_DEVICE", "cuda")
    if preferred == "cuda" and torch is not None and torch.cuda.is_available():
        return "cuda"
    return "cpu"


def get_gpu_memory_text() -> str:
    if torch is None or not torch.cuda.is_available():
        return "unavailable"
    allocated = torch.cuda.memory_allocated() / 1024 / 1024
    reserved = torch.cuda.memory_reserved() / 1024 / 1024
    return f"{allocated:.0f}MB / {reserved:.0f}MB"


def parse_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def parse_float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def image_to_base64(image: Image.Image, image_format: str = "PNG") -> str:
    buffer = io.BytesIO()
    save_format = "JPEG" if image_format.upper() in {"JPG", "JPEG"} else "PNG"
    image.save(buffer, format=save_format)
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def natural_key(path: Path):
    import re

    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", path.name)]


def list_image_files(folder: Path) -> List[Path]:
    return sorted(
        [path for path in folder.iterdir() if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS],
        key=natural_key,
    )


def normalize_to_uint8(array: np.ndarray) -> np.ndarray:
    arr = np.asarray(array, dtype=np.float32)
    if arr.ndim == 3:
        arr = arr[..., 0]
    finite = np.isfinite(arr)
    if not finite.any():
        return np.zeros(arr.shape[:2], dtype=np.uint8)
    valid = arr[finite]
    low, high = np.percentile(valid, [1, 99])
    if high <= low:
        low, high = float(valid.min()), float(valid.max())
    if high <= low:
        return np.zeros(arr.shape[:2], dtype=np.uint8)
    arr = np.clip(arr, low, high)
    return np.clip((arr - low) / (high - low) * 255.0, 0, 255).astype(np.uint8)


def resize_array(array: np.ndarray, size: Tuple[int, int], interpolation: int) -> np.ndarray:
    width, height = size
    if cv2 is not None:
        return cv2.resize(array, (width, height), interpolation=interpolation)
    pil_mode = Image.Resampling.NEAREST if interpolation == 0 else Image.Resampling.BILINEAR
    if array.ndim == 2:
        return np.array(Image.fromarray(array).resize((width, height), pil_mode))
    channels = [np.array(Image.fromarray(array[..., index]).resize((width, height), pil_mode)) for index in range(array.shape[-1])]
    return np.stack(channels, axis=-1)


def normalize_channels_to_uint8(array: np.ndarray, channels: int) -> np.ndarray:
    arr = np.asarray(array)
    if arr.ndim == 2:
        gray = normalize_to_uint8(arr)
        if channels == 1:
            return gray
        return np.repeat(gray[..., None], channels, axis=-1)

    if arr.ndim != 3:
        raise ValueError(f"Expected 2D or HxWxC array, got {arr.shape}")

    selected = []
    for index in range(channels):
        source_index = min(index, arr.shape[-1] - 1)
        selected.append(normalize_to_uint8(arr[..., source_index]))
    if channels == 1:
        return selected[0]
    return np.stack(selected, axis=-1)


def preview_image_from_array(array: np.ndarray) -> Image.Image:
    arr = np.asarray(array)
    if arr.ndim == 2:
        return Image.fromarray(arr.astype(np.uint8), mode="L")
    if arr.ndim != 3:
        raise ValueError(f"Expected 2D or HxWxC array, got {arr.shape}")
    if arr.shape[-1] == 1:
        return Image.fromarray(arr[..., 0].astype(np.uint8), mode="L")
    return Image.fromarray(arr[..., :3].astype(np.uint8), mode="RGB")


def get_modalities(n_channels: int) -> List[str]:
    return CHANNEL_MODALITIES.get(n_channels, [f"ch{index + 1}" for index in range(n_channels)])


def load_input_slice(file_path: Path, input_size: int, n_channels: int) -> Tuple[Image.Image, np.ndarray, List[str]]:
    if file_path.suffix.lower() == ".npy":
        source = np.asarray(np.load(file_path))
        model_array = normalize_channels_to_uint8(source, n_channels)
        model_array = resize_array(model_array, (input_size, input_size), 1)
        image = preview_image_from_array(model_array)
        return image, model_array.astype(np.float32), get_modalities(n_channels)

    target_mode = "RGB" if n_channels != 1 else "L"
    image = Image.open(file_path).convert(target_mode)
    image = image.resize((input_size, input_size))
    saved_array = np.array(image).astype(np.float32)
    if n_channels not in {1, 3}:
        saved_array = normalize_channels_to_uint8(saved_array, n_channels).astype(np.float32)
    return image, saved_array, get_modalities(n_channels)


def resize_mask(mask: np.ndarray, input_size: int) -> np.ndarray:
    if mask.shape[:2] == (input_size, input_size):
        return mask.astype(np.uint8, copy=False)
    interpolation = cv2.INTER_NEAREST if cv2 is not None else 0
    return resize_array(mask.astype(np.uint8), (input_size, input_size), interpolation).astype(np.uint8)


def parse_bool(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def fallback_predict(image: Image.Image, input_size: int = 512, n_channels: int = 3) -> Tuple[np.ndarray, np.ndarray]:
    gray = np.array(image.convert("L").resize((input_size, input_size)))
    arr = gray.astype(np.float32)
    positive = arr[arr > np.percentile(arr, 5)]
    mask_small = np.zeros(arr.shape, dtype=np.uint8)
    prob_small = np.zeros(arr.shape, dtype=np.float32)
    if positive.size:
        p88, p94, p98 = np.percentile(positive, [88, 94, 98])
        h, w = arr.shape
        yy, xx = np.ogrid[:h, :w]
        center_gate = ((yy - h / 2.0) ** 2 / max((h * 0.48) ** 2, 1)) + ((xx - w / 2.0) ** 2 / max((w * 0.48) ** 2, 1)) <= 1
        mask_small[arr >= p88] = 1
        mask_small[arr >= p94] = 2
        mask_small[arr >= p98] = 3
        mask_small = np.where(center_gate, mask_small, 0).astype(np.uint8)
        prob_small = np.where(mask_small > 0, np.clip(arr / max(float(arr.max()), 1.0), 0.25, 0.99), 0.0).astype(np.float32)

    if cv2 is not None:
        mask = cv2.resize(mask_small, image.size, interpolation=cv2.INTER_NEAREST)
        probs = cv2.resize(prob_small, image.size, interpolation=cv2.INTER_LINEAR)
    else:
        mask = np.array(Image.fromarray(mask_small).resize(image.size, Image.Resampling.NEAREST))
        probs = np.array(Image.fromarray((prob_small * 255).astype(np.uint8)).resize(image.size, Image.Resampling.BILINEAR)) / 255.0
    return mask.astype(np.uint8), probs.astype(np.float32)


def _to_nchw_input(image: Union[Image.Image, np.ndarray], input_size: int, n_channels: int) -> Tuple[np.ndarray, Tuple[int, int]]:
    if isinstance(image, Image.Image):
        mode = "RGB" if n_channels != 1 else "L"
        resized = image.convert(mode).resize((input_size, input_size))
        array = np.array(resized)
        original_size = image.size
        if n_channels not in {1, 3}:
            array = normalize_channels_to_uint8(array, n_channels)
    else:
        source = np.asarray(image)
        array = normalize_channels_to_uint8(source, n_channels)
        array = resize_array(array, (input_size, input_size), cv2.INTER_LINEAR if cv2 is not None else 1)
        original_size = (int(source.shape[1]), int(source.shape[0]))
    if n_channels == 1:
        return array[None, :, :].astype(np.float32, copy=False), original_size
    return array.transpose(2, 0, 1).astype(np.float32, copy=False), original_size


class TensorRTSegmenter:
    def __init__(self, engine_path: str, n_channels: int, input_size: int):
        if trt is None:
            raise RuntimeError("TensorRT python package is not installed")
        self.cuda_device = ensure_cuda_driver()
        self.cuda_context = self.cuda_device.make_context()
        self.engine_path = str(Path(engine_path).resolve())
        self.n_channels = int(n_channels)
        self.input_size = int(input_size)
        self.logger = trt.Logger(trt.Logger.WARNING)
        try:
            with open(self.engine_path, "rb") as f:
                runtime = trt.Runtime(self.logger)
                self.engine = runtime.deserialize_cuda_engine(f.read())
            if self.engine is None:
                raise RuntimeError(f"failed to deserialize TensorRT engine: {engine_path}")
            self.context = self.engine.create_execution_context()
            if self.context is None:
                raise RuntimeError("failed to create TensorRT execution context")
            self.input_name, self.output_name = self._resolve_io_names()
            self.output_classes = self._infer_output_classes()
        finally:
            self.cuda_context.pop()

    def _resolve_io_names(self) -> Tuple[str, str]:
        if hasattr(self.engine, "num_io_tensors"):
            input_names: List[str] = []
            output_names: List[str] = []
            for index in range(self.engine.num_io_tensors):
                name = self.engine.get_tensor_name(index)
                mode = self.engine.get_tensor_mode(name)
                if mode == trt.TensorIOMode.INPUT:
                    input_names.append(name)
                elif mode == trt.TensorIOMode.OUTPUT:
                    output_names.append(name)
            if len(input_names) != 1 or not output_names:
                raise RuntimeError(f"unsupported TensorRT IO layout: inputs={input_names}, outputs={output_names}")
            return input_names[0], output_names[0]

        input_names = []
        output_names = []
        for index in range(self.engine.num_bindings):
            name = self.engine.get_binding_name(index)
            if self.engine.binding_is_input(index):
                input_names.append(name)
            else:
                output_names.append(name)
        if len(input_names) != 1 or not output_names:
            raise RuntimeError(f"unsupported TensorRT binding layout: inputs={input_names}, outputs={output_names}")
        return input_names[0], output_names[0]

    def _set_input_shape(self, batch_shape: Tuple[int, int, int, int]):
        if hasattr(self.context, "set_input_shape"):
            self.context.set_input_shape(self.input_name, batch_shape)
            return
        binding_index = self.engine.get_binding_index(self.input_name)
        self.context.set_binding_shape(binding_index, batch_shape)

    def _get_tensor_shape(self, name: str) -> Tuple[int, ...]:
        if hasattr(self.context, "get_tensor_shape"):
            return tuple(int(v) for v in self.context.get_tensor_shape(name))
        binding_index = self.engine.get_binding_index(name)
        return tuple(int(v) for v in self.context.get_binding_shape(binding_index))

    def _tensor_dtype(self, name: str):
        if hasattr(self.engine, "get_tensor_dtype"):
            return trt.nptype(self.engine.get_tensor_dtype(name))
        binding_index = self.engine.get_binding_index(name)
        return trt.nptype(self.engine.get_binding_dtype(binding_index))

    def _infer_output_classes(self) -> int:
        try:
            self.cuda_context.push()
            input_shape = (1, self.n_channels, self.input_size, self.input_size)
            self._set_input_shape(input_shape)
            output_shape = self._get_tensor_shape(self.output_name)
            if len(output_shape) >= 2 and output_shape[1] > 0:
                return int(output_shape[1])
            return 4
        finally:
            self.cuda_context.pop()

    def infer(self, image: Union[Image.Image, np.ndarray], conf: float) -> Tuple[np.ndarray, np.ndarray]:
        try:
            self.cuda_context.push()
            nchw_input, original_size = _to_nchw_input(image, self.input_size, self.n_channels)
            batched = np.expand_dims(nchw_input / 255.0, axis=0)
            batch_shape = tuple(int(v) for v in batched.shape)
            self._set_input_shape(batch_shape)

            output_shape = self._get_tensor_shape(self.output_name)
            if any(int(v) < 0 for v in output_shape):
                raise RuntimeError(f"dynamic output shape is unresolved: {output_shape}")
            output_dtype = self._tensor_dtype(self.output_name)
            output_host = np.empty(output_shape, dtype=output_dtype)

            input_host = np.ascontiguousarray(batched)
            input_device = cuda.mem_alloc(input_host.nbytes)
            output_device = cuda.mem_alloc(output_host.nbytes)
            stream = cuda.Stream()
            try:
                cuda.memcpy_htod_async(input_device, input_host, stream)
                if hasattr(self.context, "set_tensor_address"):
                    self.context.set_tensor_address(self.input_name, int(input_device))
                    self.context.set_tensor_address(self.output_name, int(output_device))
                    ok = self.context.execute_async_v3(stream.handle)
                else:
                    bindings = [0] * self.engine.num_bindings
                    bindings[self.engine.get_binding_index(self.input_name)] = int(input_device)
                    bindings[self.engine.get_binding_index(self.output_name)] = int(output_device)
                    ok = self.context.execute_async_v2(bindings=bindings, stream_handle=stream.handle)
                if not ok:
                    raise RuntimeError("TensorRT execute failed")
                cuda.memcpy_dtoh_async(output_host, output_device, stream)
                stream.synchronize()
            finally:
                input_device.free()
                output_device.free()

            output = np.asarray(output_host, dtype=np.float32)
            if output.ndim == 3:
                output = np.expand_dims(output, axis=0)
            if output.ndim != 4:
                raise RuntimeError(f"unexpected TensorRT output shape: {output.shape}")
            logits = output[0]
            resized_logits = np.stack(
                [resize_array(channel, original_size, cv2.INTER_LINEAR if cv2 is not None else 1) for channel in logits],
                axis=0,
            )
            logits_shifted = resized_logits - np.max(resized_logits, axis=0, keepdims=True)
            probs = np.exp(logits_shifted)
            probs_sum = np.sum(probs, axis=0, keepdims=True)
            probs = probs / np.clip(probs_sum, 1e-8, None)
            mask = np.argmax(probs, axis=0).astype(np.uint8)
            max_probs = np.max(probs, axis=0).astype(np.float32)
            mask[max_probs < conf] = 0
            return mask, max_probs
        finally:
            self.cuda_context.pop()


def load_tensorrt_model(model_path: str, n_channels: int, input_size: int) -> TensorRTSegmenter:
    cache_key = (str(Path(model_path).resolve()), int(n_channels), int(input_size))
    cached = TENSORRT_MODEL_CACHE.get(cache_key)
    if cached is not None:
        return cached
    segmenter = TensorRTSegmenter(model_path, n_channels, input_size)
    TENSORRT_MODEL_CACHE[cache_key] = segmenter
    return segmenter


def run_tensorrt_model(image: Union[Image.Image, np.ndarray], model_path: str, input_size: int, n_channels: int, conf: float) -> Optional[Tuple[np.ndarray, np.ndarray]]:
    model_file = Path(model_path) if model_path else None
    if not model_file or not model_file.exists():
        return None
    try:
        segmenter = load_tensorrt_model(model_path, n_channels, input_size)
        return segmenter.infer(image, conf)
    except Exception as exc:
        raise RuntimeError(f"TensorRT model load/infer failed: {exc}") from exc


def run_pytorch_model(image: Union[Image.Image, np.ndarray], model_path: str, input_size: int, n_channels: int, conf: float) -> Optional[Tuple[np.ndarray, np.ndarray]]:
    model_file = Path(model_path) if model_path else None
    if torch is None or not model_file or not model_file.exists():
        return None
    try:
        device = get_device()
        model = load_pytorch_model(model_path, n_channels, device)
        array, original_size = _to_nchw_input(image, input_size, n_channels)
        tensor = torch.from_numpy(array).float().unsqueeze(0) / 255.0
        tensor = tensor.to(device)
        with torch.no_grad():
            output = model(tensor)
            if isinstance(output, dict):
                output = output.get("out") or output.get("logits") or next(iter(output.values()))
            if isinstance(output, (tuple, list)):
                output = output[0]
            output = torch.nn.functional.interpolate(output, original_size[::-1], mode="bilinear", align_corners=False)
            probs = torch.softmax(output, dim=1)
            max_probs, mask_tensor = probs.max(dim=1)
        mask = mask_tensor.squeeze(0).cpu().numpy().astype(np.uint8)
        max_probs_np = max_probs.squeeze(0).cpu().numpy().astype(np.float32)
        mask[max_probs_np < conf] = 0
        return mask, max_probs_np
    except Exception as exc:
        raise RuntimeError(f"PyTorch model load/infer failed: {exc}") from exc


def normalize_state_dict(state_dict: Dict[str, Any]) -> Dict[str, Any]:
    normalized = {}
    for key, value in state_dict.items():
        if key == "mask_values":
            continue
        if key.startswith("module."):
            key = key[7:]
        normalized[key] = value
    return normalized


def infer_checkpoint_config(state_dict: Dict[str, Any], requested_channels: int) -> Tuple[int, int, bool]:
    inc_weight = state_dict.get("inc.double_conv.0.weight")
    if inc_weight is None:
        inc_weight = state_dict.get("module.inc.double_conv.0.weight")
    out_weight = state_dict.get("outc.conv.weight")
    if out_weight is None:
        out_weight = state_dict.get("module.outc.conv.weight")
    if inc_weight is None or out_weight is None:
        raise RuntimeError("checkpoint is missing UNet inc/outc weights")

    checkpoint_channels = int(inc_weight.shape[1])
    n_classes = int(out_weight.shape[0])
    bilinear = not any(key.endswith("up1.up.weight") for key in state_dict.keys())
    if checkpoint_channels != requested_channels:
        raise RuntimeError(
            f"model expects {checkpoint_channels} input channels, but request uses n_channels={requested_channels}. "
            f"Set nChannels/n_channels to {checkpoint_channels}."
        )
    return checkpoint_channels, n_classes, bilinear


def load_pytorch_model(model_path: str, n_channels: int, device: str):
    try:
        model = torch.jit.load(model_path, map_location=device)
        model.eval()
        cache_key = (str(Path(model_path).resolve()), n_channels, getattr(model, "n_classes", 0), bool(getattr(model, "bilinear", False)), device)
        PYTORCH_MODEL_CACHE[cache_key] = model
        return model
    except Exception:
        pass

    checkpoint = torch.load(model_path, map_location=device)
    if isinstance(checkpoint, torch.nn.Module):
        model = checkpoint
    else:
        state_dict = checkpoint
        if isinstance(checkpoint, dict):
            state_dict = checkpoint.get("state_dict") or checkpoint.get("model_state_dict") or checkpoint
        if not isinstance(state_dict, dict):
            raise RuntimeError("unsupported PyTorch checkpoint format")
        checkpoint_channels, n_classes, bilinear = infer_checkpoint_config(state_dict, n_channels)
        cache_key = (str(Path(model_path).resolve()), checkpoint_channels, n_classes, bilinear, device)
        if cache_key in PYTORCH_MODEL_CACHE:
            return PYTORCH_MODEL_CACHE[cache_key]
        model = UNet(n_channels=checkpoint_channels, n_classes=n_classes, bilinear=bilinear)
        missing, unexpected = model.load_state_dict(normalize_state_dict(state_dict), strict=False)
        unexpected = [key for key in unexpected if key != "mask_values"]
        if missing or unexpected:
            raise RuntimeError(f"checkpoint architecture mismatch, missing={missing}, unexpected={unexpected}")

    model.to(device)
    model.eval()
    if "cache_key" not in locals():
        cache_key = (str(Path(model_path).resolve()), n_channels, int(getattr(model, "n_classes", 0)), bool(getattr(model, "bilinear", False)), device)
    PYTORCH_MODEL_CACHE[cache_key] = model
    return model


def predict_single_image(image: Union[Image.Image, np.ndarray], model_path: str, conf: float, input_size: int, n_channels: int, model_format: str) -> Tuple[np.ndarray, np.ndarray, str]:
    model_format_lower = (model_format or "").lower()
    if model_format_lower in {"tensorrt", "engine", "trt"}:
        predicted = run_tensorrt_model(image, model_path, input_size, n_channels, conf)
        if predicted is not None:
            return predicted[0], predicted[1], "tensorrt"
    elif model_format_lower in {"pytorch", "pt", "pth"}:
        predicted = run_pytorch_model(image, model_path, input_size, n_channels, conf)
        if predicted is not None:
            return predicted[0], predicted[1], "pytorch"
    preview = image if isinstance(image, Image.Image) else preview_image_from_array(normalize_channels_to_uint8(np.asarray(image), n_channels))
    mask, probs = fallback_predict(preview, input_size, n_channels)
    mask[probs < conf] = 0
    return mask, probs, "fallback"


def contour_points(label_mask: np.ndarray) -> List[Tuple[np.ndarray, bool]]:
    if cv2 is None:
        ys, xs = np.where(label_mask > 0)
        if len(xs) < 3:
            return []
        return [(np.array([[[xs.min(), ys.min()]], [[xs.max(), ys.min()]], [[xs.max(), ys.max()]], [[xs.min(), ys.max()]]], dtype=np.int32), False)]
    contours, hierarchy = cv2.findContours((label_mask > 0).astype(np.uint8) * 255, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    if hierarchy is None:
        return []
    hierarchy = hierarchy[0]
    return [(contour, int(hierarchy[index][3]) >= 0) for index, contour in enumerate(contours)]


def extract_polygons_from_mask(mask: np.ndarray, probs: np.ndarray, conf_threshold: float) -> List[Dict[str, Any]]:
    height, width = mask.shape[:2]
    polygons: List[Dict[str, Any]] = []
    for label_id in range(1, 4):
        label_mask = mask == label_id
        for contour, is_hole in contour_points(label_mask.astype(np.uint8)):
            if len(contour) < 3:
                continue
            if cv2 is not None:
                epsilon = 0.005 * cv2.arcLength(contour, True)
                approx = cv2.approxPolyDP(contour, epsilon, True)
                area = float(cv2.contourArea(contour))
                x, y, w, h = cv2.boundingRect(contour)
            else:
                approx = contour
                x = int(contour[:, :, 0].min())
                y = int(contour[:, :, 1].min())
                w = int(contour[:, :, 0].max() - x)
                h = int(contour[:, :, 1].max() - y)
                area = float(label_mask.sum())
            points = approx.reshape(-1, 2).astype(int).tolist()
            if len(points) < 3:
                continue
            label_probs = probs[label_mask]
            confidence = float(label_probs.mean()) if label_probs.size else conf_threshold
            label_info = MASK_LABEL_MAP[label_id]
            polygons.append({
                "id": len(polygons) + 1,
                "points": points,
                "normalized_points": [[round(p[0] / max(width, 1), 6), round(p[1] / max(height, 1), 6)] for p in points],
                "point_count": len(points),
                "label": label_info["abbr"],
                "tumor_type": label_info["name"],
                "tumor_type_cn": label_info["cn"],
                "confidence": round(max(conf_threshold, min(confidence, 0.999)), 4),
                "area_pixels": round(area, 2),
                "box": {"x1": x, "y1": y, "x2": x + w, "y2": y + h},
                "is_hole": is_hole,
                "role": "hole" if is_hole else "mask",
            })
    return polygons


def draw_label(draw: ImageDraw.ImageDraw, x: int, y: int, text: str, color: Tuple[int, int, int]):
    font = ImageFont.load_default()
    y = max(0, y - 16)
    bbox = draw.textbbox((x, y), text, font=font)
    draw.rectangle([bbox[0] - 3, bbox[1] - 2, bbox[2] + 3, bbox[3] + 2], fill=color)
    draw.text((x, y), text, fill=(255, 255, 255), font=font)


def build_segmentation_images(image: Image.Image, mask: np.ndarray, polygons: List[Dict[str, Any]]) -> Tuple[Image.Image, Image.Image, Image.Image]:
    result = image.copy().convert("RGB")
    result_draw = ImageDraw.Draw(result)
    mask_rgb_array = np.zeros((mask.shape[0], mask.shape[1], 3), dtype=np.uint8)
    overlay_array = np.zeros((mask.shape[0], mask.shape[1], 4), dtype=np.uint8)
    for label_id, color in LABEL_COLORS.items():
        mask_rgb_array[mask == label_id] = (255, 255, 255)
        overlay_array[mask == label_id] = (*color, SEGMENT_FILL_ALPHA)
    unlabeled_mask = (mask > 0) & (overlay_array[:, :, 3] == 0)
    if np.any(unlabeled_mask):
        mask_rgb_array[unlabeled_mask] = (255, 255, 255)
        overlay_array[unlabeled_mask] = (*LABEL_COLORS.get(1, (220, 60, 60)), SEGMENT_FILL_ALPHA)
    mask_image = Image.fromarray(mask_rgb_array, mode="RGB")
    overlay_layer = Image.fromarray(overlay_array, mode="RGBA").resize(image.size)
    overlay = Image.alpha_composite(image.convert("RGBA"), overlay_layer).convert("RGB")
    overlay_draw = ImageDraw.Draw(overlay)

    for polygon in polygons:
        points = [(int(x), int(y)) for x, y in polygon["points"]]
        if len(points) < 3:
            continue
        label_id = next((key for key, val in MASK_LABEL_MAP.items() if val["name"] == polygon["tumor_type"]), 1)
        color = LABEL_COLORS.get(label_id, (128, 128, 128))
        result_draw.line(points + [points[0]], fill=color, width=3)
        overlay_draw.line(points + [points[0]], fill=color, width=3)
        if not polygon.get("is_hole"):
            draw_label(result_draw, points[0][0], points[0][1], f"{polygon['label']} {polygon['confidence']:.2f}", color)
    return result, mask_image, overlay


def update_job(job_id: str, **updates):
    job = JOBS.setdefault(job_id, {"jobId": job_id, "status": "pending", "startedAt": now_iso(), "results": []})
    job.update(updates)
    total = int(job.get("totalFiles") or 0)
    processed = int(job.get("processedFiles") or 0)
    job["progress"] = 0 if total <= 0 else round(processed * 100.0 / total)
    return job


def resolve_case_file(case_dir: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else case_dir / path


def load_case_metadata(case_dir: Path) -> Dict[str, Any]:
    metadata_path = case_dir / "metadata.json"
    if not metadata_path.exists():
        return {}
    try:
        return json.loads(metadata_path.read_text(encoding="utf-8"))
    except Exception:
        traceback.print_exc()
        return {}


def ordered_case_slice_records(case_dir: Path, metadata: Dict[str, Any]) -> List[Tuple[int, str, Path, Path]]:
    records: List[Tuple[int, str, Path, Path]] = []
    for index, record in enumerate(metadata.get("slices", []) or []):
        if not isinstance(record, dict):
            continue
        image_value = record.get("image") or record.get("imagePath")
        mask_value = record.get("mask") or record.get("maskPath")
        if not image_value or not mask_value:
            continue
        image_path = resolve_case_file(case_dir, str(image_value))
        mask_path = resolve_case_file(case_dir, str(mask_value))
        if image_path.is_file() and mask_path.is_file():
            records.append((index, str(record.get("original") or image_path.name), image_path, mask_path))
    if records:
        return records

    image_files = sorted((case_dir / "images").glob("*.npy"), key=natural_key)
    mask_files = sorted((case_dir / "masks").glob("*.npy"), key=natural_key)
    if len(image_files) != len(mask_files):
        raise RuntimeError(f"Image/mask slice count mismatch: {len(image_files)} vs {len(mask_files)}")
    return [(index, image_path.name, image_path, mask_path) for index, (image_path, mask_path) in enumerate(zip(image_files, mask_files))]


def preview_image_from_saved_array(array: np.ndarray) -> Image.Image:
    arr = np.squeeze(np.asarray(array))
    if arr.ndim == 2:
        return Image.fromarray(normalize_to_uint8(arr), mode="L")
    if arr.ndim != 3:
        raise ValueError(f"Expected 2D or HxWxC array, got {arr.shape}")
    if arr.shape[-1] == 1:
        return Image.fromarray(normalize_to_uint8(arr[..., 0]), mode="L")
    if np.nanmin(arr) >= 0 and np.nanmax(arr) <= 255:
        return preview_image_from_array(np.clip(arr[..., :3], 0, 255).astype(np.uint8))
    return preview_image_from_array(normalize_channels_to_uint8(arr, min(3, arr.shape[-1])))


def build_case_results(case_dir: Path, confidence: float = 0.0) -> Dict[str, Any]:
    metadata = load_case_metadata(case_dir)
    slice_records = ordered_case_slice_records(case_dir, metadata)
    results: List[Dict[str, Any]] = []

    for index, original_name, image_path, mask_path in slice_records:
        try:
            image_array = np.asarray(np.load(image_path))
            mask = np.squeeze(np.asarray(np.load(mask_path))).astype(np.uint8)
            if mask.ndim == 3:
                mask = mask[..., 0]
            if mask.ndim != 2:
                raise ValueError(f"Expected 2D mask, got {mask.shape}: {mask_path}")

            image = preview_image_from_saved_array(image_array).resize((mask.shape[1], mask.shape[0]))
            labels = sorted(int(value) for value in np.unique(mask) if int(value) > 0)
            probs = np.where(mask > 0, confidence, 0)
            preview = build_segmentation_images(image, mask, extract_polygons_from_mask(mask, probs, confidence))[2]
            results.append({
                "index": index,
                "fileName": original_name,
                "status": "completed",
                "labels": labels,
                "previewBase64": image_to_base64(preview, "PNG"),
                "mode": "multi",
                "inferMode": "restored",
                "imagePath": str(image_path),
                "maskPath": str(mask_path),
                "errorMessage": "",
            })
        except Exception as exc:
            traceback.print_exc()
            results.append({
                "index": index,
                "fileName": original_name,
                "status": "failed",
                "labels": [],
                "mode": "multi",
                "inferMode": "restored",
                "imagePath": str(image_path),
                "maskPath": str(mask_path),
                "errorMessage": str(exc),
            })

    completed = sum(1 for item in results if item.get("status") == "completed")
    status = "completed" if completed > 0 else "failed"
    return {
        "status": status,
        "progress": 100,
        "totalFiles": len(results),
        "processedFiles": completed,
        "outputPath": str(case_dir),
        "results": results,
        "metadata": {
            "case_id": metadata.get("case_id") or case_dir.name,
            "modalities": metadata.get("modalities") or [],
            "shape": metadata.get("shape") or [],
            "postprocess": metadata.get("postprocess") or {},
        },
        "errorMessage": "" if completed > 0 else "no completed slices",
    }


def clean_label_component(label_mask: np.ndarray, min_voxels: int, keep_largest: bool) -> Tuple[np.ndarray, Dict[str, Any]]:
    label_mask = np.asarray(label_mask, dtype=bool)
    original_voxels = int(label_mask.sum())
    if original_voxels == 0:
        return label_mask, {"components_before": 0, "components_after": 0, "removed_components": 0, "removed_voxels": 0}
    if ndimage is None:
        return label_mask, {"components_before": None, "components_after": None, "removed_components": None, "removed_voxels": 0}

    labeled, count = ndimage.label(label_mask, structure=np.ones((3, 3, 3), dtype=np.uint8))
    if count == 0:
        return label_mask, {"components_before": 0, "components_after": 0, "removed_components": 0, "removed_voxels": original_voxels}

    sizes = np.bincount(labeled.ravel())
    keep = np.zeros(count + 1, dtype=bool)
    if keep_largest:
        keep[1 + int(np.argmax(sizes[1:]))] = True
    else:
        keep[1:] = sizes[1:] >= max(1, min_voxels)
        if not keep[1:].any():
            keep[1 + int(np.argmax(sizes[1:]))] = True

    cleaned = keep[labeled]
    cleaned_voxels = int(cleaned.sum())
    after_count = int(np.count_nonzero(keep[1:]))
    return cleaned, {
        "components_before": int(count),
        "components_after": after_count,
        "removed_components": int(count - after_count),
        "removed_voxels": int(original_voxels - cleaned_voxels),
    }


def postprocess_mask_volume(
    masks: Sequence[np.ndarray],
    min_component_voxels: int = 100,
    closing_iterations: int = 0,
    keep_largest: bool = False,
) -> Tuple[List[np.ndarray], Dict[str, Any]]:
    if not masks:
        return [], {"enabled": False, "reason": "empty"}

    volume = np.stack([np.asarray(mask, dtype=np.uint8) for mask in masks], axis=-1)
    labels = [int(value) for value in np.unique(volume) if int(value) > 0]
    if ndimage is None:
        return list(masks), {"enabled": False, "reason": "scipy_not_available", "labels": labels}

    output = np.zeros_like(volume, dtype=np.uint8)
    stats: Dict[str, Any] = {
        "enabled": True,
        "min_component_voxels": int(min_component_voxels),
        "closing_iterations": int(closing_iterations),
        "keep_largest": bool(keep_largest),
        "labels": {},
    }
    structure = np.ones((3, 3, 3), dtype=bool)

    for label in labels:
        label_mask = volume == label
        if closing_iterations > 0 and np.any(label_mask):
            label_mask = ndimage.binary_closing(label_mask, structure=structure, iterations=closing_iterations)
            label_mask = ndimage.binary_fill_holes(label_mask)
        cleaned, label_stats = clean_label_component(label_mask, min_component_voxels, keep_largest)
        output[cleaned & (output == 0)] = label
        label_stats["voxels_before"] = int(np.sum(volume == label))
        label_stats["voxels_after"] = int(np.sum(output == label))
        stats["labels"][str(label)] = label_stats

    return [output[..., index].astype(np.uint8, copy=False) for index in range(output.shape[-1])], stats


def batch_worker(job_id: str, payload: Dict[str, Any]):
    folder = Path(str(payload.get("folderPath") or ""))
    output = Path(str(payload.get("outputPath") or folder / f"{job_id}_output"))
    model_path = str(payload.get("pt_path") or payload.get("modelPath") or "")
    model_format = str(payload.get("model_format") or payload.get("modelFormat") or ("tensorrt" if model_path.lower().endswith(".engine") else "pytorch"))
    conf = parse_float(payload.get("conf", payload.get("confidence")), 0.0)
    input_size = parse_int(payload.get("input_size", payload.get("inputSize")), 240)
    n_channels = parse_int(payload.get("n_channels", payload.get("nChannels")), 3)
    postprocess_enabled = parse_bool(payload.get("postprocess", payload.get("postProcess")), True)
    min_component_voxels = parse_int(payload.get("min_component_voxels", payload.get("minComponentVoxels")), 100)
    closing_iterations = parse_int(payload.get("closing_iterations", payload.get("closingIterations")), 0)
    keep_largest = parse_bool(payload.get("keep_largest", payload.get("keepLargest")), False)
    files = list_image_files(folder)
    images_dir = output / "images"
    masks_dir = output / "masks"
    images_dir.mkdir(parents=True, exist_ok=True)
    masks_dir.mkdir(parents=True, exist_ok=True)
    results: List[Dict[str, Any]] = []
    slices: List[Dict[str, Any]] = []
    mask_records: List[Tuple[int, Path, np.ndarray]] = []
    output_modalities: List[str] = []
    started = time.time()
    first_error_message = ""

    update_job(job_id, status="processing", totalFiles=len(files), processedFiles=0, currentFile="", outputPath=str(output), folderPath=str(folder), errorMessage="")
    try:
        for index, file_path in enumerate(files):
            if job_id in STOP_FLAGS:
                update_job(job_id, status="stopped", currentFile="", finishedAt=now_iso())
                return
            result: Dict[str, Any]
            try:
                image, image_array, modalities = load_input_slice(file_path, input_size, n_channels)
                if not output_modalities:
                    output_modalities = modalities
                mask, _, infer_mode = predict_single_image(image_array, model_path, conf, input_size, n_channels, model_format)
                mask_resized = resize_mask(mask, input_size)
                image_path = images_dir / f"slice_{index:03d}.npy"
                mask_path = masks_dir / f"slice_{index:03d}.npy"
                np.save(image_path, image_array.astype(np.float32))
                np.save(mask_path, mask_resized.astype(np.uint8))
                labels = sorted(int(v) for v in np.unique(mask_resized) if int(v) > 0)
                preview = build_segmentation_images(image, mask_resized, extract_polygons_from_mask(mask_resized, np.where(mask_resized > 0, conf, 0), conf))[2]
                result = {
                    "index": index,
                    "fileName": file_path.name,
                    "status": "completed",
                    "labels": labels,
                    "previewBase64": image_to_base64(preview, "PNG"),
                    "mode": "multi",
                    "inferMode": infer_mode,
                    "imagePath": str(image_path),
                    "maskPath": str(mask_path),
                    "errorMessage": "",
                }
                slices.append({"image": f"images/slice_{index:03d}.npy", "mask": f"masks/slice_{index:03d}.npy", "original": file_path.name})
                mask_records.append((len(results), mask_path, mask_resized.astype(np.uint8)))
            except Exception as exc:
                if not first_error_message:
                    first_error_message = str(exc)
                print(f"[batch:{job_id}] slice failed index={index} file={file_path.name}")
                traceback.print_exc()
                result = {"index": index, "fileName": file_path.name, "status": "failed", "labels": [], "mode": "multi", "errorMessage": str(exc)}
            results.append(result)
            update_job(job_id, processedFiles=index + 1, currentFile=file_path.name, results=results, elapsedSeconds=round(time.time() - started))
            if torch is not None and torch.cuda.is_available() and index % 5 == 0:
                torch.cuda.empty_cache()

        postprocess_stats: Dict[str, Any] = {"enabled": False}
        if postprocess_enabled and mask_records:
            update_job(job_id, currentFile="postprocess 3D masks", results=results, elapsedSeconds=round(time.time() - started))
            processed_masks, postprocess_stats = postprocess_mask_volume(
                [record[2] for record in mask_records],
                min_component_voxels=min_component_voxels,
                closing_iterations=closing_iterations,
                keep_largest=keep_largest,
            )
            for (result_index, mask_path, _), processed_mask in zip(mask_records, processed_masks):
                np.save(mask_path, processed_mask.astype(np.uint8))
                labels = sorted(int(v) for v in np.unique(processed_mask) if int(v) > 0)
                if 0 <= result_index < len(results):
                    results[result_index]["labels"] = labels
                    results[result_index]["postprocessed"] = bool(postprocess_stats.get("enabled"))
        elif not postprocess_enabled:
            postprocess_stats = {"enabled": False, "reason": "disabled_by_request"}

        if not output_modalities:
            output_modalities = get_modalities(n_channels)

        metadata = {
            "case_id": folder.name,
            "slices": slices,
            "modalities": output_modalities,
            "image_channels": len(output_modalities),
            "model_channels": n_channels,
            "axis": 2,
            "shape": [input_size, input_size, len(slices)],
            "spacing": [1.0, 1.0, 1.0],
            "label_map": MULTI_LABEL_MAP,
            "postprocess": postprocess_stats,
        }
        (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
        final_status = "completed" if any(item.get("status") == "completed" for item in results) else "failed"
        job_error = "" if final_status == "completed" else (first_error_message or "all slices failed")
        update_job(job_id, status=final_status, progress=100, currentFile="", finishedAt=now_iso(), results=results, elapsedSeconds=round(time.time() - started), errorMessage=job_error)
    except Exception as exc:
        print(f"[batch:{job_id}] worker failed")
        traceback.print_exc()
        update_job(job_id, status="failed", errorMessage=str(exc), finishedAt=now_iso(), elapsedSeconds=round(time.time() - started))
    finally:
        if torch is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()


@app.get("/health")
def health():
    return api_success({
        "status": "ok",
        "service": "unet4threedim",
        "version": VERSION,
        "device": get_device(),
        "gpu_memory": get_gpu_memory_text(),
        "timestamp": now_iso(),
    })


@app.post("/segment")
def segment():
    if "file" not in request.files:
        return api_error("file is required", 400)
    upload = request.files["file"]
    model_path = request.form.get("pt_path") or request.form.get("modelPath") or ""
    conf = parse_float(request.form.get("conf") or request.form.get("confidence"), 0.25)
    input_size = parse_int(request.form.get("input_size") or request.form.get("inputSize"), 512)
    n_channels = parse_int(request.form.get("n_channels") or request.form.get("nChannels"), 3)
    model_format = request.form.get("model_format") or request.form.get("modelFormat") or ("tensorrt" if model_path.endswith(".engine") else "pytorch")

    started = time.time()
    try:
        image = Image.open(upload.stream).convert("RGB" if n_channels == 3 else "L")
        mask, probs, infer_mode = predict_single_image(image.convert("RGB"), model_path, conf, input_size, n_channels, model_format)
        polygons = extract_polygons_from_mask(mask, probs, conf)
        result_image, mask_image, overlay_image = build_segmentation_images(image.convert("RGB"), mask, polygons)
        processing_ms = round((time.time() - started) * 1000, 2)
        best_polygon = max(polygons, key=lambda item: item.get("confidence", 0), default=None)
        detection = {
            "mode": "segment",
            "tumor_detected": bool(polygons),
            "tumor_type": best_polygon["tumor_type"] if best_polygon else "normal",
            "tumor_type_en": best_polygon["tumor_type"] if best_polygon else "normal",
            "tumor_count": len(polygons),
            "confidence": best_polygon["confidence"] if best_polygon else 0,
            "image_width": image.width,
            "image_height": image.height,
            "processing_time_ms": processing_ms,
            "boxes": [],
            "polygons": polygons,
            "infer_mode": infer_mode,
        }
        return api_success({
            "detection": detection,
            "result_image_base64": image_to_base64(result_image, "JPEG"),
            "mask_image_base64": image_to_base64(mask_image, "PNG"),
            "overlay_image_base64": image_to_base64(overlay_image, "JPEG"),
            "result_image_path": "",
            "original_filename": upload.filename,
            "model_path": model_path,
            "conf_threshold": conf,
            "input_size": input_size,
            "model_format": model_format,
        }, "分割成功")
    except Exception as exc:
        return api_error(str(exc), 500)


@app.post("/segment/batch")
def start_batch():
    payload = request.get_json(silent=True) or {}
    folder = Path(str(payload.get("folderPath") or ""))
    if not folder.exists() or not folder.is_dir():
        return api_error(f"folderPath does not exist: {folder}", 400)
    job_id = str(payload.get("jobId") or uuid.uuid4())
    files = list_image_files(folder)
    output = Path(str(payload.get("outputPath") or folder / f"{job_id}_output"))
    STOP_FLAGS.discard(job_id)
    update_job(job_id, status="processing", totalFiles=len(files), processedFiles=0, outputPath=str(output), folderPath=str(folder), results=[])
    thread = threading.Thread(target=batch_worker, args=(job_id, payload), daemon=True)
    thread.start()
    return api_success({"jobId": job_id, "status": "processing", "totalFiles": len(files), "outputPath": str(output), "folderPath": str(folder)}, "批量分割任务已启动")


@app.get("/segment/progress/<job_id>")
def progress(job_id: str):
    job = JOBS.get(job_id)
    if not job:
        return api_error(f"job not found: {job_id}", 404)
    if job.get("status") != "completed":
        data = dict(job)
        if job.get("status") == "failed":
            data["results"] = job.get("results", [])
        else:
            data["results"] = []
        return api_success(data)
    return api_success(job)


@app.post("/segment/caseResults")
def case_results():
    payload = request.get_json(silent=True) or {}
    case_dir = Path(str(payload.get("outputPath") or payload.get("casePath") or ""))
    if not case_dir.exists() or not case_dir.is_dir():
        return api_error(f"outputPath does not exist: {case_dir}", 404)
    confidence = parse_float(payload.get("conf", payload.get("confidence")), 0.0)
    try:
        return api_success(build_case_results(case_dir, confidence), "病例分割预览已恢复")
    except Exception as exc:
        traceback.print_exc()
        return api_error(f"restore case results failed: {exc}", 500)


@app.post("/segment/stop")
def stop_job():
    payload = request.get_json(silent=True) or {}
    job_id = str(payload.get("jobId") or "")
    if not job_id:
        return api_error("jobId is required", 400)
    STOP_FLAGS.add(job_id)
    job = update_job(job_id, status="stopped", finishedAt=now_iso())
    return api_success({"jobId": job_id, "status": job["status"]}, "任务已停止")


@app.post("/segment/validatePath")
def validate_path():
    payload = request.get_json(silent=True) or {}
    path = Path(str(payload.get("path") or payload.get("folderPath") or ""))
    files = list_image_files(path) if path.exists() and path.is_dir() else []
    return api_success({
        "path": str(path),
        "exists": path.exists(),
        "directory": path.is_dir(),
        "totalFiles": len(files),
        "imageFiles": [file.name for file in files[:200]],
    })


@app.post("/memory/clear")
def clear_memory():
    before = get_gpu_memory_text()
    gc.collect()
    if torch is not None and torch.cuda.is_available():
        torch.cuda.empty_cache()
    return api_success({"gpu_memory_before": before, "gpu_memory_after": get_gpu_memory_text()})


def main():
    parser = argparse.ArgumentParser(description="UNet segmentation service for 3D reconstruction")
    parser.add_argument("--host", default=os.environ.get("UNET_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("UNET_PORT", "3408")))
    parser.add_argument("--config", default=os.environ.get("UNET_CONFIG", "config.yml"))
    args = parser.parse_args()
    log_runtime_diagnostics()
    app.run(host=args.host, port=args.port, debug=False, threaded=True)


if __name__ == "__main__":
    main()
