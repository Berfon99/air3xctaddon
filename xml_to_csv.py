import os
import csv
import xml.etree.ElementTree as ET

# Paths
RES_DIR = "app/src/main/res"  # Adjust this if your res folder is in a different path
OUTPUT_CSV = "translations.csv"

# Collect all strings.xml paths
string_files = {}
for root, dirs, files in os.walk(RES_DIR):
    for file in files:
        if file == "strings.xml":
            locale = "en"  # default
            if "values-" in root:
                locale = root.split("values-")[1]
            string_files[locale] = os.path.join(root, file)

# Extract keys and translations
translations = {}
all_keys = set()

for locale, path in string_files.items():
    tree = ET.parse(path)
    root = tree.getroot()
    for string in root.findall("string"):
        key = string.get("name")
        value = string.text or ""
        all_keys.add(key)
        translations.setdefault(key, {})[locale] = value

# Export to CSV
locales = sorted(string_files.keys())
with open(OUTPUT_CSV, "w", encoding="utf-8", newline='') as f:
    writer = csv.writer(f)
    writer.writerow(["key"] + locales)
    for key in sorted(all_keys):
        row = [key] + [translations.get(key, {}).get(locale, "") for locale in locales]
        writer.writerow(row)

print(f"Exported translations to {OUTPUT_CSV}")
