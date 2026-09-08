# 业务权重

按任务分类，只保留能跑推理的文件。

```
models/
├── yolo/
│   ├── detect/          # 矩形框检测
│   │   ├── YOLOv11/best.pt
│   │   ├── YOLOv11/brain-detect-yolo11.onnx
│   │   └── YOLOv8/best.pt
│   └── mask/            # 实例分割 mask
│       └── YOLO11/best.pt
│       └── YOLO11/brain-seg-yolo11.onnx
└── unet/
    └── brats/
        └── checkpoint_epoch100.pth   # Git LFS，三维重建 / 批量分割
```

来源：

- YOLO：`D:\algorithms\V11-dmt\runs\brain`（detect 与 mask）
- UNet：`D:\algorithms\Pytorch-UNet-master\checkpoints\brats`

未纳入仓库：训练 `last.pt`、重复的 `unet.onnx` / `brats-unet.onnx`（约 118MB，超出建议体积；推理用 `.pth`）。
