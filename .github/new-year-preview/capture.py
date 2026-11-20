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
    return subprocess.run(
        ["adb", *args], input=data, capture_output=True, check=True, timeout=45
    ).stdout


def find_node(attribute, text):
    for _ in range(6):
        try:
            adb("shell", "uiautomator", "dump", UI_DUMP)
            layout = ElementTree.fromstring(adb("exec-out", "cat", UI_DUMP))
            for node in layout.iter("node"):
                if node.get(attribute) == text and node.get("package") == APP:
                    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        except (subprocess.CalledProcessError, ElementTree.ParseError):
            pass
        time.sleep(1)
    raise RuntimeError(f"Screen marker did not appear: {text}")


def frame():
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")


def holiday_card(image, marker, scale):
    # Match the actual card's green surface around the countdown, on two columns.
    # Saving only this crop keeps the account header and planner data out of the result.
    left = round(20 * scale)
    probes = (left + round(9 * scale), image.width - left - round(11 * scale))

    def in_card(y):
        for x in probes:
            red, green, blue = image.getpixel((x, y))
            if not (10 <= red <= 43 and 31 <= green <= 73 and 21 <= blue <= 62):
                return False
            if green - red < 13 or green - blue < 5:
                return False
        return True

    top = marker[1]
    bottom = top
    while top > 0 and in_card(top - 1):
        top -= 1
    while bottom < image.height and in_card(bottom):
        bottom += 1
    if not (105 * scale < bottom - top < 200 * scale):
        raise RuntimeError("Could not isolate the holiday card safely")
    inset = round(6 * scale)
    return image.crop((left + inset, top, image.width - left - inset, bottom))


def main():
    OUT.mkdir(exist_ok=True)
    adb("install", "-g", "app/build/outputs/apk/debug/app-debug.apk")
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
        "exec-in", "run-as", APP, "sh", "-c",
        "'cat > shared_prefs/settings.xml'", data=settings,
    )
    adb("shell", "am", "start", "-W", "-n", f"{APP}/.MainActivity")
    marker_text = "С Новым годом!" if month == 1 else "До Нового года"
    marker = find_node("text", marker_text)
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
