import csv
import os
import re
import xml.etree.ElementTree as ET
from xml.dom import minidom

CSV_FILE = "translations.csv"
RES_DIR = "app/src/main/res"  # Adjust this path if needed

def escape_apostrophes(text: str) -> str:
    """
    Escapes apostrophes not already preceded by a backslash.
    E.g. "it's" becomes "it\\'s", but "it\\'s" remains unchanged.
    """
    return re.sub(r"(?<!\\)'", r"\\'", text or "")

# Read the CSV
with open(CSV_FILE, "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    header = next(reader)
    data = {locale: {} for locale in header[1:]}
    for row in reader:
        key = row[0]
        for i, locale in enumerate(header[1:]):
            data[locale][key] = row[i + 1]

# Write back to XML with pretty-printing and escaped apostrophes
for locale, translations in data.items():
    if locale == "en":
        folder = os.path.join(RES_DIR, "values")
        filepath = os.path.join(folder, "strings.xml")
        if os.path.exists(filepath):
            print(f"Skipping existing English file at {filepath}")
            continue
    else:
        folder = os.path.join(RES_DIR, f"values-{locale}")
        filepath = os.path.join(folder, "strings.xml")

    os.makedirs(folder, exist_ok=True)

    root = ET.Element("resources")
    for key, value in translations.items():
        safe_value = escape_apostrophes(value)
        elem = ET.SubElement(root, "string", name=key)
        elem.text = safe_value

    # Convert to a string and pretty-print with UTF-8 encoding
    rough_string = ET.tostring(root, encoding="utf-8")
    reparsed = minidom.parseString(rough_string)
    pretty_xml = reparsed.toprettyxml(indent="  ", encoding="utf-8")

    # Write bytes because we used encoding in toprettyxml()
    with open(filepath, "wb") as f:
        f.write(pretty_xml)

    print(f"Updated {filepath}")
