package app.polar.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
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
        TutorialPage(R.string.tutorial_title_2, R.string.tutorial_desc_2, R.drawable.ic_tasks),
        TutorialPage(R.string.tutorial_title_3, R.string.tutorial_desc_3, R.drawable.ic_chat),
        TutorialPage(R.string.tutorial_title_4, R.string.tutorial_desc_4, R.drawable.ic_check_box),
        TutorialPage(R.string.tutorial_title_5, R.string.tutorial_desc_5, R.drawable.ic_calendar),
        TutorialPage(R.string.tutorial_title_6, R.string.tutorial_desc_6, R.drawable.ic_theme),
        TutorialPage(R.string.tutorial_title_7, R.string.tutorial_desc_7, R.drawable.ic_stats)
    )

    private lateinit var indicators: Array<ImageView>

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

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    private fun setupIndicators() {
        val count = tutorialPages.size
        indicators = Array(count) { ImageView(this) }
        
        // Define exact small size for dots (e.g., 10dp)
        val density = resources.displayMetrics.density
        val sizePx = (10 * density).toInt()
        
        val layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            setMargins(8, 0, 8, 0)
        }

        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        for (i in 0 until count) {
            indicators[i].apply {
                setImageDrawable(ContextCompat.getDrawable(this@TutorialActivity, R.drawable.ic_circle_outline))
                this.layoutParams = layoutParams
                setColorFilter(primaryColor)
            }
            binding.dotsLayout.addView(indicators[i])
        }
        if (count > 0) {
            updateIndicators(0)
        }
    }

    private fun updateIndicators(position: Int) {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data
        
        for (i in indicators.indices) {
            indicators[i].setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (i == position) R.drawable.ic_circle else R.drawable.ic_circle_outline
                )
            )
            indicators[i].setColorFilter(primaryColor)
        }
    }

    private fun updateButtons(position: Int) {
        binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        
        if (position == tutorialPages.size - 1) {
            binding.btnSkip.visibility = View.INVISIBLE
            binding.btnNext.icon = null
            binding.btnNext.setText(R.string.action_finish)
            binding.btnNext.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
        } else {
            binding.btnSkip.visibility = View.VISIBLE
            binding.btnNext.icon = ContextCompat.getDrawable(this, R.drawable.ic_arrow_forward)
            binding.btnNext.text = ""
            val scale = resources.displayMetrics.density
            binding.btnNext.layoutParams.width = (56 * scale + 0.5f).toInt()
        }
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
