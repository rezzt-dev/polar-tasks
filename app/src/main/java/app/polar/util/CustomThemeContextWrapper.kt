package app.polar.util

import android.content.Context
import android.content.res.Resources
import android.view.ContextThemeWrapper
import app.polar.R

/**
 * Contexto temático para el tema personalizado.
 *
 * Extiende [ContextThemeWrapper] para aplicar el estilo base [R.style.Theme_Polar_Custom]
 * y sobrescribe [getResources] para devolver [PolarResources], que intercepta la
 * resolución de los colores "stub" (`@color/custom_*`) y devuelve los valores
 * generados dinámicamente.
 */
class CustomThemeContextWrapper(
  base: Context,
  private val colors: CustomThemeColors
) : ContextThemeWrapper(base, R.style.Theme_Polar_Custom) {

  private val polarResources: PolarResources by lazy {
    val baseRes = base.resources
    PolarResources(
      baseRes.assets,
      baseRes.displayMetrics,
      baseRes.configuration,
      baseRes,
      colors
    )
  }

  override fun getResources(): Resources {
    return polarResources
  }
}
