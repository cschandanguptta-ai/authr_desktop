#!/usr/bin/env python3
"""Copy the Android app sources into the Compose Desktop project and apply
the minimal, documented transformations required for desktop shims."""
import os
import re

SRC = "/home/z/my-project/source/app/src/main/java/com/example"
DST = "/home/z/my-project/authr-desktop/desktop/src/main/kotlin/com/example"

SKIP = {
    "MainActivity.kt",          # -> rewritten as Main.kt (Window + AppRoot)
    "StudioApplication.kt",     # -> init moved into Main.kt
    "di/AppModule.kt",          # -> rewritten for Koin-desktop + worker factory
    "utils/AudioEncoder.kt",    # -> rewritten to use ffmpeg (identical M4A output)
    "data/local/AppDatabase.kt",# -> rewritten for Room KMP builder + driver
}

def rel_path(root, path):
    return os.path.relpath(path, root)

count_copied = 0
transforms = []

for root, dirs, files in os.walk(SRC):
    for fname in files:
        if not fname.endswith(".kt"):
            continue
        src_file = os.path.join(root, fname)
        rel = rel_path(SRC, src_file)
        if rel in SKIP:
            transforms.append(f"SKIP (rewritten manually): {rel}")
            continue
        dst_file = os.path.join(DST, rel)
        os.makedirs(os.path.dirname(dst_file), exist_ok=True)
        text = open(src_file, encoding="utf-8").read()

        # 1) Moshi codegen -> reflection (moshi-kotlin KotlinJsonAdapterFactory)
        if "@JsonClass(generateAdapter = true)" in text:
            text = text.replace("@JsonClass(generateAdapter = true)",
                                "@JsonClass(generateAdapter = false)")
            transforms.append(f"moshi-reflection: {rel}")

        # 2) ApiService: build every Retrofit service with a Kotlin-aware Moshi
        if rel == "data/api/ApiService.kt":
            text = text.replace(
                "object ApiClient {\n    val okHttpClient: OkHttpClient by lazy {",
                "object ApiClient {\n\n"
                "    /** Desktop port: Kotlin reflection adapter so @JsonClass data classes\n"
                "     * with default values serialize identically to the codegen path. */\n"
                "    val moshi: com.squareup.moshi.Moshi = com.squareup.moshi.Moshi.Builder()\n"
                "        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())\n"
                "        .build()\n\n"
                "    val okHttpClient: OkHttpClient by lazy {")
            n = text.count("MoshiConverterFactory.create()")
            text = text.replace("MoshiConverterFactory.create()",
                                "MoshiConverterFactory.create(moshi)")
            transforms.append(f"api-service-moshi: {rel} ({n} factories)")

        # 3) StudioViewModel: logcat -> desktop Log ring buffer
        if rel == "ui/viewmodel/StudioViewModel.kt":
            text = text.replace(
                '                Runtime.getRuntime().exec(arrayOf("logcat", "-c"))',
                '                android.util.Log.clear()')
            old_fetch = re.search(
                r"    private fun fetchLogcat\(\): String \{.*?\n    \}\n",
                text, re.DOTALL)
            if old_fetch:
                new_fetch = (
                    "    private fun fetchLogcat(): String {\n"
                    "        return try {\n"
                    "            // Desktop port: read from the in-memory log ring buffer\n"
                    "            // (equivalent of `logcat -d -v time`).\n"
                    "            val all = android.util.Log.dump()\n"
                    "            val lines = all.split(\"\\n\").filter { it.isNotBlank() }\n"
                    "            val start = if (lines.size > 300) lines.size - 300 else 0\n"
                    "            val finalLines = lines.subList(start, lines.size)\n"
                    "            if (finalLines.isEmpty()) \"No logs captured yet.\"\n"
                    "            else finalLines.joinToString(\"\\n\")\n"
                    "        } catch (e: Exception) {\n"
                    "            \"Failed to read logs: ${e.message}\"\n"
                    "        }\n"
                    "    }\n")
                text = text[:old_fetch.start()] + new_fetch + text[old_fetch.end():]
                transforms.append(f"logcat->Log.dump: {rel}")

        open(dst_file, "w", encoding="utf-8").write(text)
        count_copied += 1

print(f"Copied {count_copied} files")
print("\n".join(transforms))
