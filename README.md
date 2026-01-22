# DbApp - Data Broker Android Application

Android application demonstrating access to the Data Broker (Db) daemon via JNI-wrapped C++ client library.

## 概要

DbAppは、Data Broker（Db）デーモンにアクセスするAndroidアプリケーションです。
JNI経由でC++クライアントライブラリを使用し、TCP接続でDbからデータを取得・設定します。

## アーキテクチャ

```
┌─────────────────────────────────────────────┐
│ DbApp.apk (Android Application)             │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │ MainActivity.kt                       │  │
│  │ - GET/SET/LIST UI                     │  │
│  └──────────────┬───────────────────────┘  │
│                 │                           │
│  ┌──────────────▼───────────────────────┐  │
│  │ DbClient.kt (Kotlin wrapper)         │  │
│  │ - get(), set(), listAsMap()          │  │
│  └──────────────┬───────────────────────┘  │
│                 │ System.loadLibrary()      │
│  ┌──────────────▼───────────────────────┐  │
│  │ libdb_client_jni.so (JNI bridge)     │  │ ← APK内に埋め込み
│  │ - dbGet(), dbSet(), dbList()         │  │   (use_embedded_native_libs)
│  └──────────────┬───────────────────────┘  │
│                 │ FFI                       │
│  ┌──────────────▼───────────────────────┐  │
│  │ libdb_client.so (C++ library)        │  │ ← APK内に埋め込み
│  │ - TCP client (localhost:50051)       │  │
│  └──────────────┬───────────────────────┘  │
└─────────────────┼───────────────────────────┘
                  │ TCP/IP (localhost:50051)
                  ▼
        ┌─────────────────────┐
        │ db_daemon (Rust)    │
        │ - TCP server        │
        │ - HashMap storage   │
        └─────────────────────┘
```

## ファイル構成

```
src/vendor/db_app/
├── Android.bp                    # ビルド定義
├── AndroidManifest.xml           # INTERNET権限の宣言
├── db_app.mk                     # 製品パッケージ定義
├── README.md                     # このファイル
│
├── jni/
│   └── db_client_jni.cpp        # JNIブリッジ（C++ ↔ Kotlin/Java）
│
├── src/com/example/dbapp/
│   ├── DbClient.kt              # Kotlinラッパー（便利メソッド提供）
│   └── MainActivity.kt          # UI Activity
│
├── res/
│   ├── layout/
│   │   └── activity_main.xml   # UI レイアウト（GET/SET/LISTボタン）
│   └── values/
│       └── strings.xml          # 文字列リソース
│
└── sepolicy/                    # SELinuxポリシー（作成したが最終的には不使用）
    ├── file_contexts            # ライブラリのSELinuxラベル定義
    └── seapp_contexts           # アプリのSELinuxドメイン定義
```

## ビルド設定の重要なポイント

### 1. `use_embedded_native_libs: true` 🔑

**最も重要な設定**

```soong
android_app {
    name: "DbApp",
    // ...
    use_embedded_native_libs: true,  // ← これがないと動作しない！
}
```

**理由**:
- JNIライブラリ（`.so`）をAPK内に埋め込む
- SELinux制約を回避（`vendor_file`への読み取り権限不要）
- **Android Studioの標準的な方法**（3rdパーティ配布に最適）

**この設定がない場合**:
```
❌ ライブラリが/vendor/lib64/にインストールされる
❌ アプリ（platform_app domain）が vendor_file を読めない
❌ SELinux denial: denied { read } for name="libdb_client_jni.so"
❌ アプリがクラッシュ
```

### 2. `INTERNET` permission 🔑

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.dbapp">

    <uses-permission android:name="android.permission.INTERNET" />
    <!-- ↑ これがないと動作しない！ -->
```

**理由**:
- **localhostへのTCP接続でも`INTERNET`権限が必須**
- この権限がないとソケット接続が失敗する

**この設定がない場合**:
```
❌ TCP connect(localhost:50051) が失敗
❌ データ取得・設定ができない
```

### 3. `vendor: true`

```soong
android_app {
    name: "DbApp",
    // ...
    vendor: true,  // vendorパーティションに配置
}
```

**理由**:
- vendorパーティションのdbデーモンと同じ場所に配置
- `/vendor/priv-app/DbApp/DbApp.apk`にインストール

## ビルド方法

### モジュールのみビルド

```bash
# podman containerで実行
podman exec -it aosp-build-env bash -c "
    cd /work/src &&
    source build/envsetup.sh &&
    lunch aosp_car_dev-trunk_staging-eng &&
    m DbApp
"
```

または、ルートディレクトリから：

```bash
./build.sh DbApp
```

### フルビルド（vendor.img再作成）

```bash
./build.sh
```

## インストールとテスト

### 1. エミュレータ起動

```bash
./run-emulator.sh
```

### 2. APKインストール

```bash
src/out/host/linux-x86/bin/adb install -r -d \
    src/out/target/product/emulator_car64_x86_64/vendor/priv-app/DbApp/DbApp.apk
```

### 3. アプリ起動

```bash
src/out/host/linux-x86/bin/adb shell am start -n com.example.dbapp/.MainActivity
```

### 4. 動作確認

UIから以下の操作をテスト：

1. **LIST**: 全データを表示
2. **GET**: 特定のキーの値を取得
3. **SET**: キーと値を設定

## 動作確認済み機能

### ✅ LIST操作

エミュレータで確認済み：

```
All Data (5 entries):

client.counter = 42
client.test = hello_from_cpp
test.data = hello
test_key = test_value
vehicle.speed = 60
```

**確認内容**:
- ✅ アプリ起動成功
- ✅ JNIライブラリロード成功（APK内から）
- ✅ Dbデーモンとの通信成功（localhost:50051）
- ✅ データ取得成功

## 実装の詳細

### JNI Bridge (db_client_jni.cpp)

3つのネイティブメソッドを提供：

```cpp
extern "C" {
    // データ取得
    JNIEXPORT jstring JNICALL
    Java_com_example_dbapp_DbClient_dbGet(JNIEnv* env, jobject, jstring key);

    // データ設定
    JNIEXPORT jboolean JNICALL
    Java_com_example_dbapp_DbClient_dbSet(JNIEnv* env, jobject, jstring key, jstring value);

    // 全データ取得
    JNIEXPORT jstring JNICALL
    Java_com_example_dbapp_DbClient_dbList(JNIEnv* env, jobject);
}
```

**重要な実装ポイント**:
- ヘルパー関数（`jstring_to_string`等）は`extern "C"`の**外**に配置
- C linkageの制約（std::stringが使えない）を回避

### Kotlin Wrapper (DbClient.kt)

JNI関数を便利に使えるようラップ：

```kotlin
class DbClient {
    // JNIネイティブメソッド
    external fun dbGet(key: String): String?
    external fun dbSet(key: String, value: String): Boolean
    external fun dbList(): String?

    // 便利メソッド
    fun get(key: String, defaultValue: String = ""): String {
        return dbGet(key) ?: defaultValue
    }

    fun listAsMap(): Map<String, String> {
        val listStr = dbList() ?: return emptyMap()
        // "key1=value1, key2=value2" → Map変換
        // ...
    }

    companion object {
        init {
            System.loadLibrary("db_client_jni")
        }
    }
}
```

## トラブルシューティング

### アプリがクラッシュする（JNI library not found）

**原因**: `use_embedded_native_libs: true` が設定されていない

**解決方法**:
1. `Android.bp`に`use_embedded_native_libs: true`を追加
2. リビルド: `m DbApp`
3. 再インストール

### データが取得できない（No data in Data Broker）

**原因1**: `INTERNET`権限がない

**解決方法**:
1. `AndroidManifest.xml`に`<uses-permission android:name="android.permission.INTERNET" />`を追加
2. リビルド・再インストール

**原因2**: Dbデーモンが起動していない

**確認方法**:
```bash
adb shell "ps -A | grep db_daemon"
adb shell "netstat -tuln | grep 50051"
```

**解決方法**:
```bash
# Dbデーモンの手動起動
adb shell "/vendor/bin/db_daemon &"
```

### SELinux denial が出る

**通常は発生しない**（`use_embedded_native_libs: true`で回避済み）

**もし発生したら**:
```bash
# SELinuxをPermissiveに設定（デバッグ用）
adb shell "setenforce 0"
```

## 3rdパーティ配布について

この実装は**3rdパーティ開発者がAndroid Studioで使用できる**ように設計されています。

### 配布方法

1. **C++ Client Library**:
   - `libdb_client.so`をAARまたはJARに含める
   - Gradleでの依存関係設定例:
     ```gradle
     android {
         sourceSets {
             main {
                 jniLibs.srcDirs = ['libs']
             }
         }
     }
     ```

2. **JNI Wrapper**:
   - `libdb_client_jni.so`も同様にAARに含める

3. **Kotlin Wrapper**:
   - `DbClient.kt`をライブラリモジュールとして提供

### Android Studioでの使用例

```kotlin
// build.gradle
dependencies {
    implementation 'com.example:db-client:1.0.0'
}

// MainActivity.kt
import com.example.dbapp.DbClient

class MainActivity : AppCompatActivity() {
    private val dbClient = DbClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // データ取得
        val speed = dbClient.get("vehicle.speed")
        Log.d("DbApp", "Speed: $speed")

        // データ設定
        dbClient.set("app.status", "active")

        // 全データ取得
        val allData = dbClient.listAsMap()
        allData.forEach { (key, value) ->
            Log.d("DbApp", "$key = $value")
        }
    }
}
```

**重要**: AndroidManifest.xmlに`INTERNET`権限を追加する必要があります。

## 既知の制限事項

1. **プロトコル**: 簡易TCPプロトコル（gRPCではない）
   - 理由: AOSPにgRPCライブラリが標準含まれていない
   - 将来的にgRPC化する場合: `docs/poc-implementation-comparison.md`のフェーズ1.5参照

2. **認証**: 未実装
   - 現在は認証なし
   - フェーズ2で実装予定（UID/PID + PackageManager API）

3. **UDS非対応**: TCP/IP (localhost) のみ
   - フェーズ3でUDS対応予定

## 参考資料

- **C++ Client Library**: `src/vendor/db_client/README.md`
- **Db Daemon**: `src/vendor/db/README.md`
- **実装比較**: `docs/poc-implementation-comparison.md`
- **AOSP Build System**: `docs/android-bp-build-system.md`

## コミット履歴

```
fae5110 Phase 2: Add Android App with JNI wrapper for Data Broker access
ada8a26 Step 1: Add C++ client library for Data Broker
```

## ライセンス

Apache License 2.0
