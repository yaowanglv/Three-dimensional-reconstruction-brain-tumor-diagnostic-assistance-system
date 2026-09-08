# Ultralytics 🚀 AGPL-3.0 License - https://ultralytics.com/license
"""
RT-DETR MultiModal predictor module.

This module provides the RTDETRMMPredictor class for inference with multi-modal RT-DETR models
supporting RGB+X modality inputs. Architecture strictly follows YOLOMM's successful pattern.
"""

import torch
import numpy as np
import cv2
from pathlib import Path
from typing import Union, List, Dict, Optional, Tuple, Any

from ultralytics.models.rtdetr.predict import RTDETRPredictor
from ultralytics.utils import LOGGER, DEFAULT_CFG, ops, colorstr
from ultralytics.engine.results import Results


class RTDETRMMPredictor(RTDETRPredictor):
    """
    A predictor class for RT-DETR MultiModal (RTDETRMM) object detection models.

    This class extends RTDETRPredictor to support multi-modal inputs (RGB + X modality)
    during inference. Architecture strictly follows YOLOMM's MultiModalDetectionPredictor
    successful pattern for consistency and simplicity.

    Key Features:
        - Supports RGB+X dual-modal inference (best performance)
        - Supports single-modal inference with modality parameter
        - Always outputs 6-channel tensor [RGB,RGB,RGB,X,X,X] format
        - Compatible with YOLOMM API for consistent user experience
        - Simple and clean architecture following proven patterns

    Input Format Requirements:
        - Dual-modal: List format [RGB_source, X_source] - ORDER IS IMPORTANT!
        - Single-modal: Single source with modality parameter
        - 6-channel tensor: Pre-processed [RGB, RGB, RGB, X, X, X] format

    Attributes:
        args: Prediction arguments and settings.
        model: The RTDETRMM model used for inference.
        modality: Specific modality for single-modal inference.
        is_dual_modal: Whether current inference is dual-modal.
        is_single_modal: Whether current inference is single-modal.

    Methods:
        preprocess: Preprocess input data for multi-modal inference.
        _parse_inference_input: Parse and validate inference input.
        _process_dual_modality: Process dual-modal input.
        _process_single_modality: Process single-modal input with filling.

    Examples:
        >>> # Dual-modal inference
        >>> predictor = RTDETRMMPredictor(overrides={'model': 'rtdetr-r18-mm.pt'})
        >>> results = predictor(source=['rgb.jpg', 'thermal.jpg'])  # [RGB, X] order!
        
        >>> # Single-modal inference
        >>> predictor = RTDETRMMPredictor(overrides={'modality': 'rgb'})
        >>> results = predictor(source='rgb.jpg')
    """

    def __init__(self, cfg=DEFAULT_CFG, overrides=None, _callbacks=None):
        """
        Initialize RTDETRMMPredictor for multi-modal inference.

        Args:
            cfg (str | Path | dict, optional): Configuration file path or dict.
            overrides (dict, optional): Dictionary to override default prediction arguments.
            _callbacks (list, optional): List of callback functions.
        """
        # Initialize parent predictor
        super().__init__(cfg, overrides, _callbacks)
        
        # Initialize multimodal-specific attributes (following YOLOMM pattern)
        self.modality = getattr(self.args, 'modality', None)
        
        # Initialize modal states (following YOLOMM pattern)
        self.is_dual_modal = self.modality is None
        self.is_single_modal = self.modality is not None
        
        # Track input sources for multi-modal visualization
        self.rgb_source = None
        self.x_source = None
        self.input_mode = None  # 'dual', 'single_rgb', 'single_x'
        
        # Log initialization
        LOGGER.info(f"RTDETRMMPredictor initialized: modality={self.modality}, "
                   f"dual={self.is_dual_modal}, single={self.is_single_modal}")

    def _parse_inference_input(self, source):
        """
        Parse and validate inference input from RTDETRMM.predict() method.
        
        Follows YOLOMM's input parsing pattern exactly for consistency.
        
        Args:
            source: Input source from RTDETRMM.predict() method. Can be:
                - Single file path (str/Path): for single-modal inference
                - List of 2 paths: [rgb_path, x_path] for dual-modal inference
                - PIL.Image or np.ndarray: for single image
                - torch.Tensor: preprocessed tensor
                
        Returns:
            tuple: (parsed_source, input_format_info) where:
                - parsed_source: Validated and normalized input source
                - input_format_info: Dict with input analysis information
                
        Raises:
            ValueError: If input format is invalid or incompatible with modality settings
            TypeError: If input type is not supported
        """
        import numpy as np
        from PIL import Image
        from pathlib import Path
        
        # Initialize input format analysis (following YOLOMM pattern)
        input_info = {
            'input_type': type(source).__name__,
            'is_batch': False,
            'source_count': 1,
            'modality_mode': 'dual' if self.is_dual_modal else f'single_{self.modality}',
            'inference_format': None,
            'validation_passed': False
        }
        
        try:
            # Log initial input analysis
            LOGGER.debug(f"解析推理输入: 类型={input_info['input_type']}, 模态模式={input_info['modality_mode']}")
            
            # Case 1: Tensor input (already preprocessed)
            if isinstance(source, torch.Tensor):
                input_info['inference_format'] = 'preprocessed_tensor'
                input_info['tensor_shape'] = list(source.shape)
                
                if source.dim() == 4 and source.shape[1] == 6:
                    LOGGER.debug("检测到6通道预处理tensor，直接使用")
                    input_info['validation_passed'] = True
                    return source, input_info
                else:
                    LOGGER.warning(f"Tensor维度不符合预期: {source.shape}，将重新处理")
            
            # Case 2: List/Tuple input
            elif isinstance(source, (list, tuple)):
                input_info['source_count'] = len(source)
                
                if len(source) == 2 and self.is_dual_modal:
                    # Dual-modal format: [rgb_source, x_source]
                    input_info['inference_format'] = 'dual_modal'
                    rgb_source, x_source = source
                    
                    # Validate individual sources
                    rgb_info = self._analyze_single_source(rgb_source, 'rgb')
                    x_info = self._analyze_single_source(x_source, 'x_modal')
                    
                    input_info['rgb_source'] = rgb_info
                    input_info['x_source'] = x_info
                    input_info['validation_passed'] = True
                    
                    return source, input_info
                    
                elif len(source) == 1 and self.is_single_modal:
                    # Single-modal with list wrapper
                    input_info['inference_format'] = 'single_modal'
                    single_source = source[0]
                    LOGGER.debug(f"单模态输入(列表包装): {type(single_source)}")
                    
                    # Analyze the single source
                    source_info = self._analyze_single_source(single_source, self.modality)
                    input_info.update(source_info)
                    input_info['validation_passed'] = True
                    
                    return single_source, input_info
                    
                else:
                    # Invalid list format for current modality settings
                    if self.is_dual_modal:
                        raise ValueError(f"双模态推理需要2个输入源，但接收到{len(source)}个")
                    else:
                        # Use first element for single-modal
                        single_source = source[0]
                        LOGGER.warning(f"单模态推理接收到{len(source)}个输入，使用第一个: {single_source}")
                        return self._parse_inference_input(single_source)
            
            # Case 3: Single source input
            else:
                if self.is_dual_modal:
                    raise ValueError(
                        f"双模态推理需要列表格式输入 [rgb_source, x_source]，"
                        f"但接收到单个源: {type(source)}"
                    )
                
                # Single-modal input validation
                input_info['inference_format'] = 'single_modal'
                source_info = self._analyze_single_source(source, self.modality)
                input_info.update(source_info)
                input_info['validation_passed'] = True
                
                return source, input_info
                
        except Exception as e:
            input_info['validation_passed'] = False
            input_info['error'] = str(e)
            LOGGER.error(f"输入解析失败: {e}")
            raise
        
        finally:
            # Log final input analysis
            self._log_input_analysis(input_info)

    def _analyze_single_source(self, source, modality_hint=None):
        """
        Analyze a single input source and determine its characteristics.
        Following YOLOMM pattern exactly.
        
        Args:
            source: Single input source (path, PIL.Image, np.ndarray, etc.)
            modality_hint (str): Hint about expected modality type
            
        Returns:
            dict: Analysis information about the source
        """
        import numpy as np
        from PIL import Image
        from pathlib import Path
        
        analysis = {
            'source_type': 'unknown',
            'path': None,
            'exists': False,
            'format': None,
            'modality_hint': modality_hint
        }
        
        if isinstance(source, (str, Path)):
            # File path
            path = Path(source)
            analysis['source_type'] = 'file_path'
            analysis['path'] = str(path)
            analysis['exists'] = path.exists()
            analysis['format'] = path.suffix.lower() if path.suffix else 'no_extension'
            
            if not analysis['exists']:
                raise FileNotFoundError(f"输入文件不存在: {path}")
                
        elif isinstance(source, Image.Image):
            # PIL Image
            analysis['source_type'] = 'pil_image'
            analysis['format'] = source.format or 'unknown'
            analysis['mode'] = source.mode
            analysis['size'] = source.size
            
        elif isinstance(source, np.ndarray):
            # Numpy array
            analysis['source_type'] = 'numpy_array'
            analysis['shape'] = source.shape
            analysis['dtype'] = str(source.dtype)
            
        elif isinstance(source, torch.Tensor):
            # Tensor
            analysis['source_type'] = 'torch_tensor'
            analysis['shape'] = list(source.shape)
            analysis['dtype'] = str(source.dtype)
            analysis['device'] = str(source.device)
            
        else:
            analysis['source_type'] = f'unsupported_{type(source).__name__}'
            
        return analysis

    def _log_input_analysis(self, input_info):
        """
        Log detailed input analysis information.
        Following YOLOMM pattern.
        
        Args:
            input_info (dict): Input analysis information
        """
        LOGGER.debug("=== RTDETRMM输入解析分析报告 ===")
        LOGGER.debug(f"输入类型: {input_info['input_type']}")
        LOGGER.debug(f"推理格式: {input_info['inference_format']}")
        LOGGER.debug(f"模态模式: {input_info['modality_mode']}")
        LOGGER.debug(f"源数量: {input_info['source_count']}")
        LOGGER.debug(f"验证通过: {input_info['validation_passed']}")
        
        if 'rgb_source' in input_info:
            LOGGER.debug(f"RGB源信息: {input_info['rgb_source']}")
            
        if 'x_source' in input_info:
            LOGGER.debug(f"X模态源信息: {input_info['x_source']}")
            
        if 'error' in input_info:
            LOGGER.debug(f"错误信息: {input_info['error']}")
            
        LOGGER.debug("=== 分析报告结束 ===")

    def _update_modality_state(self):
        """
        Update modality state dynamically from args (following YOLOMM pattern).
        This allows for dynamic modality switching during prediction.
        """
        current_modality = getattr(self.args, 'modality', None)
        if current_modality != self.modality:
            LOGGER.debug(f"RTDETRMMPredictor: 动态更新模态状态 {self.modality} → {current_modality}")
            self.modality = current_modality
            self.is_dual_modal = self.modality is None
            self.is_single_modal = self.modality is not None

    def preprocess(self, im):
        """
        Prepares multimodal input images before inference.
        
        Follows YOLOMM's preprocess pattern exactly for consistency.
        Always outputs 6-channel tensor [RGB, RGB, RGB, X, X, X] format.
        
        Args:
            im (torch.Tensor | List | str): Input images. Can be:
                - List of 2 paths: [rgb_path, x_path] for dual-modal
                - Single path: rgb_path or x_path for single-modal
                - torch.Tensor: preprocessed tensor
                
        Returns:
            torch.Tensor: 6-channel tensor [RGB, RGB, RGB, X, X, X] format
            
        Raises:
            ValueError: If input format is invalid or incompatible with modality settings
            FileNotFoundError: If image files cannot be found
            RuntimeError: If tensor processing fails
        """
        try:
            # Update modality state dynamically (following YOLOMM pattern)
            self._update_modality_state()
            
            # Detect input mode before preprocessing (following YOLOMM pattern)
            if isinstance(im, (list, tuple)) and len(im) == 2:
                self._dual_input_detected = True
                self.input_mode = 'dual'
            else:
                self._dual_input_detected = False
                self.input_mode = f'single_{self.modality}' if self.modality else 'single'
            
            # 使用新的输入解析方法 (following YOLOMM pattern)
            LOGGER.debug(f"开始多模态预处理: modality={self.modality}, input_type={type(im)}")
            
            # 解析和验证输入
            parsed_source, input_info = self._parse_inference_input(im)
            
            # 快速路径：如果输入已经是正确格式的6通道tensor
            if isinstance(parsed_source, torch.Tensor) and parsed_source.dim() == 4 and parsed_source.shape[1] == 6:
                LOGGER.debug("输入已为6通道tensor，进行格式验证后直接返回")
                return self._finalize_tensor(parsed_source)
            
            # 根据解析结果选择处理路径
            if input_info['inference_format'] in ['dual_modal'] and self.is_dual_modal:
                result_tensor = self._process_dual_modality(parsed_source)
            elif input_info['inference_format'] in ['single_modal'] and self.is_single_modal:
                result_tensor = self._process_single_modality(parsed_source)
            else:
                # 兼容旧的处理方式
                if self.is_dual_modal:
                    result_tensor = self._process_dual_modality(parsed_source)
                else:
                    result_tensor = self._process_single_modality(parsed_source)
            
            # 最终格式验证和设备转换
            final_tensor = self._finalize_tensor(result_tensor)
            
            LOGGER.debug(f"多模态预处理完成: shape={final_tensor.shape}, device={final_tensor.device}")
            return final_tensor
            
        except Exception as e:
            # 统一异常处理
            error_msg = f"多模态预处理失败: {str(e)}"
            LOGGER.error(error_msg)
            self._log_debug_info(im, e)
            raise RuntimeError(error_msg) from e

    def _process_dual_modality(self, im):
        """
        Process dual-modal input: [rgb_path, x_path] or similar formats.
        
        Follows YOLOMM pattern exactly with RT-DETR specific adaptations.
        
        Args:
            im (List): Dual-modal input data [RGB_source, X_source]
            
        Returns:
            torch.Tensor: 6-channel tensor [RGB, RGB, RGB, X, X, X]
        """
        # If already preprocessed tensor with 6 channels, return directly
        if isinstance(im, torch.Tensor) and im.shape[1] == 6:
            LOGGER.debug("输入已为6通道tensor，直接返回")
            return im
        
        # Parse dual-modal input and load images
        rgb_images, x_images = self._parse_dual_modal_input(im)
        
        # Preprocess each modality separately using parent's method (RT-DETR specific)
        rgb_tensor = super().preprocess(rgb_images)  # Shape: (B, 3, H, W)
        x_tensor = super().preprocess(x_images)      # Shape: (B, 3, H, W)
        
        # Ensure same spatial dimensions
        rgb_tensor, x_tensor = self._align_tensor_dimensions(rgb_tensor, x_tensor)
        
        # Combine modalities: [RGB, RGB, RGB, X, X, X] order (matching YOLOMM training data format)
        combined_tensor = torch.cat([rgb_tensor, x_tensor], dim=1)  # Shape: (B, 6, H, W)
        
        LOGGER.debug(f"双模态预处理完成: {combined_tensor.shape}")
        return combined_tensor

    def _parse_dual_modal_input(self, im):
        """
        Parse dual-modal input and separate RGB and X-modal data.
        Following YOLOMM pattern with RT-DETR adaptations.
        
        Args:
            im (List): Input data - [rgb_source, x_source]
                
        Returns:
            tuple: (rgb_images, x_images) ready for preprocessing
        """
        if isinstance(im, (list, tuple)):
            if len(im) == 2:
                # Standard dual-modal format: [rgb_source, x_source]
                rgb_source, x_source = im
                LOGGER.debug(f"解析标准双模态输入: RGB={type(rgb_source)}, X={type(x_source)}")
                
                # Load images using simple loading
                rgb_images = self._load_image_source(rgb_source)
                x_images = self._load_image_source(x_source)
                
                return rgb_images, x_images
                
            else:
                # Invalid dual-modal input format
                raise ValueError(
                    f"双模态推理需要包含2个元素的列表输入 [rgb_source, x_source]，"
                    f"但接收到: {type(im)} with {len(im)} 元素"
                )
        else:
            # Single source input - invalid for dual-modal
            raise ValueError(
                f"双模态推理需要列表格式输入 [rgb_source, x_source]，"
                f"但接收到单个源: {type(im)}"
            )

    def _load_image_source(self, source):
        """
        Load image(s) from various source types.
        Simplified version of YOLOMM's loading mechanism.
        
        Args:
            source (str | Path | PIL.Image | np.ndarray | torch.Tensor): Image source
            
        Returns:
            List[np.ndarray]: Loaded images ready for preprocessing
        """
        import cv2
        import numpy as np
        from PIL import Image
        from pathlib import Path
        
        LOGGER.debug(f"加载图像源: 类型={type(source)}")
        
        # Case 1: String or Path (file path)
        if isinstance(source, (str, Path)):
            source_path = Path(source)
            
            # Check if file exists
            if not source_path.exists():
                raise FileNotFoundError(f"图像文件不存在: {source_path}")
            
            # Direct OpenCV loading
            img = cv2.imread(str(source_path))
            if img is None:
                raise ValueError(f"无法加载图像: {source_path}")
            
            # Convert BGR to RGB
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            LOGGER.debug(f"成功加载图像: {source_path}")
            return [img]
            
        # Case 2: PIL Image
        elif isinstance(source, Image.Image):
            LOGGER.debug("处理PIL图像输入")
            if source.mode != "RGB":
                source = source.convert("RGB")
            img = np.asarray(source)
            return [img]
            
        # Case 3: Numpy array
        elif isinstance(source, np.ndarray):
            LOGGER.debug(f"处理numpy数组输入: shape={source.shape}")
            
            if source.ndim == 3:
                # Single image: (H, W, C)
                return [source]
            elif source.ndim == 4:
                # Batch of images: (B, H, W, C) or (B, C, H, W)
                if source.shape[1] == 3 or source.shape[1] == 1:
                    # Format: (B, C, H, W) -> convert to (B, H, W, C)
                    images = [img.transpose(1, 2, 0) for img in source]
                else:
                    # Format: (B, H, W, C)
                    images = list(source)
                return images
            else:
                raise ValueError(f"不支持的numpy数组维度: {source.shape}")
                
        # Case 4: Torch Tensor
        elif isinstance(source, torch.Tensor):
            LOGGER.debug(f"处理torch.Tensor输入: shape={source.shape}")
            
            # Convert to numpy
            if source.device != torch.device('cpu'):
                source = source.cpu()
            source_np = source.numpy()
            
            # Recursive call with numpy array
            return self._load_image_source(source_np)
            
        # Case 5: List or Tuple (multiple images)
        elif isinstance(source, (list, tuple)):
            LOGGER.debug(f"处理列表输入: 长度={len(source)}")
            
            all_images = []
            for i, item in enumerate(source):
                try:
                    loaded = self._load_image_source(item)
                    all_images.extend(loaded)
                except Exception as e:
                    LOGGER.error(f"加载列表项[{i}]失败: {e}")
                    raise
            
            LOGGER.debug(f"列表加载完成: 总计{len(all_images)}张图像")
            return all_images
            
        else:
            raise TypeError(f"不支持的图像源类型: {type(source)}")

    def _align_tensor_dimensions(self, tensor1, tensor2):
        """
        Ensure two tensors have the same spatial dimensions.
        Following YOLOMM pattern exactly.
        
        Args:
            tensor1 (torch.Tensor): First tensor (B, C, H, W)
            tensor2 (torch.Tensor): Second tensor (B, C, H, W)
            
        Returns:
            tuple: (aligned_tensor1, aligned_tensor2) with same spatial dimensions
        """
        import torch.nn.functional as F
        
        if tensor1.shape[2:] == tensor2.shape[2:]:
            # Already same dimensions
            return tensor1, tensor2
        
        # Get dimensions
        h1, w1 = tensor1.shape[2:]
        h2, w2 = tensor2.shape[2:]
        
        # Use the smaller dimensions as target
        target_h = min(h1, h2)
        target_w = min(w1, w2)
        target_size = (target_h, target_w)
        
        LOGGER.debug(f"对齐tensor维度到: {target_size}")
        
        # Resize if necessary
        if (h1, w1) != target_size:
            tensor1 = F.interpolate(tensor1, size=target_size, mode='bilinear', align_corners=False)
        
        if (h2, w2) != target_size:
            tensor2 = F.interpolate(tensor2, size=target_size, mode='bilinear', align_corners=False)
        
        return tensor1, tensor2

    def _process_single_modality(self, im):
        """
        Process single-modal input with intelligent filling for missing modality.
        
        Follows YOLOMM pattern exactly with ModalityFiller integration.
        
        Args:
            im (str | Path | PIL.Image | np.ndarray | torch.Tensor): Single modal input
            
        Returns:
            torch.Tensor: 6-channel tensor [RGB, RGB, RGB, X, X, X] format
        """
        # 如果输入是路径字符串，需要先加载图像
        if isinstance(im, (str, Path)):
            im = self._load_image_source(im)
        
        # 确保传递给父类preprocess的是正确格式的列表
        if isinstance(im, np.ndarray) and im.ndim == 3:
            # 单个HWC图像需要包装成列表格式
            im = [im]
        
        # Preprocess the input modality using parent's method (RT-DETR specific)
        source_tensor = super().preprocess(im)  # Shape: (B, 3, H, W)
        
        if self.modality == 'rgb':
            # RGB模态推理：RGB为真实数据，X模态需要填充
            rgb_tensor = source_tensor
            x_tensor = self._generate_modality_filling(rgb_tensor, 'rgb', 'x')
            LOGGER.debug(f"RGB单模态推理 - 生成X模态填充数据")
            
        elif self.modality in ['depth', 'thermal', 'ir', 'nir', 'x']:
            # X模态推理：X模态为真实数据，RGB需要填充
            x_tensor = source_tensor
            rgb_tensor = self._generate_modality_filling(x_tensor, self.modality, 'rgb')
            LOGGER.debug(f"{self.modality}单模态推理 - 生成RGB填充数据")
            
        else:
            raise ValueError(f"不支持的单模态类型: {self.modality}")
        
        # 组合成6通道tensor: [RGB, RGB, RGB, X, X, X] 顺序（与YOLOMM训练数据格式保持一致）
        combined_tensor = torch.cat([rgb_tensor, x_tensor], dim=1)  # Shape: (B, 6, H, W)
        
        LOGGER.debug(f"单模态预处理完成: {combined_tensor.shape}")
        return combined_tensor

    def _generate_modality_filling(self, source_tensor: torch.Tensor, 
                                   source_modality: str, target_modality: str) -> torch.Tensor:
        """
        Generate filled modality data for single-modal inference.
        
        Uses YOLOMM's ModalityFiller if available, otherwise simple fallback.
        
        Args:
            source_tensor: Source modality tensor (B, 3, H, W)
            source_modality: Source modality name
            target_modality: Target modality to generate
            
        Returns:
            torch.Tensor: Generated modality tensor (B, 3, H, W)
        """
        # Try to import YOLOMM's ModalityFiller if available
        try:
            from ultralytics.models.yolo.multimodal.modal_filling import generate_modality_filling
            LOGGER.debug(f"使用YOLOMM的ModalityFiller生成{target_modality}模态")
            return generate_modality_filling(source_tensor, source_modality, target_modality)
        except ImportError:
            LOGGER.debug(f"ModalityFiller不可用，使用简单填充策略")
        
        # Fallback: Simple filling strategies
        if target_modality == 'x' or source_modality == 'rgb':
            # For X modality, use edge detection or grayscale as simple approximation
            # Convert to grayscale and replicate
            gray = 0.299 * source_tensor[:, 0] + 0.587 * source_tensor[:, 1] + 0.114 * source_tensor[:, 2]
            filled = gray.unsqueeze(1).repeat(1, 3, 1, 1)
        else:
            # For RGB from X modality, just copy (simple strategy)
            filled = source_tensor.clone()
        
        # Add slight noise to indicate it's synthetic
        noise = torch.randn_like(filled) * 0.01
        filled = filled + noise
        
        return filled.clamp(0, 1)

    def _finalize_tensor(self, tensor):
        """
        Finalize processed tensor with format validation and device management.
        Following YOLOMM pattern exactly.
        
        Args:
            tensor (torch.Tensor): Processed tensor to finalize
            
        Returns:
            torch.Tensor: Finalized tensor on correct device
            
        Raises:
            ValueError: If tensor format is invalid
        """
        if not isinstance(tensor, torch.Tensor):
            raise ValueError(f"期望torch.Tensor输出，但得到: {type(tensor)}")
        
        if tensor.dim() != 4:
            raise ValueError(f"期望4维tensor [B, C, H, W]，但得到维度: {tensor.dim()}")
        
        if tensor.shape[1] != 6:
            raise ValueError(f"期望6通道tensor，但得到: {tensor.shape[1]}通道")
        
        # 设备管理 - 确保tensor在正确的设备上
        if hasattr(self, 'device') and self.device != tensor.device:
            LOGGER.debug(f"转移tensor到设备: {self.device}")
            tensor = tensor.to(self.device)
        
        # 数据类型确保
        if tensor.dtype != torch.float32:
            LOGGER.debug(f"转换tensor数据类型: {tensor.dtype} -> torch.float32")
            tensor = tensor.float()
        
        return tensor

    def _log_debug_info(self, im, exception):
        """
        Log detailed debug information when preprocessing fails.
        Following YOLOMM pattern.
        
        Args:
            im: Original input data
            exception: The exception that occurred
        """
        LOGGER.debug("=== 多模态预处理调试信息 ===")
        LOGGER.debug(f"模态设置: modality={self.modality}, is_dual_modal={self.is_dual_modal}")
        LOGGER.debug(f"输入类型: {type(im)}")
        
        if isinstance(im, (list, tuple)):
            LOGGER.debug(f"列表输入长度: {len(im)}")
            for i, item in enumerate(im):
                LOGGER.debug(f"  项目[{i}]: {type(item)} - {item}")
        elif isinstance(im, torch.Tensor):
            LOGGER.debug(f"Tensor形状: {im.shape}")
            LOGGER.debug(f"Tensor设备: {im.device}")
            LOGGER.debug(f"Tensor数据类型: {im.dtype}")
        else:
            LOGGER.debug(f"输入内容: {im}")
        
        LOGGER.debug(f"异常类型: {type(exception).__name__}")
        LOGGER.debug(f"异常信息: {str(exception)}")
        LOGGER.debug("=== 调试信息结束 ===")

    def stream_inference(self, source=None, model=None, *args, **kwargs):
        """
        Streams real-time inference on camera feed and saves results to file.
        
        Overrides parent method to handle 6-channel warmup for multimodal RT-DETR models.
        Always uses 6-channel warmup for RTDETRMM (matching YOLOMM pattern).
        
        Args:
            source (str, optional): The source of the image to make predictions on.
            model (nn.Module, optional): The model to use for predictions.
            *args (Any): Variable length argument list.
            **kwargs (Any): Arbitrary keyword arguments.
            
        Yields:
            (List[ultralytics.engine.results.Results]): The prediction results.
        """
        if self.args.verbose:
            LOGGER.info("")

        # Setup model
        if not self.model:
            self.setup_model(model)

        with self._lock:  # for thread-safe inference
            # Setup source every time predict is called
            self.setup_source(source if source is not None else self.args.source)

            # Check if save_dir/ label file exists
            if self.args.save or self.args.save_txt:
                (self.save_dir / "labels" if self.args.save_txt else self.save_dir).mkdir(parents=True, exist_ok=True)

            # RTDETRMM warmup with 6 channels (following YOLOMM pattern)
            if not self.done_warmup:
                model_channels = 6  # RTDETRMM固定为6通道输入（与YOLOMM一致）
                LOGGER.info(f"RTDETRMMPredictor: 使用 {model_channels} 通道进行模型预热")
                
                # RT-DETR multimodal warmup with 6 channels
                self.model.warmup(imgsz=(1 if self.model.pt or self.model.triton else self.dataset.bs, 
                                       model_channels, *self.imgsz))
                self.done_warmup = True

            self.seen, self.windows, self.batch = 0, [], None
            profilers = (
                ops.Profile(device=self.device),
                ops.Profile(device=self.device),
                ops.Profile(device=self.device),
            )
            self.run_callbacks("on_predict_start")
            for self.batch in self.dataset:
                self.run_callbacks("on_predict_batch_start")
                paths, im0s, s = self.batch

                # Preprocess
                with profilers[0]:
                    im = self.preprocess(im0s)

                # Inference
                with profilers[1]:
                    preds = self.inference(im, *args, **kwargs)
                    if self.args.embed:
                        yield from [preds] if isinstance(preds, torch.Tensor) else preds  # yield embedding tensors
                        continue

                # Postprocess
                with profilers[2]:
                    self.results = self.postprocess(preds, im, im0s)
                self.run_callbacks("on_predict_postprocess_end")

                # Visualize, save, write results
                n = len(im0s)  # 原始输入图像数量
                results_count = len(self.results)  # 实际生成的结果数量
                
                # 对于多模态推理，原始图像可能是2张，但结果只有1个
                # 需要根据实际results数量来处理
                if results_count != n:
                    LOGGER.debug(f"多模态推理: 输入{n}张图像，生成{results_count}个结果")
                
                for i in range(results_count):  # 使用实际结果数量
                    self.seen += 1
                    self.results[i].speed = {
                        "preprocess": profilers[0].dt * 1e3 / results_count,  # 使用结果数量计算平均时间
                        "inference": profilers[1].dt * 1e3 / results_count,
                        "postprocess": profilers[2].dt * 1e3 / results_count,
                    }
                    
                    # 为多模态推理调整路径处理
                    if results_count < n:
                        # 多模态情况：多个输入产生1个结果，使用第一个路径作为主路径
                        result_path = Path(paths[0])
                        result_string = s[0] if s else ""
                        
                        # 在结果字符串中添加多模态信息
                        if len(paths) > 1:
                            modality_info = f"({len(paths)}模态输入)"
                            result_string = f"{result_string} {modality_info}" if result_string else modality_info
                    else:
                        # 标准情况：1对1映射
                        result_path = Path(paths[i])
                        result_string = s[i] if i < len(s) else ""
                    
                    if self.args.verbose or self.args.save or self.args.save_txt or self.args.show:
                        result_string += self.write_results(i, result_path, im, result_string)
                    
                    # 保存更新后的字符串
                    if i < len(s):
                        s[i] = result_string
                    elif len(s) == 0:
                        s = [result_string]

                # Print batch results
                if self.args.verbose:
                    # 只打印有效的结果字符串
                    valid_strings = [s_item for s_item in s[:results_count] if s_item]
                    if valid_strings:
                        LOGGER.info("\n".join(valid_strings))

                self.run_callbacks("on_predict_batch_end")
                yield from self.results

        # Release assets
        for v in self.vid_writer.values():
            if isinstance(v, cv2.VideoWriter):
                v.release()

        # Print final results
        if self.args.verbose and self.seen:
            t = tuple(x.t / self.seen * 1e3 for x in profilers)  # speeds per image
            LOGGER.info(
                f"Speed: %.1fms preprocess, %.1fms inference, %.1fms postprocess per image at shape "
                f"{(min(self.args.batch, self.seen), 6, *im.shape[2:])}"  # 显示6通道
                % t
            )
        if self.args.save or self.args.save_txt or self.args.save_crop:
            nl = len(list(self.save_dir.glob("labels/*.txt")))  # number of labels
            s = f"\n{nl} label{'s' * (nl > 1)} saved to {self.save_dir / 'labels'}" if self.args.save_txt else ""
            LOGGER.info(f"Results saved to {colorstr('bold', self.save_dir)}{s}")
        self.run_callbacks("on_predict_end")
    
    def postprocess(self, preds, img, orig_imgs):
        """
        Override parent's postprocess to cache original images for multi-modal visualization.
        
        Following YOLOMM pattern for multi-modal result handling.
        For dual-modal input, creates only one Results object using the RGB path.
        
        Args:
            preds: Model predictions
            img: Preprocessed images
            orig_imgs: Original images
            
        Returns:
            Results: Detection results
        """
        # Store original images for later use in visualization
        self._orig_imgs_cache = orig_imgs
        
        # Call parent's postprocess to get properly formatted results
        results = super().postprocess(preds, img, orig_imgs)
        
        # For dual-modal input, only keep the first result (RGB)
        # This prevents duplicate outputs since we have 2 inputs but 1 detection result
        if self.is_dual_modal and isinstance(orig_imgs, list) and len(orig_imgs) == 2 and len(results) == 2:
            # Keep only the first result (RGB), as both share the same detections
            results = results[:1]
            LOGGER.debug("RTDETRMMPredictor: 双模态输入合并为单个结果对象")
        
        return results
    
    def write_results(self, i: int, p: Path, im: torch.Tensor, s: list) -> str:
        """
        Override parent's write_results to handle multi-modal result saving.
        
        Following YOLOMM pattern exactly:
        For dual-modal input: saves RGB result, X result, and side-by-side comparison
        For single-modal input: saves only the specified modality result
        
        Args:
            i (int): Index of the current result in the batch
            p (Path): Path to the original image file
            im (torch.Tensor): Preprocessed 6-channel tensor [RGB,RGB,RGB,X,X,X]
            s (list): List of result strings
            
        Returns:
            str: Result string with processing information
        """
        string = super().write_results(i, p, im, s)
        
        # Only process if saving is enabled
        if self.args.save:
            # Determine save mode based on input
            if self.is_dual_modal and hasattr(self, '_dual_input_detected') and self._dual_input_detected:
                # Dual-modal input: save RGB, X, and combined results
                self._save_multimodal_results(i, p, im)
            elif self.is_single_modal:
                # Single-modal input: modify filename to include modality
                self._update_single_modal_filename(p)
        
        return string
    
    def _save_multimodal_results(self, i: int, p: Path, im: torch.Tensor):
        """
        Save separate RGB and X modality results for dual-modal input.
        
        Creates three output files:
        1. filename.jpg - RGB modality inference result (following YOLOMM naming)
        2. filename_X.jpg - X modality inference result  
        3. filename_multimodal.jpg - Side-by-side comparison
        
        Args:
            i (int): Result index
            p (Path): Original image path
            im (torch.Tensor): 6-channel preprocessed tensor
        """
        try:
            # Get the result object
            result = self.results[i]
            
            # Get base filename without extension
            base_name = p.stem
            save_dir = self.save_dir
            
            # Get original images from cache if available
            if hasattr(self, '_orig_imgs_cache') and self._orig_imgs_cache is not None:
                # For dual-modal input, we should have RGB and X images
                if len(self._orig_imgs_cache) >= 2:
                    # First image is RGB, second is X modality
                    rgb_img = self._orig_imgs_cache[0]
                    x_img = self._orig_imgs_cache[1]
                    
                    # Ensure images are in RGB format
                    if isinstance(rgb_img, np.ndarray):
                        if rgb_img.ndim == 2:
                            rgb_img = cv2.cvtColor(rgb_img, cv2.COLOR_GRAY2RGB)
                        elif rgb_img.shape[2] == 4:
                            rgb_img = cv2.cvtColor(rgb_img, cv2.COLOR_BGRA2RGB)
                        elif rgb_img.shape[2] == 3 and rgb_img.dtype == np.uint8:
                            # Assume BGR format from cv2.imread
                            rgb_img = cv2.cvtColor(rgb_img, cv2.COLOR_BGR2RGB)
                    
                    if isinstance(x_img, np.ndarray):
                        if x_img.ndim == 2:
                            x_img = cv2.cvtColor(x_img, cv2.COLOR_GRAY2RGB)
                        elif x_img.shape[2] == 4:
                            x_img = cv2.cvtColor(x_img, cv2.COLOR_BGRA2RGB)
                        elif x_img.shape[2] == 3 and x_img.dtype == np.uint8:
                            # Assume BGR format from cv2.imread
                            x_img = cv2.cvtColor(x_img, cv2.COLOR_BGR2RGB)
                else:
                    # Fallback to tensor conversion if not enough original images
                    LOGGER.warning("使用tensor恢复图像作为备用方案")
                    rgb_tensor, x_tensor = self._separate_modalities(im)
                    rgb_img = self._tensor_to_image(rgb_tensor)
                    x_img = self._tensor_to_image(x_tensor)
            else:
                # Fallback to tensor conversion
                LOGGER.warning("原始图像缓存不可用，使用tensor恢复")
                rgb_tensor, x_tensor = self._separate_modalities(im)
                rgb_img = self._tensor_to_image(rgb_tensor)
                x_img = self._tensor_to_image(x_tensor)
            
            # Plot results on each modality
            # RGB modality result (使用原始文件名，不加后缀)
            rgb_annotated = self._plot_on_image(result, rgb_img, "RGB")
            rgb_path = save_dir / f"{base_name}.jpg"  # 按照设计，RGB预测图使用原始文件名
            cv2.imwrite(str(rgb_path), cv2.cvtColor(rgb_annotated, cv2.COLOR_RGB2BGR))
            
            # X modality result
            x_annotated = self._plot_on_image(result, x_img, "X")
            x_path = save_dir / f"{base_name}_X.jpg"
            cv2.imwrite(str(x_path), cv2.cvtColor(x_annotated, cv2.COLOR_RGB2BGR))
            
            # Create side-by-side comparison  
            multimodal_img = self._create_multimodal_comparison(rgb_annotated, x_annotated)
            multimodal_path = save_dir / f"{base_name}_multimodal.jpg"  # 小写multimodal
            cv2.imwrite(str(multimodal_path), cv2.cvtColor(multimodal_img, cv2.COLOR_RGB2BGR))
            
            LOGGER.info(f"保存多模态结果: RGB={rgb_path}, X={x_path}, 对比图={multimodal_path}")
            
        except Exception as e:
            LOGGER.error(f"保存多模态结果失败: {e}")
    
    def _update_single_modal_filename(self, p: Path):
        """
        Update the saved filename for single-modal input to include modality.
        
        Args:
            p (Path): Original image path
        """
        try:
            # Wait a bit to ensure file is saved
            import time
            time.sleep(0.2)
            
            # Get the default save path
            default_path = self.save_dir / p.name
            default_jpg_path = default_path.with_suffix('.jpg')
            
            # Create new filename with modality
            base_name = p.stem
            modality_upper = self.modality.upper() if self.modality else "UNKNOWN"
            new_path = self.save_dir / f"{base_name}_{modality_upper}.jpg"
            
            # Also check for the file with original extension
            original_ext_path = self.save_dir / p.name
            
            # Try multiple possible default filenames
            possible_files = [default_jpg_path, original_ext_path]
            
            for default_file in possible_files:
                if default_file.exists() and not new_path.exists():
                    # Rename the file
                    default_file.rename(new_path)
                    LOGGER.info(f"单模态文件重命名成功: {default_file.name} -> {new_path.name}")
                    break
            else:
                # If no file found to rename, log debug info
                LOGGER.warning(f"单模态文件重命名失败: 未找到默认保存的文件")
                LOGGER.debug(f"尝试查找的文件: {[str(f) for f in possible_files]}")
                LOGGER.debug(f"目录中的文件: {list(self.save_dir.glob('*'))}")
                
        except Exception as e:
            LOGGER.error(f"更新单模态文件名失败: {e}")
    
    def _separate_modalities(self, tensor: torch.Tensor) -> tuple:
        """
        Separate 6-channel tensor into RGB and X modality tensors.

        Args:
            tensor (torch.Tensor): 6-channel tensor [RGB,RGB,RGB,X,X,X]

        Returns:
            tuple: (rgb_tensor, x_tensor) each with 3 channels
        """
        if tensor.dim() == 3:
            # Single image: (6, H, W) → [RGB(0-2), X(3-5)]
            rgb_tensor = tensor[:3]   # First 3 channels (RGB)
            x_tensor = tensor[3:]     # Last 3 channels (X modality)
        else:
            # Batch: (B, 6, H, W) → [RGB(0-2), X(3-5)]
            rgb_tensor = tensor[:, :3]   # First 3 channels (RGB)
            x_tensor = tensor[:, 3:]     # Last 3 channels (X modality)
            
        return rgb_tensor, x_tensor
    
    def _tensor_to_image(self, tensor: torch.Tensor) -> np.ndarray:
        """
        Convert preprocessed tensor back to displayable image.
        
        Handles denormalization and format conversion.
        
        Args:
            tensor (torch.Tensor): Normalized tensor (C, H, W) or (B, C, H, W)
            
        Returns:
            np.ndarray: Image in HWC format with uint8 values
        """
        # Remove batch dimension if present
        if tensor.dim() == 4 and tensor.shape[0] == 1:
            tensor = tensor[0]
        elif tensor.dim() == 4:
            # For batch, take first image
            tensor = tensor[0]
            
        # Move to CPU if on GPU
        tensor = tensor.cpu()
        
        # Convert to HWC format
        img = tensor.permute(1, 2, 0).numpy()
        
        # Denormalize (assume standard ImageNet normalization)
        mean = np.array([0.485, 0.456, 0.406])
        std = np.array([0.229, 0.224, 0.225])
        img = img * std + mean
        
        # Clip to [0, 1] and convert to uint8
        img = np.clip(img, 0, 1)
        img = (img * 255).astype(np.uint8)
        
        # Handle grayscale
        if img.shape[2] == 1:
            img = cv2.cvtColor(img.squeeze(), cv2.COLOR_GRAY2RGB)
            
        return img
    
    def _plot_on_image(self, result, img: np.ndarray, modality_name: str) -> np.ndarray:
        """
        Plot detection results on a specific modality image.
        
        Args:
            result: Detection result object
            img (np.ndarray): Image to plot on (HWC format)
            modality_name (str): Name of the modality for labeling
            
        Returns:
            np.ndarray: Annotated image
        """
        # Create a copy to avoid modifying original
        img_copy = img.copy()
        
        # Use the result's plot method with the specific image
        annotated = result.plot(
            img=img_copy,
            line_width=self.args.line_width,
            boxes=self.args.show_boxes,
            conf=self.args.show_conf,
            labels=self.args.show_labels
        )
        
        # Add modality label
        h, w = annotated.shape[:2]
        label_bg_color = (0, 0, 0)  # Black background
        label_text_color = (255, 255, 255)  # White text
        
        # Draw modality label in top-left corner
        cv2.rectangle(annotated, (10, 10), (150, 40), label_bg_color, -1)
        cv2.putText(annotated, f"{modality_name} Modality", (15, 30),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, label_text_color, 2)
        
        return annotated
    
    def _create_multimodal_comparison(self, rgb_img: np.ndarray, x_img: np.ndarray) -> np.ndarray:
        """
        Create side-by-side comparison of RGB and X modality results.
        
        Args:
            rgb_img (np.ndarray): Annotated RGB image
            x_img (np.ndarray): Annotated X modality image
            
        Returns:
            np.ndarray: Combined side-by-side image
        """
        # Ensure both images have the same height
        h1, w1 = rgb_img.shape[:2]
        h2, w2 = x_img.shape[:2]
        
        if h1 != h2:
            # Resize to match heights
            target_h = max(h1, h2)
            if h1 < target_h:
                scale = target_h / h1
                new_w1 = int(w1 * scale)
                rgb_img = cv2.resize(rgb_img, (new_w1, target_h))
            else:
                scale = target_h / h2
                new_w2 = int(w2 * scale)
                x_img = cv2.resize(x_img, (new_w2, target_h))
        
        # Create side-by-side image
        gap = 10  # Gap between images
        combined_width = rgb_img.shape[1] + x_img.shape[1] + gap
        combined_height = max(rgb_img.shape[0], x_img.shape[0])
        
        # Create black canvas
        combined = np.zeros((combined_height, combined_width, 3), dtype=np.uint8)
        
        # Place images
        combined[:rgb_img.shape[0], :rgb_img.shape[1]] = rgb_img
        combined[:x_img.shape[0], rgb_img.shape[1] + gap:] = x_img
        
        # Add title
        title_height = 50
        final_img = np.zeros((combined_height + title_height, combined_width, 3), dtype=np.uint8)
        final_img[title_height:] = combined
        
        # Add title text
        cv2.putText(final_img, "RT-DETR Multi-Modal Detection Results", 
                    (combined_width // 2 - 200, 35),
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        
        return final_img