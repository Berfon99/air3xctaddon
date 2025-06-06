import csv
import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

CSV_FILE = "translations.csv"
RES_DIR = "app/src/main/res"  # Adjust if needed

# Read the CSV
with open(CSV_FILE, "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader)
    keys = []
    data = {locale: {} for locale in header[1:]}
    for row in reader:
        key = row[0]
        for i, locale in enumerate(header[1:]):
            data[locale][key] = row[i + 1]

# Write back to XML with pretty-printing
for locale, translations in data.items():
    if locale == "en":
        folder = os.path.join(RES_DIR, "values")
        filepath = os.path.join(folder, "strings.xml")
        # Skip writing if the English file already exists
        if os.path.exists(filepath):
            print(f"Skipping existing English file at {filepath}")
            continue
    else:
        folder = os.path.join(RES_DIR, f"values-{locale}")

    os.makedirs(folder, exist_ok=True)
    filepath = os.path.join(folder, "strings.xml")

    root = ET.Element("resources")
    for key, value in translations.items():
        elem = ET.SubElement(root, "string", name=key)
        elem.text = value

    # Convert to a string and pretty-print
    rough_string = ET.tostring(root, 'utf-8')
    reparsed = minidom.parseString(rough_string)
    pretty_xml = reparsed.toprettyxml(indent="  ")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(pretty_xml)

    print(f"Updated {filepath}")
