package app.polar.util

import android.content.res.ColorStateList
import android.content.res.Resources
import android.util.DisplayMetrics
import app.polar.R

/**
 * Envoltorio de [Resources] que devuelve los colores del tema personalizado
 * cuando se solicitan los recursos "stub" `@color/custom_*`.
 *
 * El resto de llamadas se delegan al [Resources] original.
 */
@Suppress("DEPRECATION")
class PolarResources(
  assets: android.content.res.AssetManager,
  metrics: DisplayMetrics,
  config: android.content.res.Configuration,
  private val base: Resources,
  private val colors: CustomThemeColors
) : Resources(assets, metrics, config) {

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getColor(id: Int): Int {
    return resolveCustomColor(id) ?: base.getColor(id)
  }

  override fun getColor(id: Int, theme: Theme?): Int {
    return resolveCustomColor(id) ?: base.getColor(id, theme)
  }

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getColorStateList(id: Int): ColorStateList {
    val color = resolveCustomColor(id)
    return if (color != null) ColorStateList.valueOf(color) else base.getColorStateList(id)
  }

  override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
    val color = resolveCustomColor(id)
    return if (color != null) ColorStateList.valueOf(color) else base.getColorStateList(id, theme)
  }

  private fun resolveCustomColor(id: Int): Int? {
    return when (id) {
      R.color.custom_background -> colors.background
      R.color.custom_surface -> colors.surface
      R.color.custom_surface_variant -> colors.surfaceVariant
      R.color.custom_surface_container -> colors.surfaceContainer
      R.color.custom_surface_container_high -> colors.surfaceContainerHigh
      R.color.custom_foreground -> colors.foreground
      R.color.custom_primary -> colors.primary
      R.color.custom_on_primary -> colors.onPrimary
      R.color.custom_primary_container -> colors.primaryContainer
      R.color.custom_on_primary_container -> colors.onPrimaryContainer
      R.color.custom_secondary -> colors.secondary
      R.color.custom_on_secondary -> colors.onSecondary
      R.color.custom_secondary_container -> colors.secondaryContainer
      R.color.custom_on_secondary_container -> colors.onSecondaryContainer
      R.color.custom_outline -> colors.outline
      R.color.custom_outline_variant -> colors.outlineVariant
      R.color.custom_error -> colors.error
      R.color.custom_success -> colors.success
      R.color.custom_task_background -> colors.taskBackground
      R.color.custom_task_border -> colors.taskBorder
      R.color.custom_main_border -> colors.mainBorder
      R.color.custom_button_background -> colors.buttonBackground
      else -> null
    }
  }
}
