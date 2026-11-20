import io
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image


APP = "io.github.cmix7777.kazhdyidnevnik"
OUT = Path("preview-output")
UI_DUMP = "/sdcard/preview.xml"


def adb(*args, data=None):
    result = subprocess.run(
        ["adb", *args], input=data, capture_output=True, check=False, timeout=45
    )
    if result.returncode:
        raise RuntimeError(
            f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}"
        )
    return result.stdout


def find_node(attribute, text):
    for _ in range(6):
        try:
            adb("shell", "uiautomator", "dump", UI_DUMP)
            layout = ElementTree.fromstring(adb("exec-out", "cat", UI_DUMP))
            for node in layout.iter("node"):
                if node.get(attribute) == text and node.get("package") == APP:
                    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        except (RuntimeError, ElementTree.ParseError):
            pass
        time.sleep(1)
    raise RuntimeError(f"Screen marker did not appear: {text}")


def frame():
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")


def holiday_card(image, marker, scale):
    # Use a majority of pixels so snow, lamps, and tree branches do not break the crop.
    left = round(20 * scale)
    inset = round(8 * scale)
    columns = range(left + inset, image.width - left - inset, max(1, round(5 * scale)))

    def green_fraction(y):
        green_pixels = 0
        for x in columns:
            red, green, blue = image.getpixel((x, y))
            if 25 <= green <= 100 and green - red >= 9 and green - blue >= 2:
                green_pixels += 1
        return green_pixels / len(columns)

    label_top = marker[1]
    candidate_top = max(0, label_top - round(78 * scale))
    candidate_bottom = min(image.height, label_top + round(105 * scale))
    rows = [y for y in range(candidate_top, candidate_bottom) if green_fraction(y) >= 0.62]
    if rows and 100 * scale <= rows[-1] - rows[0] <= 167 * scale:
        top, bottom = rows[0], rows[-1] + 1
    else:
        # Conservative bounds stay inside the card when we cannot detect its edges.
        print("Using a tighter crop around the countdown")
        top = max(0, label_top - round(49 * scale))
        bottom = min(image.height, label_top + round(75 * scale))
    return image.crop((left + inset, top, image.width - left - inset, bottom))


def main():
    OUT.mkdir(exist_ok=True)
    print("Installing debug APK")
    adb("install", "-g", "app/build/outputs/apk/debug/app-debug.apk")
    print("Preparing an offline emulator")
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    density = adb("shell", "wm", "density").decode()
    scale = int(re.findall(r"density:\s*(\d+)", density)[-1]) / 160

    year = int(adb("shell", "date", "+%Y").strip())
    month = int(adb("shell", "date", "+%m").strip())
    new_year = year if month == 1 else year + 1
    settings = f"""<?xml version="1.0" encoding="utf-8"?>
<map>
    <boolean name="notificationsAsked" value="true" />
    <boolean name="holidayPreview" value="true" />
    <string name="holidayIntro">intro-{new_year}</string>
    <set name="holidayDays">
        <string>{new_year}:{new_year - 1}-12-15</string>
        <string>{new_year}:{new_year - 1}-12-16</string>
        <string>{new_year}:{new_year - 1}-12-17</string>
    </set>
</map>""".encode()
    adb("shell", "am", "force-stop", APP)
    adb("shell", "run-as", APP, "mkdir", "-p", "shared_prefs")
    adb(
        "shell", "-T", "run-as", APP, "sh", "-c",
        "'cat > shared_prefs/settings.xml'", data=settings,
    )
    print("Opening the app with holiday preview enabled")
    adb("shell", "am", "start", "-W", "-n", f"{APP}/.MainActivity")
    marker_text = "С Новым годом!" if month == 1 else "До Нового года"
    marker = find_node("text", marker_text)
    print("Holiday countdown is visible")
    holiday_card(frame(), marker, scale).save(OUT / "today.png")
    print("Captured holiday card on the Today screen")

    left, top, right, bottom = find_node("content-desc", "Переписка")
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    back = find_node("content-desc", "Назад")
    note = find_node("text", "Телефоны не связаны")
    image = frame()
    # The crop begins after the avatar and ends well before the connection message.
    start = back[3] + round(7 * scale)
    end = min(start + round(80 * scale), note[1] - round(10 * scale))
    if end - start < 36 * scale:
        raise RuntimeError("Could not isolate the chat garland safely")
    image.crop((0, start, image.width, end)).save(OUT / "chat.png")
    print("Captured holiday garland on the Chat screen")


if __name__ == "__main__":
    main()
