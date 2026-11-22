# 專案進度總覽（2026-11-22）

目前專案已完成可編譯的基礎遊戲流程，核心釣魚小遊戲、魚類資源與 Data Pack 戰利品表已接上。最近一次執行 `gradlew.bat compileJava test` 成功。

## 已完成

| 項目 | 狀態 | 說明 |
| --- | --- | --- |
| NeoForge 專案骨架 | ✅ | Minecraft 1.21.1、NeoForge 1.21.1、Java 21、Mod ID `swingfishing`。 |
| 8 方位甩動判定 | ✅ | 支援視角 Yaw/Pitch 累積、方向判定、最小角度門檻與方向偏差。 |
| 伺服器權威小遊戲 | ✅ | 成功/失誤計數、逾時、冷卻、脫鉤與釣起結算均在伺服器執行。 |
| 玩家狀態保存 | ✅ | 使用 NeoForge Attachment，包含小遊戲狀態、浮標 UUID，以及浮標所在方塊座標/維度。 |
| 網路同步與 HUD | ✅ | 使用 Custom Packet Payload 同步狀態，客戶端渲染方向、進度、失誤與結束提示。 |
| 安全清理流程 | ✅ | 收竿、浮標消失、死亡、登出、切換維度、關閉功能等情況會清理狀態。 |
| 魚類內容 | ✅ | `ModItems` 已註冊 140 條魚，並有模型、材質、英文/繁中語言鍵與魚類標籤。 |
| 魚圖鑑 | ✅ | Fish Book 與 Patchouli 魚圖鑑已建立，目前有 140 個魚類條目。 |
| Data Pack 戰利品表 | ✅ | 位於 `data/swingfishing/loot_table/fishing/`（Minecraft 1.21 正確單數路徑）；`ocean` 56、`river` 63、`nether` 10、`end` 11，共涵蓋 140 條魚。 |
| 浮標位置判定 | ✅ | 戰利品表依「浮標所在方塊」的維度與生態域選擇，不再以玩家位置為主要依據。 |
| 單元測試 | ✅ | 已有 `FishingMiniGameDataTest` 與 `SwipeDirectionTest`，目前 Gradle 測試通過。 |

## 目前的主要流程

1. 玩家使用釣竿，原版 `ItemFishedEvent` 啟動甩動小遊戲。
2. 伺服器記錄釣魚浮標的 UUID、方塊位置與維度。
3. 客戶端採樣視角變化，透過 Payload 傳送 Yaw/Pitch 增量。
4. 伺服器驗證方向、累計成功/失誤與時間限制。
5. 成功完成後，依浮標位置選擇四個 Loot Table 之一並發放戰利品。
6. 若得到模組魚類，會更新 Fish Book 的發現紀錄並同步提示。

## 開發位置與修改指南

### 想改釣魚小遊戲規則

- 小遊戲主流程、成功/失誤、逾時、脫鉤、結算：
  `src/main/java/com/swingfishing/fishing/game/FishingMiniGameManager.java`
- 8 方位名稱、角度公式與方向判定：
  `src/main/java/com/swingfishing/fishing/game/SwipeDirection.java`
- 玩家小遊戲狀態、計數器、浮標 UUID、浮標方塊位置與存檔欄位：
  `src/main/java/com/swingfishing/fishing/data/FishingMiniGameData.java`
- 玩家 Attachment 註冊：
  `src/main/java/com/swingfishing/fishing/data/ModAttachments.java`
- 魚上鉤事件、每 tick、死亡、登出、換維度與收竿清理：
  `src/main/java/com/swingfishing/fishing/game/FishingGameEvents.java`

修改規則或狀態欄位時，需同步檢查 `FishingMiniGameData` 的 `start`、`clear`、`serializeNBT`、`deserializeNBT`，以及對應的單元測試。

### 想改設定值

修改位置：

`src/main/java/com/swingfishing/config/ModConfig.java`

新增設定時，請同時更新：

1. `ModConfigSpec` 欄位、預設值與範圍。
2. `src/main/resources/assets/swingfishing/lang/en_us.json`。
3. `src/main/resources/assets/swingfishing/lang/zh_tw.json`。
4. README 的「設定值」清單。

### 想新增或修改魚

一條魚通常需要同步處理下列位置：

1. 物品註冊：`src/main/java/com/swingfishing/item/ModItems.java`
2. 物品模型：`src/main/resources/assets/swingfishing/models/item/<魚 ID>.json`
3. 魚的材質：`src/main/resources/assets/swingfishing/textures/item/<魚 ID>.png`
4. 名稱翻譯：`src/main/resources/assets/swingfishing/lang/en_us.json` 與 `zh_tw.json`
5. 魚類標籤：`src/main/resources/data/swingfishing/tags/item/mod_fishes.json` 或 `fishes.json`
6. 魚圖鑑條目：`src/main/resources/assets/swingfishing/patchouli_books/fishing_guide/en_us/entries/fish/<魚 ID>.json`
7. Loot Table 中的生態域分類與權重。

魚 ID 必須使用小寫 snake_case，並且註冊名稱、模型檔名、材質檔名、翻譯鍵與 Loot Table 的 `swingfishing:<魚 ID>` 完全一致。

### 想修改釣魚戰利品與機率

目前使用 Data Pack Loot Table，不要再到 Java 裡新增魚名或機率：

`src/main/resources/data/swingfishing/loot_table/fishing/`

- `ocean.json`：浮標位於海洋生態域。
- `river.json`：一般世界非海洋生態域。
- `nether.json`：地獄維度。
- `end.json`：末地維度。

每個表的 `entries` 使用 `weight` 控制權重；原版寶藏與垃圾條目也保留在表中。Java 端選表的位置是 `FishingMiniGameManager.selectFishingLootTable`，目前依浮標記錄的維度與方塊生態域判定。

### 想修改客戶端 HUD 或滑鼠視角取樣

- 視角取樣、封包發送與 HUD 繪製：
  `src/main/java/com/swingfishing/client/ClientEvents.java`
- 客戶端同步狀態快取：
  `src/main/java/com/swingfishing/client/ClientMiniGameState.java`
- 按鍵註冊：
  `src/main/java/com/swingfishing/client/KeyBindings.java`

客戶端不可直接判定成功或發放物品；伺服器只接受 `SwipeInputPayload`，並在 `ServerPayloadHandler` 驗證後交給 `FishingMiniGameManager`。

### 想修改網路封包

- 封包定義：`src/main/java/com/swingfishing/network/SwipeInputPayload.java`、`MiniGameSyncPayload.java`
- 封包註冊：`src/main/java/com/swingfishing/network/NetworkRegistry.java`
- 伺服器接收：`src/main/java/com/swingfishing/network/ServerPayloadHandler.java`
- 客戶端接收：`src/main/java/com/swingfishing/network/ClientPayloadHandler.java`
- 伺服器通知客戶端：`src/main/java/com/swingfishing/network/NetworkClientNotifier.java`

新增或修改封包欄位時，必須同步更新 record、`STREAM_CODEC`、發送端、接收端與測試。

### 想新增除錯指令

修改：

`src/main/java/com/swingfishing/fishing/game/DebugCommands.java`

目前除錯指令以 `/sfdebug` 開頭；除錯啟動的小遊戲會使用玩家 UUID 作為假浮標，不代表正常釣魚流程。

### 修改後的檢查流程

在專案根目錄 `C:\SwingFishing Mod` 執行：

```powershell
.\gradlew.bat compileJava test
```

至少確認 Java 編譯、單元測試、資源打包與 Loot Table 路徑都成功，再進行遊戲內測試。

## 設定值

目前 COMMON 設定包含：

- `enableHeadSwingFishing`：是否啟用小遊戲。
- `requiredSuccessSwipes`：完成一次釣魚所需成功甩動次數。
- `maxFailedSwipes`：允許的最大失誤次數。
- `swipeTimeoutTicks`：單一方向的作答時間。
- `minAngleDegrees`：有效甩動的最小角度。
- `swipeCooldownTicks`：成功或失誤後的輸入冷卻時間。

## 後續建議

- 進行實際遊戲內測試：海洋、一般河流、地獄、末地各至少完成一次釣魚。
- 測試收竿、浮標消失、切換維度、死亡與關閉設定時 HUD 和狀態是否都正確清除。
- 確認 `models/item` 內一般模型與 `item.swingfishing.*` 模型是否都需要；目前兩組檔案都保留，尚未刪除。
- 將 Loot Table 權重與生態域分類整理成更容易調整的資料規格，方便日後平衡機率。

---

NeoForge 1.21.1 釣魚模組 — AI 代碼生成規範（含雙語與易懂文案）
專案基本資訊：

專案根目錄／主資料夾：SwingFishing Mod

Mod ID：swingfishing

Root Package：com.swingfishing

目標版本：Minecraft 1.21.1 / NeoForge 1.21.1（Java 21、UTF-8 編碼、ModDevGradle + Mixin）

角色與任務：你正在協助開發位於 SwingFishing Mod 專案中的 NeoForge 1.21.1 釣魚模組（modid = swingfishing、package = com.swingfishing），核心特色是「魚上鉤後以 8 方位視角甩動（類似 iPhone Face ID 擺頭引導）進行溜魚與收線判定」。請嚴格遵守以下所有規定生成純 Java 程式碼與語言檔：

1. 語言、編碼與版本規範
   使用 純 Java（Java 21 語法）與 UTF-8 編碼撰寫。

目標平台固定為 Minecraft 1.21.1 與 NeoForge 1.21.1。

必須使用 NeoForge 1.21.1 對應的正確事件與 API（例如 ModConfigSpec、ModContainer#registerConfig、RegisterPayloadHandlersEvent、IPayloadRegistrar、CustomPacketPayload、StreamCodec、DeferredRegister 註冊 AttachmentType、RegisterGuiLayersEvent、PlayerTickEvent.Post、釣魚相關事件等）。

禁止混用舊版 Forge 或其他大版本 API；若有小版本映射或事件細節需確認，請在註解中明確標註並附上完整 import。

禁止省略核心邏輯或寫 // TODO: 略；每次新增或修改資料結構（列舉、狀態欄位、Config 欄位、封包欄位）時，必須同步列出受影響的檔案與欄位名稱。

2. 套件與模組分層規範
   com.swingfishing：主模組類別（@Mod("swingfishing") 建構子接收 IEventBus 與 ModContainer）與事件註冊。

com.swingfishing.config：ModConfigSpec 設定檔定義與註冊。

com.swingfishing.fishing.game：8 方位列舉（SwipeDirection）、視角增量（Delta Yaw / Delta Pitch）判定、小遊戲狀態機與結算。

com.swingfishing.fishing.data：玩家狀態儲存（AttachmentType）與資料結構。

com.swingfishing.network：CustomPacketPayload、StreamCodec 與封包處理器。

com.swingfishing.client：視角增量取樣、HUD 覆蓋層渲染與 Client 視覺回饋（嚴禁在此套件直接呼叫 Server 獨有類別）。

語言資源檔：src/main/resources/assets/swingfishing/lang/en_us.json 與 src/main/resources/assets/swingfishing/lang/zh_tw.json。

3. 雙語支援與易懂文案規範（en_us & zh_tw）
   同時提供英文與繁體中文：所有面向玩家的文字（提示訊息、狀態說明、方向名稱、設定註解/說明、戰利品或操作提示）都必須同時提供 en_us（英文） 與 zh_tw（繁體中文） 版本。

禁止硬編碼顯示文字：Java 程式碼中顯示給玩家的字串一律使用 Component.translatable("...") 搭配清楚的翻譯鍵（例如 hud.swingfishing.direction.up_right、message.swingfishing.escaped），不要在程式碼裡把中文或英文訊息寫死。

口語化、直覺易懂：文案要像一般玩家一眼就看得懂的日常用語，避免生硬的程式術語或過於文言的翻譯（例如用「往右上甩視角！」而不是「請執行右上方角位移向量偏移」；英文用 "Flick view up-right!" 或 "Swing up-right!"）。

每次新增翻譯鍵時，必須同步列出 en_us.json 與 zh_tw.json 的對應鍵值。

4. Server / Client 邊界與網路安全規範
   Server 端為唯一權威：小遊戲狀態轉換、計時、成功次數、失誤次數、脫鉤判定與戰利品發放皆由 Server 端判定。

封包安全：所有 CustomPacketPayload 處理器必須透過 context.enqueueWork(...) 回到主執行緒執行，並驗證玩家實體與目前釣魚狀態是否有效。

端點隔離：涉及 Minecraft.getInstance()、GuiGraphics、PoseStack、Client 渲染或 Client 輸入事件的程式碼，必須放在 client 套件或透過 Client 專用事件註冊，確保 Dedicated Server 啟動時不會發生 NoClassDefFoundError。

5. 8 方位視角甩動與防呆機制規範
   統一使用單一 8 方位列舉（UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT），並於註解明寫視角變化正負號約定（Yaw 左右轉動、Pitch 上下俯仰）。

凡遇到以下任一情況，必須立即清理玩家小遊戲狀態並通知 Client 隱藏 HUD：

玩家收竿、切換快捷列選中格、丟棄釣竿或離開釣魚狀態

浮標實體被移除、離開水域或找不到對應浮標

玩家死亡、切換維度或登出伺服器

enableHeadSwingFishing 被設為 false

累計失誤達到上限（脫鉤）或成功次數達標（釣起）

6. Config 設定檔（COMMON）規範
   透過 ModConfigSpec 註冊 ModConfig.Type.COMMON，並提供以下欄位（含合理範圍與清楚說明註解）：

enableHeadSwingFishing（boolean，預設 true）：是否啟用 8 方位視角擺頭釣魚小遊戲。

requiredSuccessSwipes（int，預設 5，範圍 1..20）：單次釣魚需成功跟上的轉動次數。

maxFailedSwipes（int，預設 3，範圍 1..10）：允許失誤上限，達到即脫鉤。

swipeTimeoutTicks（int，預設 24，範圍 5..200，單位 ticks）：單一方向指令作答時限。

minAngleDegrees（double，預設 15.0，範圍 1.0..90.0，單位 degrees）：判定為有效甩動的最小視角變化角度。

即時讀取與熱更新安全：每次運算皆動態讀取最新 Config 值；若 enableHeadSwingFishing 為 false，不攔截原版收竿、不發送小遊戲封包、不渲染 8 方位 HUD，且若正在進行中則立即安全清理狀態與 HUD。

7. 輸出格式規範
   每次生成程式碼時，先簡述該類別的職責與上下游銜接點。

接著提供完整、可直接編譯的純 Java 程式碼（若有涉及翻譯鍵，一併附上 en_us.json 與 zh_tw.json 片段）。

最後列出測試重點與常見陷阱（註明單位 ticks、degrees 與關鍵角度/向量公式）。
