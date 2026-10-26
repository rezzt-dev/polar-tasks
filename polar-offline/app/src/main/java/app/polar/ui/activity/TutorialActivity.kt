package app.polar.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.polar.R
import app.polar.databinding.ActivityTutorialBinding
import app.polar.ui.fragment.TutorialStepFragment

class TutorialActivity : BaseActivity() {

    private lateinit var binding: ActivityTutorialBinding
    private val tutorialPages = listOf(
        TutorialPage(R.string.tutorial_title_1, R.string.tutorial_desc_1, R.drawable.ic_home),
        TutorialPage(R.string.tutorial_title_2, R.string.tutorial_desc_2, R.drawable.ic_stat_list),
        TutorialPage(R.string.tutorial_title_3, R.string.tutorial_desc_3, R.drawable.ic_swipe),
        TutorialPage(R.string.tutorial_title_4, R.string.tutorial_desc_4, R.drawable.ic_image),
        TutorialPage(R.string.tutorial_title_5, R.string.tutorial_desc_5, R.drawable.ic_calendar),
        TutorialPage(R.string.tutorial_title_6, R.string.tutorial_desc_6, R.drawable.ic_settings_theme),
        TutorialPage(R.string.tutorial_title_7, R.string.tutorial_desc_7, R.drawable.ic_stats)
    )

    private lateinit var indicators: Array<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupIndicators()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = TutorialPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Transición entre páginas: fundido con un ligero desplazamiento del contenido.
        binding.viewPager.setPageTransformer { page, position ->
            val offset = kotlin.math.abs(position).coerceAtMost(1f)
            page.alpha = 1f - offset
            page.translationX = -position * page.width * 0.75f
            page.scaleX = 1f - offset * 0.04f
            page.scaleY = 1f - offset * 0.04f
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    // Indicadores tipo píldora: el activo se alarga y se rellena con el color de acento.
    private fun setupIndicators() {
        val count = tutorialPages.size
        val density = resources.displayMetrics.density
        val dot = (6 * density).toInt()
        indicators = Array(count) {
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dot, dot).apply {
                    marginStart = (3 * density).toInt()
                    marginEnd = (3 * density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dot / 2f
                }
            }
        }
        indicators.forEach { binding.dotsLayout.addView(it) }
        if (count > 0) updateIndicators(0)
    }

    private fun updateIndicators(position: Int) {
        val density = resources.displayMetrics.density
        val dot = (6 * density).toInt()
        val active = (20 * density).toInt()
        val activeColor = resolveColor(androidx.appcompat.R.attr.colorPrimary)
        val inactiveColor = resolveColor(com.google.android.material.R.attr.colorOutline)

        indicators.forEachIndexed { i, view ->
            val target = if (i == position) active else dot
            (view.background as android.graphics.drawable.GradientDrawable)
                .setColor(if (i == position) activeColor else inactiveColor)
            val start = view.layoutParams.width
            if (start == target) return@forEachIndexed
            android.animation.ValueAnimator.ofInt(start, target).apply {
                duration = 220
                interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener {
                    view.layoutParams = view.layoutParams.apply { width = it.animatedValue as Int }
                }
                start()
            }
        }
    }

    private fun resolveColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updateButtons(position: Int) {
        binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        val isLast = position == tutorialPages.size - 1
        binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        binding.btnNext.setText(if (isLast) R.string.action_finish else R.string.action_next)
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < tutorialPages.size - 1) {
                binding.viewPager.currentItem += 1
            } else {
                finishTutorial()
            }
        }

        binding.btnBack.setOnClickListener {
            if (binding.viewPager.currentItem > 0) {
                binding.viewPager.currentItem -= 1
            }
        }
        
        binding.btnSkip.setOnClickListener {
            finishTutorial()
        }
    }

    private fun finishTutorial() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("is_first_run", false).apply()
        
        val isFromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        if (!isFromSettings) {
            startActivity(Intent(this, app.polar.MainActivity::class.java))
        }
        finish()
    }

    private inner class TutorialPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = tutorialPages.size
        override fun createFragment(position: Int): Fragment {
            val page = tutorialPages[position]
            return TutorialStepFragment.newInstance(page.titleRes, page.descRes, page.iconRes)
        }
    }

    private data class TutorialPage(val titleRes: Int, val descRes: Int, val iconRes: Int)

    companion object {
        const val EXTRA_FROM_SETTINGS = "extra_from_settings"
    }
}
