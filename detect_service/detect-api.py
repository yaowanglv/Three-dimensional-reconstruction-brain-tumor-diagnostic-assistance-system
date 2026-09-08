import warnings
warnings.filterwarnings('ignore')

import os
import io
import base64
import json
import time
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
from ultralytics import YOLO
from PIL import Image, ImageDraw
import numpy as np

app = Flask(__name__)
CORS(app)

# 配置
UPLOAD_FOLDER = 'uploads'
RESULT_FOLDER = 'results'
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif', 'bmp', 'tiff', 'webp'}

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(RESULT_FOLDER, exist_ok=True)

# 缓存加载的模型
model_cache = {}

# 脑肿瘤类别映射
tumor_type_mapping = {
    'gl': 'glioma',
    'me': 'meningioma',
    'pi': 'pituitary',
    'tumor': 'tumor'
}

brisc_class_mapping = {
    0: 'gl',
    1: 'me',
    2: 'pi'
}


def resolve_tumor_class(cls_id, raw_name):
    """Resolve BRISC numeric fallback classes when exported metadata is missing."""
    raw_text = str(raw_name).strip()
    numeric_text = raw_text

    if raw_text.lower().startswith('class'):
        numeric_text = raw_text[5:]

    if numeric_text.isdigit() and int(numeric_text) in brisc_class_mapping:
        return brisc_class_mapping[int(numeric_text)]

    if cls_id in brisc_class_mapping and raw_text in {'', str(cls_id), f'class{cls_id}'}:
        return brisc_class_mapping[cls_id]

    return raw_name


SEGMENT_FILL_COLOR = (220, 60, 60)
SEGMENT_OUTLINE_COLOR = (180, 20, 20)
SEGMENT_FILL_ALPHA = 96  # 96 / 255 ~= 0.38


def allowed_file(filename):
    """检查文件扩展名是否允许"""
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def parse_bool(value, default=False):
    if value is None:
        return default
    return str(value).strip().lower() in {'1', 'true', 'yes', 'on'}


def encode_pil_image(image, image_format='JPEG'):
    if image_format.upper() == 'JPEG' and image.mode != 'RGB':
        image = image.convert('RGB')
    buffered = io.BytesIO()
    image.save(buffered, format=image_format)
    return base64.b64encode(buffered.getvalue()).decode('utf-8')


def polygon_area(points):
    if len(points) < 3:
        return 0.0
    area = 0.0
    for i, (x1, y1) in enumerate(points):
        x2, y2 = points[(i + 1) % len(points)]
        area += x1 * y2 - x2 * y1
    return abs(area) / 2.0


def draw_label(draw, x, y, text, color):
    padding = 4
    try:
        bbox = draw.textbbox((x, y), text)
        text_width = bbox[2] - bbox[0]
        text_height = bbox[3] - bbox[1]
    except Exception:
        text_width = max(42, len(text) * 7)
        text_height = 14
    x = max(0, int(x))
    y = max(0, int(y) - text_height - padding * 2)
    draw.rectangle(
        [x, y, x + text_width + padding * 2, y + text_height + padding * 2],
        fill=color
    )
    draw.text((x + padding, y + padding), text, fill=(255, 255, 255))


def build_segmentation_images(image, polygons_data):
    result_pil = image.copy()
    mask_pil = Image.new('L', image.size, 0)
    overlay_layer = Image.new('RGBA', image.size, (0, 0, 0, 0))

    result_draw = ImageDraw.Draw(result_pil)
    mask_draw = ImageDraw.Draw(mask_pil)
    overlay_draw = ImageDraw.Draw(overlay_layer)

    for i, polygon in enumerate(polygons_data):
        raw_points = polygon.get('points') or []
        points = [(int(round(x)), int(round(y))) for x, y in raw_points]
        if len(points) < 3:
            continue

        fill_color = SEGMENT_FILL_COLOR
        outline_color = SEGMENT_OUTLINE_COLOR
        mask_draw.polygon(points, fill=255)
        overlay_draw.polygon(points, fill=(*fill_color, SEGMENT_FILL_ALPHA))
        overlay_draw.line(points + [points[0]], fill=(*outline_color, 230), width=3)
        result_draw.line(points + [points[0]], fill=outline_color, width=3)

        label = f"{polygon.get('tumor_type', polygon.get('label', 'tumor'))} {polygon.get('confidence', 0):.2f}"
        top_point = min(points, key=lambda item: item[1])
        draw_label(result_draw, top_point[0], top_point[1], label, outline_color)

    overlay_pil = Image.alpha_composite(image.convert('RGBA'), overlay_layer).convert('RGB')
    return result_pil, mask_pil, overlay_pil


def get_model(pt_path):
    """获取模型实例（带缓存）"""
    if pt_path not in model_cache:
        if not os.path.exists(pt_path):
            raise FileNotFoundError(f"模型文件不存在: {pt_path}")
        print(f"正在加载模型: {pt_path}")
        model_cache[pt_path] = YOLO(pt_path)
        print(f"模型加载完成: {pt_path}")
    return model_cache[pt_path]


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        'status': 'ok',
        'message': 'YOLO脑肿瘤检测服务运行正常',
        'timestamp': datetime.now().isoformat()
    })


@app.route('/detect', methods=['POST'])
def detect():
    """
    脑肿瘤图像检测接口

    请求参数:
    - file: 图像文件 (required)
    - pt_path: 模型文件路径 (required)
    - conf: 置信度阈值 (optional, default=0.25)
    - mode: detect/segment，segment模式返回多边形、二值掩码和半透明叠加图 (optional, default=detect)
    - save_result: 是否保存结果图像 (optional, default=true)
    - return_image: 是否返回结果图像base64 (optional, default=true)
    - return_mask_images: segment模式下是否返回mask/overlay base64 (optional, default=true)

    返回:
    - code: 状态码 (0成功, -1失败)
    - message: 提示信息
    - data: 检测结果
    """
    start_time = time.time()

    # 调试日志
    print(f"[DEBUG] 收到检测请求")
    print(f"[DEBUG] Content-Type: {request.content_type}")
    print(f"[DEBUG] Files: {list(request.files.keys())}")
    print(f"[DEBUG] Form: {list(request.form.keys())}")

    try:
        # 1. 获取请求参数
        if 'file' not in request.files:
            print(f"[ERROR] 请求中缺少 'file' 参数, files={request.files}")
            return jsonify({'code': -1, 'message': '请上传图像文件'}), 400

        file = request.files['file']
        print(f"[DEBUG] file.filename: {file.filename}")

        if file.filename == '':
            print(f"[ERROR] 文件名为空")
            return jsonify({'code': -1, 'message': '未选择文件'}), 400

        # 2. 获取pt路径和conf参数
        pt_path = request.form.get('pt_path')
        print(f"[DEBUG] pt_path: {pt_path}")
        if not pt_path:
            return jsonify({'code': -1, 'message': '请提供模型文件路径(pt_path)'}), 400

        conf = float(request.form.get('conf', 0.25))
        mode = request.form.get('mode', 'detect').strip().lower()
        segment_mode = mode in {'segment', 'seg', 'mask'}
        save_result = parse_bool(request.form.get('save_result'), True)
        return_image = parse_bool(request.form.get('return_image'), True)
        return_mask_images = parse_bool(request.form.get('return_mask_images'), segment_mode)
        print(
            f"[DEBUG] conf={conf}, mode={mode}, save_result={save_result}, "
            f"return_image={return_image}, return_mask_images={return_mask_images}"
        )

        # 3. 验证文件类型
        print(f"[DEBUG] 验证文件类型: {file.filename}, ext={file.filename.rsplit('.', 1)[1].lower() if '.' in file.filename else '无'}")
        if not allowed_file(file.filename):
            print(f"[ERROR] 不支持的文件类型: {file.filename}")
            return jsonify({'code': -1, 'message': f'不支持的文件类型，请上传: {ALLOWED_EXTENSIONS}'}), 400

        print(f"[DEBUG] 文件验证通过，准备读取图像...")

        # 4. 读取图像
        try:
            image_bytes = file.read()
            print(f"[DEBUG] 读取到图像数据: {len(image_bytes)} bytes")
            image = Image.open(io.BytesIO(image_bytes))
            print(f"[DEBUG] 图像打开成功: mode={image.mode}, size={image.size}")
        except Exception as e:
            print(f"[ERROR] 读取图像失败: {str(e)}")
            return jsonify({'code': -1, 'message': f'读取图像失败: {str(e)}'}), 400

        # 转换为RGB（如果是RGBA或其他模式）
        try:
            if image.mode != 'RGB':
                print(f"[DEBUG] 转换图像模式: {image.mode} -> RGB")
                image = image.convert('RGB')
        except Exception as e:
            print(f"[ERROR] 转换图像模式失败: {str(e)}")
            return jsonify({'code': -1, 'message': f'转换图像失败: {str(e)}'}), 400

        # 5. 加载模型
        print(f"[DEBUG] 准备加载模型: {pt_path}")
        try:
            model = get_model(pt_path)
            print(f"[DEBUG] 模型加载成功")
        except FileNotFoundError as e:
            print(f"[ERROR] 模型文件不存在: {pt_path}")
            return jsonify({'code': -1, 'message': str(e)}), 400
        except Exception as e:
            print(f"[ERROR] 模型加载失败: {str(e)}")
            return jsonify({'code': -1, 'message': f'模型加载失败: {str(e)}'}), 500

        # 6. 执行检测
        print(f"[DEBUG] 开始检测: conf={conf}, image_size={image.size}")

        try:
            # 将PIL图像转换为numpy数组
            img_array = np.array(image)
            print(f"[DEBUG] 图像转换为numpy数组: shape={img_array.shape}")

            # 运行预测
            print(f"[DEBUG] 调用模型预测...")
            results = model.predict(
                source=img_array,
                imgsz=640,
                conf=conf,
                save=False,
                verbose=False
            )
            print(f"[DEBUG] 模型预测完成")
        except Exception as e:
            print(f"[ERROR] 模型预测失败: {str(e)}")
            import traceback
            traceback.print_exc()
            return jsonify({'code': -1, 'message': f'模型预测失败: {str(e)}'}), 500

        # 7. 解析结果
        result = results[0]
        boxes_data = []
        polygons_data = []

        if result.boxes is not None:
            boxes = result.boxes
            for i, box in enumerate(boxes):
                # 获取边界框坐标
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()

                # 获取置信度和类别
                confidence = float(box.conf[0].cpu().numpy())
                cls_id = int(box.cls[0].cpu().numpy())
                cls_name = resolve_tumor_class(cls_id, result.names.get(cls_id, str(cls_id)))

                # 映射肿瘤类型
                mapped_type = tumor_type_mapping.get(cls_name, cls_name)

                boxes_data.append({
                    'id': i + 1,
                    'x1': round(float(x1), 2),
                    'y1': round(float(y1), 2),
                    'x2': round(float(x2), 2),
                    'y2': round(float(y2), 2),
                    'width': round(float(x2 - x1), 2),
                    'height': round(float(y2 - y1), 2),
                    'center_x': round(float((x1 + x2) / 2), 2),
                    'center_y': round(float((y1 + y2) / 2), 2),
                    'label': cls_name,
                    'tumor_type': mapped_type,
                    'confidence': round(confidence, 4),
                    'area_pixels': round(float((x2 - x1) * (y2 - y1)), 2)
                })

        if segment_mode and result.masks is not None:
            mask_segments = result.masks.xy
            normalized_segments = result.masks.xyn
            for i, segment in enumerate(mask_segments):
                if len(segment) < 3:
                    continue

                box_info = boxes_data[i] if i < len(boxes_data) else {}
                points = [[round(float(point[0]), 2), round(float(point[1]), 2)] for point in segment]
                normalized_points = []
                if i < len(normalized_segments):
                    normalized_points = [
                        [round(float(point[0]), 6), round(float(point[1]), 6)]
                        for point in normalized_segments[i]
                    ]

                polygons_data.append({
                    'id': i + 1,
                    'points': points,
                    'normalized_points': normalized_points,
                    'point_count': len(points),
                    'label': box_info.get('label', 'tumor'),
                    'tumor_type': box_info.get('tumor_type', 'tumor'),
                    'confidence': round(float(box_info.get('confidence', 0)), 4),
                    'area_pixels': round(polygon_area(points), 2),
                    'box': {
                        'x1': box_info.get('x1'),
                        'y1': box_info.get('y1'),
                        'x2': box_info.get('x2'),
                        'y2': box_info.get('y2')
                    } if box_info else None
                })

        # 8. 生成结果图像
        result_image_base64 = None
        result_image_path = None
        mask_image_base64 = None
        overlay_image_base64 = None

        if segment_mode:
            result_pil, mask_pil, overlay_pil = build_segmentation_images(image, polygons_data)

            if save_result:
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                result_filename = f"segment_{timestamp}_{file.filename}"
                result_image_path = os.path.join(RESULT_FOLDER, result_filename)
                result_pil.save(result_image_path)
                print(f"分割结果图像已保存: {result_image_path}")

            if return_image:
                result_image_base64 = encode_pil_image(result_pil, 'JPEG')

            if return_mask_images:
                mask_image_base64 = encode_pil_image(mask_pil, 'PNG')
                overlay_image_base64 = encode_pil_image(overlay_pil, 'JPEG')

        elif len(boxes_data) > 0:
            # 临时替换类别名称，让结果图像上显示全称（如 glioma 而不是 gl）
            original_names = dict(result.names)
            result.names = {
                k: tumor_type_mapping.get(resolve_tumor_class(k, v), resolve_tumor_class(k, v))
                for k, v in result.names.items()
            }

            # 使用ultralytics的绘图功能
            plotted_img = result.plot(line_width=2, font_size=0.6)
            result_pil = Image.fromarray(plotted_img)

            # 恢复原始类别名
            result.names = original_names

            # 保存结果图像
            if save_result:
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                result_filename = f"result_{timestamp}_{file.filename}"
                result_image_path = os.path.join(RESULT_FOLDER, result_filename)
                result_pil.save(result_image_path)
                print(f"结果图像已保存: {result_image_path}")

            # 转换为base64
            if return_image:
                result_image_base64 = encode_pil_image(result_pil, 'JPEG')

        # 9. 构建返回数据
        processing_time = round((time.time() - start_time) * 1000, 2)
        primary_items = polygons_data if segment_mode else boxes_data

        # 判断肿瘤类型：未检测到返回正常，检测到返回对应类型
        if len(primary_items) == 0:
            tumor_result = '正常'
            tumor_type_en = 'normal'
            best_confidence = 0
        else:
            # 取置信度最高的作为主要肿瘤类型
            best_item = max(primary_items, key=lambda x: x['confidence'])
            tumor_result = best_item['tumor_type']
            tumor_type_en = tumor_result
            best_confidence = best_item['confidence']

        detection_data = {
            'mode': 'segment' if segment_mode else 'detect',
            'tumor_detected': len(primary_items) > 0,
            'tumor_type': tumor_result,
            'tumor_type_en': tumor_type_en,
            'tumor_count': len(primary_items),
            'confidence': round(float(best_confidence), 4),
            'image_width': image.width,
            'image_height': image.height,
            'processing_time_ms': processing_time,
            'boxes': boxes_data,
            'polygons': polygons_data
        }

        response_data = {
            'code': 0,
            'message': '分割成功' if segment_mode else '检测成功',
            'data': {
                'detection': detection_data,
                'result_image_base64': result_image_base64,
                'mask_image_base64': mask_image_base64,
                'overlay_image_base64': overlay_image_base64,
                'result_image_path': result_image_path,
                'original_filename': file.filename,
                'model_path': pt_path,
                'conf_threshold': conf
            }
        }

        print(
            f"{'分割' if segment_mode else '检测'}完成: "
            f"发现{len(primary_items)}个目标, 耗时{processing_time}ms"
        )
        return jsonify(response_data)

    except Exception as e:
        print(f"检测失败: {str(e)}")
        import traceback
        traceback.print_exc()
        return jsonify({
            'code': -1,
            'message': f'检测失败: {str(e)}'
        }), 500


@app.route('/batch_detect', methods=['POST'])
def batch_detect():
    """
    批量检测接口

    请求参数:
    - files: 多个图像文件 (required)
    - pt_path: 模型文件路径 (required)
    - conf: 置信度阈值 (optional, default=0.25)

    返回:
    - 批量检测结果列表
    """
    try:
        if 'files' not in request.files:
            return jsonify({'code': -1, 'message': '请上传图像文件'}), 400

        files = request.files.getlist('files')
        if not files or files[0].filename == '':
            return jsonify({'code': -1, 'message': '未选择文件'}), 400

        pt_path = request.form.get('pt_path')
        if not pt_path:
            return jsonify({'code': -1, 'message': '请提供模型文件路径(pt_path)'}), 400

        conf = float(request.form.get('conf', 0.25))

        results = []
        for file in files:
            # 创建临时请求来复用单张检测逻辑
            with app.test_client() as client:
                data = {
                    'file': (io.BytesIO(file.read()), file.filename),
                    'pt_path': pt_path,
                    'conf': str(conf),
                    'return_image': 'false'  # 批量时不返回图像
                }
                response = client.post('/detect', data=data, content_type='multipart/form-data')
                result = json.loads(response.data)
                results.append(result)

        return jsonify({
            'code': 0,
            'message': f'批量检测完成，共{len(results)}张图像',
            'data': {
                'total': len(results),
                'success': sum(1 for r in results if r['code'] == 0),
                'failed': sum(1 for r in results if r['code'] != 0),
                'results': results
            }
        })

    except Exception as e:
        return jsonify({'code': -1, 'message': f'批量检测失败: {str(e)}'}), 500


@app.route('/models', methods=['GET'])
def list_models():
    """列出可用的模型文件"""
    models_dir = request.args.get('dir', '.')
    models = []

    try:
        for root, dirs, files in os.walk(models_dir):
            for file in files:
                if file.endswith('.pt'):
                    models.append({
                        'name': file,
                        'path': os.path.join(root, file),
                        'size': os.path.getsize(os.path.join(root, file))
                    })
        return jsonify({'code': 0, 'data': models})
    except Exception as e:
        return jsonify({'code': -1, 'message': str(e)}), 500


if __name__ == '__main__':
    print("=" * 50)
    print("YOLO 脑肿瘤检测服务启动中...")
    print(f"上传目录: {UPLOAD_FOLDER}")
    print(f"结果目录: {RESULT_FOLDER}")
    print("接口地址: http://localhost:5001")
    print("=" * 50)

    app.run(host='0.0.0.0', port=5001, debug=False)
