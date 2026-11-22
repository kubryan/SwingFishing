import os
import json
import shutil

# ==========================================
# 設定路徑
# ==========================================
MOD_ID = "swingfishing"
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(BASE_DIR, "src", "main")
JAVA_DIR = os.path.join(SRC_DIR, "java", "com", "swingfishing", "item")
RES_DIR = os.path.join(SRC_DIR, "resources", "assets", MOD_ID)
DATA_DIR = os.path.join(SRC_DIR, "resources", "data", MOD_ID)
MODEL_DIR = os.path.join(RES_DIR, "models", "item")
TEXTURE_DIR = os.path.join(RES_DIR, "textures", "item")
LANG_DIR = os.path.join(RES_DIR, "lang")
TAG_DIR = os.path.join(DATA_DIR, "tags", "item")

# Patchouli 專用路徑
PATCHOULI_DIR = os.path.join(RES_DIR, "patchouli_books", "fishing_guide")
PATCHOULI_BOOK = os.path.join(PATCHOULI_DIR, "book.json")
PATCHOULI_CATEGORIES = os.path.join(PATCHOULI_DIR, "en_us", "categories")
PATCHOULI_ENTRIES = os.path.join(PATCHOULI_DIR, "en_us", "entries", "fish")

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
RAW_TEXTURES_DIR = os.path.join(TOOLS_DIR, "raw_textures")
TRANS_FILE = os.path.join(TOOLS_DIR, "translations.json")

# 建立所有需要的資料夾
for d in [JAVA_DIR, MODEL_DIR, TEXTURE_DIR, LANG_DIR, TAG_DIR, PATCHOULI_CATEGORIES, PATCHOULI_ENTRIES]:
    os.makedirs(d, exist_ok=True)

# ==========================================
# 1. 讀取翻譯檔
# ==========================================
try:
    with open(TRANS_FILE, "r", encoding="utf-8") as f:
        translations = json.load(f)
except FileNotFoundError:
    print("❌ 找不到 tools/translations.json，請先建立翻譯檔！")
    exit()

# ==========================================
# 2. 生成 Java 註冊檔 (ModItems.java)
# ==========================================
java_code = f"""package com.swingfishing.item;

import com.swingfishing.SwingFishing;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自動生成的魚類與物品註冊類別。
 */
public final class ModItems {{
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SwingFishing.MOD_ID);

    // 魚類圖鑑
    public static final DeferredItem<Item> FISH_BOOK = ITEMS.registerItem("fish_book", FishBookItem::new, new Item.Properties());

"""

for fish_name in translations.keys():
    raw_name = fish_name.replace("item.swingfishing.", "")
    
    # 跳過 fish_book，避免重複註冊
    if raw_name == "fish_book":
        continue
        
    var_name = raw_name.upper()
    java_code += f"    public static final DeferredItem<Item> {var_name} = ITEMS.registerSimpleItem(\"{raw_name}\", new Item.Properties());\n"

java_code += "}\n"

with open(os.path.join(JAVA_DIR, "ModItems.java"), "w", encoding="utf-8") as f:
    f.write(java_code)
print("✅ 已生成 ModItems.java")

# ==========================================
# 3. 生成模型 JSON 與複製貼圖
# ==========================================
model_generated = 0
texture_copied = 0

# 處理 FISH_BOOK 的模型與貼圖
fish_book_model = {
    "parent": "minecraft:item/generated",
    "textures": { "layer0": f"{MOD_ID}:item/fish_book" }
}
with open(os.path.join(MODEL_DIR, "fish_book.json"), "w", encoding="utf-8") as f:
    json.dump(fish_book_model, f, indent=2)

src_book_tex = os.path.join(RAW_TEXTURES_DIR, "fish_book.png")
if os.path.exists(src_book_tex):
    shutil.copy2(src_book_tex, os.path.join(TEXTURE_DIR, "fish_book.png"))

# 處理其他魚類
for fish_key in translations.keys():
    fish_name = fish_key.replace("item.swingfishing.", "")
    if fish_name == "fish_book": continue
    
    # 生成 JSON 模型
    model_json = {
        "parent": "minecraft:item/generated",
        "textures": { "layer0": f"{MOD_ID}:item/{fish_name}" }
    }
    with open(os.path.join(MODEL_DIR, f"{fish_name}.json"), "w", encoding="utf-8") as f:
        json.dump(model_json, f, indent=2)
    model_generated += 1

    # 複製貼圖
    src_tex = os.path.join(RAW_TEXTURES_DIR, f"{fish_name}.png")
    dst_tex = os.path.join(TEXTURE_DIR, f"{fish_name}.png")
    if os.path.exists(src_tex):
        shutil.copy2(src_tex, dst_tex)
        texture_copied += 1

print(f"✅ 已生成 {model_generated} 個魚類 item model")
print(f"✅ 已複製 {texture_copied} 張魚類貼圖")

# ==========================================
# 4. 生成物品標籤 Tags (Item Tags)
# ==========================================
mod_fish_ids = [f"{MOD_ID}:{k.replace('item.swingfishing.', '')}" for k in translations.keys() if "fish_book" not in k]

with open(os.path.join(TAG_DIR, "mod_fishes.json"), "w", encoding="utf-8") as f:
    json.dump({"replace": False, "values": mod_fish_ids}, f, indent=2)

fishes_tag = {
    "replace": False,
    "values": [
        "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
        {"id": f"#{MOD_ID}:mod_fishes", "required": False}
    ]
}
with open(os.path.join(TAG_DIR, "fishes.json"), "w", encoding="utf-8") as f:
    json.dump(fishes_tag, f, indent=2)
print("✅ 已生成 items tags")

# ==========================================
# 5. 生成 Patchouli 圖鑑資料
# ==========================================
# 5a. 生成 book.json
book_json = {
    "name": "item.swingfishing.fish_book",
    "landing_text": "message.swingfishing.fish_book.landing",
    "use_resource_pack": True,
    "book_texture": "patchouli:textures/gui/book_blue.png",
    "model": "swingfishing:fish_book",
    "text_color": "000000",
    "header_color": "333333",
    "nameplate_color": "FFDD00",
    "version": 1
}
with open(PATCHOULI_BOOK, "w", encoding="utf-8") as f:
    json.dump(book_json, f, indent=2)

# 5b. 生成 categories/fish.json
category_json = {
    "name": "category.swingfishing.fish",
    "description": "category.swingfishing.fish.desc",
    "icon": f"{MOD_ID}:agave_bream",
    "sortnum": 0
}
with open(os.path.join(PATCHOULI_CATEGORIES, "fish.json"), "w", encoding="utf-8") as f:
    json.dump(category_json, f, indent=2)

# 5c. 批量生成 entries/fish/*.json
entry_count = 0
for fish_key in translations.keys():
    fish_name = fish_key.replace("item.swingfishing.", "")
    if fish_name == "fish_book": continue

    entry_json = {
        "name": f"item.swingfishing.{fish_name}",
        "category": "swingfishing:fish",
        "icon": f"{MOD_ID}:{fish_name}",
        "pages": [
            {
                "type": "patchouli:spotlight",
                "item": f"{MOD_ID}:{fish_name}",
                "title": f"item.swingfishing.{fish_name}",
                "text": f"entry.swingfishing.{fish_name}.desc"
            }
        ]
    }
    
    entry_file_path = os.path.join(PATCHOULI_ENTRIES, f"{fish_name}.json")
    with open(entry_file_path, "w", encoding="utf-8") as f:
        json.dump(entry_json, f, indent=2)
    entry_count += 1

print(f"✅ 已生成 Patchouli 書本結構與 {entry_count} 個魚類條目")

# ==========================================
# 6. 更新語言檔
# ==========================================
lang_files = {
    "zh_tw.json": translations,
    "en_us.json": {k: k.replace("item.swingfishing.", "").replace("_", " ").title() for k in translations.keys()}
}

for lang_file, updates in lang_files.items():
    lang_path = os.path.join(LANG_DIR, lang_file)
    
    if os.path.exists(lang_path):
        with open(lang_path, "r", encoding="utf-8") as f:
            try:
                existing_data = json.load(f)
            except json.JSONDecodeError:
                existing_data = {}
    else:
        existing_data = {}

    existing_data.update(updates)
    
    # 補齊 Patchouli 相關翻譯
    if lang_file == "zh_tw.json":
        existing_data["item.swingfishing.fish_book"] = "魚類圖鑑"
        existing_data["message.swingfishing.fish_book.landing"] = "這裡記錄了你所有釣過的魚類。"
        existing_data["category.swingfishing.fish"] = "魚類圖鑑"
        existing_data["category.swingfishing.fish.desc"] = "所有已發現的魚類。"
    else:
        existing_data["item.swingfishing.fish_book"] = "Fish Book"
        existing_data["message.swingfishing.fish_book.landing"] = "Records of all fish you have caught."
        existing_data["category.swingfishing.fish"] = "Fish Catalog"
        existing_data["category.swingfishing.fish.desc"] = "All discovered fishes."
        
    # 補齊每條魚的預設描述（防止圖鑑中顯示原始代碼）
    for fish_key in translations.keys():
        fish_name = fish_key.replace("item.swingfishing.", "")
        if fish_name == "fish_book": continue
        
        desc_key = f"entry.swingfishing.{fish_name}.desc"
        if desc_key not in existing_data:
            if lang_file == "zh_tw.json":
                existing_data[desc_key] = "這是一種神秘的魚類，似乎可以在釣魚時發現。"
            else:
                existing_data[desc_key] = "A mysterious fish that can be found while fishing."

    with open(lang_path, "w", encoding="utf-8") as f:
        json.dump(existing_data, f, indent=2, ensure_ascii=False)
        
print("✅ 已更新 en_us.json 與 zh_tw.json (包含圖鑑描述佔位符)")
print("🎉 自動化生成完成！請重新整理 Gradle 並進遊戲測試。")