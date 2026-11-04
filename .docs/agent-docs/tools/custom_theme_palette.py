#!/usr/bin/env python3
"""genera la paleta del tema personalizado de polar.

uso: custom_theme_palette.py <dir_res> [<dir_res> ...]
ejemplo: custom_theme_palette.py polar-offline/app/src/main/res polar-online/app/src/main/res

escribe en cada `<dir_res>/values/`:
  - colors_custom_theme.xml: colores de fondos, acentos y prioridades.
  - themes_custom.xml: un tema base por fondo y un overlay por acento y por color de
    prioridad, en version clara y oscura.

el tema personalizado no crea colores en tiempo de ejecucion: combina estos estilos con
`Resources.Theme.applyStyle()` (ver `CustomThemePalette.kt`), asi que todos los `?attr/`
de layouts, drawables y selectores funcionan igual que en los temas predefinidos.

cada acento y cada color de prioridad tiene un tono para fondos claros y otro para fondos
oscuros; el script comprueba el contraste (wcag) de todas las combinaciones y falla si
alguna no llega al minimo. si anades una opcion, registrala tambien en
`CustomThemePalette.kt` y anade su texto en los cinco `values-*/strings.xml`.
"""
import sys
from pathlib import Path

# --- fondos -----------------------------------------------------------------------------
# clave -> (fondo, texto, es_oscuro). el resto de superficies, contornos y textos
# secundarios se derivan mezclando el fondo con el color del texto, con los mismos pasos
# que siguen los temas polar claro y polar oscuro.
BACKGROUNDS = {
    "snow": ("#FAFAFA", "#18181B", False),
    "ivory": ("#FBF8F1", "#1F1B16", False),
    "mist": ("#F4F6F9", "#141A24", False),
    "mint": ("#F2F8F4", "#13201A", False),
    "petal": ("#FBF4F5", "#241618", False),
    "lavender": ("#F6F4FB", "#1B1726", False),
    "graphite": ("#1C1C1F", "#FAFAFA", True),
    "black": ("#000000", "#F2F2F2", True),
    "midnight": ("#0F1520", "#E9EEF6", True),
    "pine": ("#0F1A15", "#E6F0EA", True),
    "plum": ("#1A1220", "#F1E9F4", True),
    "cocoa": ("#1C1714", "#F3ECE6", True),
}

# --- acentos ----------------------------------------------------------------------------
# clave -> (tono para fondos claros, tono para fondos oscuros)
ACCENTS = {
    "ink": ("#18181B", "#FAFAFA"),
    "blue": ("#2563EB", "#60A5FA"),
    "indigo": ("#4F46E5", "#818CF8"),
    "violet": ("#7C3AED", "#A78BFA"),
    "pink": ("#DB2777", "#F472B6"),
    "red": ("#DC2626", "#F87171"),
    "orange": ("#C2410C", "#FB923C"),
    "amber": ("#B45309", "#FBBF24"),
    "green": ("#15803D", "#4ADE80"),
    "teal": ("#0F766E", "#2DD4BF"),
    "cyan": ("#0E7490", "#22D3EE"),
    "slate": ("#475569", "#94A3B8"),
}

# --- prioridades ------------------------------------------------------------------------
# clave -> (tono para fondos claros, tono para fondos oscuros)
PRIORITY_COLORS = {
    "red": ("#DC2626", "#F87171"),
    "orange": ("#EA580C", "#FB923C"),
    "amber": ("#D97706", "#FBBF24"),
    "green": ("#16A34A", "#4ADE80"),
    "teal": ("#0D9488", "#2DD4BF"),
    "blue": ("#2563EB", "#60A5FA"),
    "indigo": ("#4F46E5", "#818CF8"),
    "violet": ("#7C3AED", "#A78BFA"),
    "pink": ("#DB2777", "#F472B6"),
    "slate": ("#64748B", "#94A3B8"),
}

PRIORITIES = ("High", "Medium", "Low")

# minimos de contraste (wcag 2.1)
MIN_TEXT = 7.0  # texto principal sobre el fondo
MIN_TEXT_VARIANT = 4.5  # texto secundario sobre el fondo
MIN_ON_ACCENT = 4.5  # texto sobre el acento (botones)
MIN_ACCENT = 3.0  # acento sobre el fondo (componentes)
MIN_PRIORITY = 2.8  # marca de prioridad sobre la tarjeta (los temas polar usan el mismo ambar)

# errores y exitos comunes a todos los fondos, iguales que en polar claro / oscuro
LIGHT_STATUS = {"error": "#DC2626", "error_container": "#FEE2E2", "on_error_container": "#7F1D1D", "success": "#16A34A"}
DARK_STATUS = {"error": "#F87171", "error_container": "#3B1416", "on_error_container": "#FECACA", "success": "#4ADE80"}


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hexc(c):
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(v))) for v in c)


def mix(a, b, t):
    """mezcla a hacia b en proporcion t (0 = a, 1 = b)."""
    ca, cb = rgb(a), rgb(b)
    return hexc(tuple(x + (y - x) * t for x, y in zip(ca, cb)))


def luminance(hex_color):
    def channel(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(v) for v in rgb(hex_color))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def background_palette(bg, fg, dark):
    if dark:
        p = {
            "background": bg,
            "surface": mix(bg, fg, 0.03),
            "surface_container_lowest": mix(bg, "#000000", 0.15),
            "surface_container_low": mix(bg, fg, 0.018),
            "surface_container": mix(bg, fg, 0.05),
            "surface_container_high": mix(bg, fg, 0.085),
            "surface_container_highest": mix(bg, fg, 0.125),
            "foreground": fg,
            "foreground_variant": mix(bg, fg, 0.6),
            "outline": mix(bg, fg, 0.225),
            "outline_variant": mix(bg, fg, 0.10),
            "inverse_surface": mix(fg, bg, 0.04),
            "inverse_on_surface": mix(bg, "#000000", 0.15),
        }
        p["surface_bright"] = p["surface_container_highest"]
        p["surface_dim"] = bg
    else:
        p = {
            "background": bg,
            "surface": mix(bg, "#FFFFFF", 0.75),
            "surface_container_low": mix(bg, fg, 0.015),
            "surface_container": mix(bg, fg, 0.035),
            "surface_container_high": mix(bg, fg, 0.065),
            "surface_container_highest": mix(bg, fg, 0.10),
            "foreground": fg,
            "foreground_variant": mix(bg, fg, 0.64),
            "outline": mix(bg, fg, 0.16),
            "outline_variant": mix(bg, fg, 0.075),
            "inverse_surface": mix(fg, bg, 0.08),
            "inverse_on_surface": bg,
        }
        p["surface_container_lowest"] = p["surface"]
        p["surface_bright"] = p["surface"]
        p["surface_dim"] = p["surface_container_highest"]
    return p


def accent_palette(key, light, dark):
    """roles del acento para fondos claros (light) y oscuros (dark)."""
    if key == "ink":
        # tinta: el acento es el propio color del texto, como en polar claro / oscuro
        return {
            "light": {"primary": light, "on_primary": "#FAFAFA", "container": "#E4E4E7", "on_container": light},
            "dark": {"primary": dark, "on_primary": "#18181B", "container": "#38383E", "on_container": dark},
        }
    on_light = "#FFFFFF" if contrast("#FFFFFF", light) >= MIN_ON_ACCENT else mix(light, "#000000", 0.85)
    return {
        "light": {
            "primary": light,
            "on_primary": on_light,
            "container": mix("#FFFFFF", light, 0.16),
            "on_container": mix(light, "#000000", 0.5),
        },
        "dark": {
            "primary": dark,
            "on_primary": mix(dark, "#000000", 0.82),
            "container": mix("#18181B", dark, 0.3),
            "on_container": mix(dark, "#FFFFFF", 0.7),
        },
    }


def check(errors, label, a, b, minimum):
    value = contrast(a, b)
    if value < minimum:
        errors.append(f"{label}: {a} sobre {b} = {value:.2f} (< {minimum})")


def validate(backgrounds, accents):
    errors = []
    for key, (bg, fg, dark) in BACKGROUNDS.items():
        p = backgrounds[key]
        check(errors, f"fondo {key} texto", fg, bg, MIN_TEXT)
        check(errors, f"fondo {key} texto secundario", p["foreground_variant"], bg, MIN_TEXT_VARIANT)
        check(errors, f"fondo {key} texto secundario en tarjeta", p["foreground_variant"], p["surface"], MIN_TEXT_VARIANT)
        mode = "dark" if dark else "light"
        for akey, a in accents.items():
            roles = a[mode]
            check(errors, f"acento {akey} sobre {key}", roles["primary"], bg, MIN_ACCENT)
            check(errors, f"texto sobre acento {akey} en {key}", roles["on_primary"], roles["primary"], MIN_ON_ACCENT)
            check(errors, f"contenedor {akey} en {key}", roles["on_container"], roles["container"], MIN_ON_ACCENT)
        for pkey, (light, darkc) in PRIORITY_COLORS.items():
            check(errors, f"prioridad {pkey} en {key}", darkc if dark else light, p["surface"], MIN_PRIORITY)
    return errors


def style_name(*parts):
    return ".".join(parts)


def pascal(key):
    return "".join(part.capitalize() for part in key.split("_"))


def build():
    backgrounds = {k: background_palette(bg, fg, dark) for k, (bg, fg, dark) in BACKGROUNDS.items()}
    accents = {k: accent_palette(k, l, d) for k, (l, d) in ACCENTS.items()}
    errors = validate(backgrounds, accents)
    if errors:
        sys.exit("contraste insuficiente:\n  " + "\n  ".join(errors))

    header = "  <!-- generado por .docs/agent-docs/tools/custom_theme_palette.py: no editar a mano -->\n"

    colors = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", header.rstrip("\n")]
    colors.append("\n  <!-- fondos -->")
    for key, p in backgrounds.items():
        for role, value in p.items():
            colors.append(f'  <color name="custom_bg_{key}_{role}">{value}</color>')
        colors.append("")
    colors.append("  <!-- acentos (tono para fondo claro / oscuro) -->")
    for key, modes in accents.items():
        for mode, roles in modes.items():
            for role, value in roles.items():
                colors.append(f'  <color name="custom_accent_{key}_{mode}_{role}">{value}</color>')
        colors.append("")
    colors.append("  <!-- prioridades (tono para fondo claro / oscuro) -->")
    for key, (light, dark) in PRIORITY_COLORS.items():
        colors.append(f'  <color name="custom_priority_{key}_light">{light}</color>')
        colors.append(f'  <color name="custom_priority_{key}_dark">{dark}</color>')
    colors.append("")
    colors.append("  <!-- estados comunes -->")
    for mode, status in (("light", LIGHT_STATUS), ("dark", DARK_STATUS)):
        for role, value in status.items():
            colors.append(f'  <color name="custom_status_{mode}_{role}">{value}</color>')
    colors.append("</resources>\n")

    themes = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", header.rstrip("\n")]
    themes.append(
        "  <!--\n"
        "    tema personalizado: el fondo es un tema completo (superficies, textos, contornos y\n"
        "    estados) y el acento y cada prioridad son overlays que `ThemeManager` aplica encima con\n"
        "    `Resources.Theme.applyStyle()`. cada overlay existe en tono claro y oscuro para mantener\n"
        "    el contraste con cualquier fondo.\n"
        "  -->"
    )
    for key, p in backgrounds.items():
        dark = BACKGROUNDS[key][2]
        mode = "dark" if dark else "light"
        parent = "Platform.Theme.Polar.Dark" if dark else "Platform.Theme.Polar.Light"
        c = lambda role: f"@color/custom_bg_{key}_{role}"
        s = lambda role: f"@color/custom_status_{mode}_{role}"
        on_status = "@color/black" if dark else "@color/white"
        # acento por defecto (tinta) para que el tema base este completo aunque falte el overlay
        a = lambda role: f"@color/custom_accent_ink_{mode}_{role}"
        items = [
            ("colorPrimary", a("primary")),
            ("colorOnPrimary", a("on_primary")),
            ("colorPrimaryContainer", a("container")),
            ("colorOnPrimaryContainer", a("on_container")),
            ("colorPrimaryInverse", f"@color/custom_accent_ink_{'light' if dark else 'dark'}_primary"),
            ("colorSecondary", a("primary")),
            ("colorOnSecondary", a("on_primary")),
            ("colorSecondaryContainer", a("container")),
            ("colorOnSecondaryContainer", a("on_container")),
            ("colorTertiary", a("primary")),
            ("colorOnTertiary", a("on_primary")),
            ("colorTertiaryContainer", a("container")),
            ("colorOnTertiaryContainer", a("on_container")),
            ("buttonBackground", a("primary")),
            ("colorError", s("error")),
            ("colorOnError", on_status),
            ("colorErrorContainer", s("error_container")),
            ("colorOnErrorContainer", s("on_error_container")),
            ("colorSuccess", s("success")),
            ("colorOnSuccess", on_status),
            ("colorDateOverdue", s("error")),
            ("colorPriorityHigh", f"@color/custom_priority_red_{mode}"),
            ("colorPriorityMedium", f"@color/custom_priority_amber_{mode}"),
            ("colorPriorityLow", f"@color/custom_priority_blue_{mode}"),
            ("android:colorBackground", c("background")),
            ("colorOnBackground", c("foreground")),
            ("colorSurface", c("surface")),
            ("colorOnSurface", c("foreground")),
            ("colorSurfaceVariant", c("surface_container_high")),
            ("colorOnSurfaceVariant", c("foreground_variant")),
            ("colorSurfaceContainerLowest", c("surface_container_lowest")),
            ("colorSurfaceContainerLow", c("surface_container_low")),
            ("colorSurfaceContainer", c("surface_container")),
            ("colorSurfaceContainerHigh", c("surface_container_high")),
            ("colorSurfaceContainerHighest", c("surface_container_highest")),
            ("colorSurfaceBright", c("surface_bright")),
            ("colorSurfaceDim", c("surface_dim")),
            ("colorSurfaceInverse", c("inverse_surface")),
            ("colorOnSurfaceInverse", c("inverse_on_surface")),
            ("colorOutline", c("outline")),
            ("colorOutlineVariant", c("outline_variant")),
            ("android:statusBarColor", "@android:color/transparent"),
            ("android:navigationBarColor", "@android:color/transparent"),
            ("android:windowLightStatusBar", "false" if dark else "true"),
            ("taskBackground", c("surface")),
            ("taskBorder", c("outline_variant")),
            ("mainBorder", c("outline_variant")),
        ]
        themes.append(f'\n  <style name="{style_name("Theme.Polar.Custom", pascal(key))}" parent="{parent}">')
        themes += [f'    <item name="{name}">{value}</item>' for name, value in items]
        themes.append("  </style>")

    themes.append("\n  <!-- acentos -->")
    for key in accents:
        for mode in ("light", "dark"):
            other = "dark" if mode == "light" else "light"
            a = lambda role: f"@color/custom_accent_{key}_{mode}_{role}"
            items = [
                ("colorPrimary", a("primary")),
                ("colorOnPrimary", a("on_primary")),
                ("colorPrimaryContainer", a("container")),
                ("colorOnPrimaryContainer", a("on_container")),
                # la superficie inversa (snackbars) es del modo contrario
                ("colorPrimaryInverse", f"@color/custom_accent_{key}_{other}_primary"),
                ("colorSecondary", a("primary")),
                ("colorOnSecondary", a("on_primary")),
                ("colorSecondaryContainer", a("container")),
                ("colorOnSecondaryContainer", a("on_container")),
                ("colorTertiary", a("primary")),
                ("colorOnTertiary", a("on_primary")),
                ("colorTertiaryContainer", a("container")),
                ("colorOnTertiaryContainer", a("on_container")),
                ("buttonBackground", a("primary")),
            ]
            themes.append(f'  <style name="ThemeOverlay.Polar.Custom.Accent.{pascal(key)}.{pascal(mode)}" parent="">')
            themes += [f'    <item name="{name}">{value}</item>' for name, value in items]
            themes.append("  </style>")

    themes.append("\n  <!-- prioridades -->")
    for priority in PRIORITIES:
        for key in PRIORITY_COLORS:
            for mode in ("light", "dark"):
                themes.append(
                    f'  <style name="ThemeOverlay.Polar.Custom.Priority{priority}.{pascal(key)}.{pascal(mode)}" parent="">'
                    f'<item name="colorPriority{priority}">@color/custom_priority_{key}_{mode}</item></style>'
                )
    themes.append("</resources>\n")
    return "\n".join(colors), "\n".join(themes)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    colors, themes = build()
    for res in sys.argv[1:]:
        values = Path(res) / "values"
        if not values.is_dir():
            sys.exit(f"no existe {values}")
        (values / "colors_custom_theme.xml").write_text(colors, encoding="utf-8")
        (values / "themes_custom.xml").write_text(themes, encoding="utf-8")
        print(f"escrito {values}/colors_custom_theme.xml y themes_custom.xml")


if __name__ == "__main__":
    main()
