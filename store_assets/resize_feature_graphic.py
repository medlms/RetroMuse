from PIL import Image

src_path = r"C:\Users\LMS\.gemini\antigravity\brain\da4be007-ed5a-4315-8813-6728db642486\retromuse_feature_graphic_source_1786237539116.jpg"
target_paths = [
    r"C:\Users\LMS\AndroidStudioProjects\RetroMuse\store_assets\feature_graphic.png",
    r"C:\RetroMuse\store_assets\feature_graphic.png"
]

img = Image.open(src_path)
# Crop and resize to exactly 1024 x 500
# Target aspect ratio is 1024/500 = 2.048
# Source aspect ratio is 16/9 = 1.777...
# Since target is wider than source, we crop top/bottom of source to match 2.048 aspect ratio, then resize.

src_w, src_h = img.size
target_aspect = 1024.0 / 500.0
current_aspect = float(src_w) / float(src_h)

if current_aspect > target_aspect:
    # Source is wider than target aspect, crop left/right
    new_w = int(src_h * target_aspect)
    offset = (src_w - new_w) // 2
    img_cropped = img.crop((offset, 0, src_w - offset, src_h))
else:
    # Source is taller than target aspect, crop top/bottom
    new_h = int(src_w / target_aspect)
    offset = (src_h - new_h) // 2
    img_cropped = img.crop((0, offset, src_w, src_h - offset))

final_img = img_cropped.resize((1024, 500), Image.Resampling.LANCZOS)

for path in target_paths:
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)
    final_img.save(path, "PNG")

print("Feature graphic cropped and resized to 1024x500 successfully!")
