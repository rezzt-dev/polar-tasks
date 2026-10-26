#!/usr/bin/env python3
"""convierte iconos svg de lucide en vector drawables de android.

uso: lucide2vd.py <dir_iconos_lucide> <dir_drawable_salida>
el mapeo nombre_android -> icono_lucide esta en MAPPING.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

STROKE = "1.75"

MAPPING = {
    # navegacion y acciones generales
    "ic_add": "plus",
    "ic_arrow_back": "arrow-left",
    "ic_arrow_forward": "arrow-right",
    "ic_arrow_down": "chevron-down",
    "ic_arrow_up": "chevron-up",
    "ic_chevron_right": "chevron-right",
    "ic_chevron_left": "chevron-left",
    "ic_menu": "menu",
    "ic_more_vertical": "ellipsis-vertical",
    "ic_search": "search",
    "ic_settings": "settings",
    "ic_sort": "arrow-up-down",
    "ic_edit": "pencil",
    "ic_delete": "trash-2",
    "ic_trash": "trash-2",
    "ic_undo": "undo-2",
    "ic_share": "share-2",
    "ic_attach_file": "paperclip",
    "ic_check": "check",
    "ic_check_box": "square-check",
    "ic_check_circle": "circle-check",
    "ic_circle_outline": "circle",
    "ic_lock": "lock",
    "ic_sync": "repeat",
    "ic_cloud_download": "cloud-download",
    "ic_cloud_upload": "cloud-upload",
    # secciones
    "ic_home": "house",
    "ic_tasks": "bell",
    "ic_calendar": "calendar-days",
    "ic_calendar_mini": "calendar",
    "ic_label": "tag",
    "ic_folder": "folder",
    "ic_stats": "chart-no-axes-column",
    "ic_notifications": "bell",
    "ic_schedule": "clock",
    "ic_location": "map-pin",
    "ic_location_notification": "map-pin",
    "ic_theme": "palette",
    "ic_text_format": "type",
    "ic_text_size": "a-large-small",
    "ic_colorize": "pipette",
    # ajustes
    "ic_settings_backup_export": "file-output",
    "ic_settings_backup_import": "file-input",
    "ic_settings_checkbox": "square-check",
    "ic_settings_checkbox_circular": "circle-check",
    "ic_settings_csv": "file-spreadsheet",
    "ic_settings_font": "type",
    "ic_settings_language": "languages",
    "ic_settings_notifications": "bell-ring",
    "ic_settings_schedule": "alarm-clock",
    "ic_settings_text_size": "a-large-small",
    "ic_settings_theme": "palette",
    "ic_settings_tutorial": "circle-help",
    # estadisticas
    "ic_stat_check": "circle-check",
    "ic_stat_error": "circle-alert",
    "ic_stat_fire": "flame",
    "ic_stat_insight": "sparkles",
    "ic_stat_list_chart": "chart-bar",
    "ic_stat_list": "list-checks",
    "ic_stat_priority": "flag",
    "ic_stat_schedule": "clock",
    "ic_stat_star": "star",
    "ic_stat_trend": "trending-up",
    # iconos de lista
    "ic_list": "list",
    "ic_account_balance_wallet": "wallet",
    "ic_beach_access": "umbrella",
    "ic_build": "wrench",
    "ic_card_giftcard": "gift",
    "ic_chat": "message-circle",
    "ic_child_care": "baby",
    "ic_cleaning_services": "spray-can",
    "ic_code": "code",
    "ic_directions_car": "car",
    "ic_family_restroom": "users",
    "ic_favorite": "bookmark",
    "ic_fitness_center": "dumbbell",
    "ic_flag": "flag",
    "ic_flight": "plane",
    "ic_heart": "heart",
    "ic_image": "image",
    "ic_lightbulb": "lightbulb",
    "ic_medical_services": "briefcase-medical",
    "ic_menu_book": "book-open",
    "ic_movie": "clapperboard",
    "ic_music_note": "music",
    "ic_pets": "paw-print",
    "ic_restaurant": "utensils",
    "ic_savings": "piggy-bank",
    "ic_school": "graduation-cap",
    "ic_shopping_cart": "shopping-cart",
    "ic_sports_esports": "gamepad-2",
    "ic_star": "star",
    "ic_work": "briefcase",
    "ic_yard": "sprout",
    # nuevos
    "ic_grid": "layout-grid",
    "ic_inbox": "inbox",
    "ic_x": "x",
    "ic_list_todo": "list-todo",
    "ic_hourglass": "hourglass",
    "ic_copy": "copy",
    "ic_rotate_ccw": "rotate-ccw",
    "ic_moon": "moon",
    "ic_sun": "sun",
    "ic_info": "info",
    "ic_external_link": "external-link",
    "ic_navigation": "navigation",
    "ic_user": "user",
    "ic_log_out": "log-out",
    "ic_cloud": "cloud",
    "ic_refresh": "refresh-cw",
    "ic_swipe": "move-horizontal",
    "ic_shield": "shield-check",
    "ic_filter": "list-filter",
    "ic_plus_circle": "circle-plus",
    "ic_minus": "minus",
    "ic_zap": "zap",
    "ic_users": "users",
    "ic_ban": "ban",
    "ic_arrow_right_left": "arrow-right-left",
    "ic_git_branch": "git-branch",
    "ic_eye": "eye",
    "ic_eye_off": "eye-off",
    "ic_mail": "mail",
    "ic_key": "key-round",
    "ic_text": "text",
    "ic_calendar_check": "calendar-check",
    "ic_calendar_clock": "calendar-clock",
}

NUM = r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"


def fmt(v: float) -> str:
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def normalize_path(d: str) -> str:
    """reescribe el path con separadores explicitos, sobre todo en los flags de los arcos,
    porque el parser de android no acepta flags compactos como `a1 1 0 011 1`."""
    tokens = re.findall(r"[MmZzLlHhVvCcSsQqTtAa]|" + NUM, d)
    out = []
    i = 0
    cmd = None
    counts = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
    while i < len(tokens):
        t = tokens[i]
        if re.fullmatch(r"[MmZzLlHhVvCcSsQqTtAa]", t):
            cmd = t
            out.append(t)
            i += 1
            if cmd in "Zz":
                continue
            continue
        n = counts[cmd.lower()]
        args = []
        if cmd in "Aa":
            # rx ry rot flag flag x y; los flags pueden venir pegados ("011")
            j = i
            raw = []
            while len(raw) < 7 and j < len(tokens):
                tok = tokens[j]
                if len(raw) in (3, 4) and re.fullmatch(r"[01]{2,}.*", tok.lstrip("+")) and "." not in tok[:2]:
                    # separar flags pegados
                    raw.append(tok[0])
                    rest = tok[1:]
                    if len(raw) == 4 and rest:
                        raw.append(rest[0])
                        rest = rest[1:]
                    if rest:
                        tokens[j] = rest
                        continue
                    j += 1
                    continue
                raw.append(tok)
                j += 1
            args = raw
            i = j
        else:
            args = tokens[i:i + n]
            i += n
        out.append(" ".join(fmt(float(a)) for a in args))
    return " ".join(out)


def circle(cx, cy, r):
    return (f"M{fmt(cx - r)},{fmt(cy)} a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0 "
            f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0 Z")


def ellipse(cx, cy, rx, ry):
    return (f"M{fmt(cx - rx)},{fmt(cy)} a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 "
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0 Z")


def rect(x, y, w, h, rx, ry):
    if rx == 0 and ry == 0:
        return f"M{fmt(x)},{fmt(y)} h{fmt(w)} v{fmt(h)} h{fmt(-w)} Z"
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    return (f"M{fmt(x + rx)},{fmt(y)} h{fmt(w - 2 * rx)} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(ry)} "
            f"v{fmt(h - 2 * ry)} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(ry)} h{fmt(-(w - 2 * rx))} "
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(-ry)} v{fmt(-(h - 2 * ry))} "
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(-ry)} Z")


def points(p, close):
    nums = [float(n) for n in re.findall(NUM, p)]
    pts = list(zip(nums[0::2], nums[1::2]))
    s = f"M{fmt(pts[0][0])},{fmt(pts[0][1])} " + " ".join(f"L{fmt(a)},{fmt(b)}" for a, b in pts[1:])
    return s + (" Z" if close else "")


def element_to_path(el) -> str:
    tag = el.tag.split("}")[-1]
    a = el.attrib
    g = lambda k, d=0.0: float(a.get(k, d))
    if tag == "path":
        return normalize_path(a["d"])
    if tag == "circle":
        return circle(g("cx"), g("cy"), g("r"))
    if tag == "ellipse":
        return ellipse(g("cx"), g("cy"), g("rx"), g("ry"))
    if tag == "rect":
        rx = a.get("rx")
        ry = a.get("ry")
        rxv = float(rx) if rx is not None else (float(ry) if ry is not None else 0.0)
        ryv = float(ry) if ry is not None else rxv
        return rect(g("x"), g("y"), g("width"), g("height"), rxv, ryv)
    if tag == "line":
        return f"M{fmt(g('x1'))},{fmt(g('y1'))} L{fmt(g('x2'))},{fmt(g('y2'))}"
    if tag == "polyline":
        return points(a["points"], False)
    if tag == "polygon":
        return points(a["points"], True)
    raise ValueError(f"elemento no soportado: {tag}")


def convert(svg_path: Path, lucide_name: str) -> str:
    root = ET.parse(svg_path).getroot()
    paths = [element_to_path(el) for el in root.iter() if el is not root]
    body = "\n".join(
        f'    <path\n'
        f'        android:pathData="{p}"\n'
        f'        android:strokeColor="@android:color/white"\n'
        f'        android:strokeWidth="{STROKE}"\n'
        f'        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />'
        for p in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<!-- lucide: {lucide_name} (isc license, https://lucide.dev) -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24"\n'
        '    android:tint="?attr/colorControlNormal">\n'
        f'{body}\n'
        '</vector>\n'
    )


def main():
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])
    only = set(sys.argv[3:])
    for name, lucide in MAPPING.items():
        if only and name not in only:
            continue
        svg = src / f"{lucide}.svg"
        if not svg.exists():
            print(f"falta {lucide} para {name}", file=sys.stderr)
            continue
        (dst / f"{name}.xml").write_text(convert(svg, lucide))
        print(f"{name} <- {lucide}")


if __name__ == "__main__":
    main()
