package im.angry.openeuicc.ui.wizard

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.ui.BaseEuiccAccessActivity
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.LocalProfileAssistant

class DownloadWizardActivity: BaseEuiccAccessActivity() {
    data class DownloadWizardState(
        var currentStepFragmentClassName: String? = null,
        var selectedLogicalSlot: Int,
        var activationCode: ActivationCode = ActivationCode(),
        var downloadStarted: Boolean = false,
        var downloadTaskID: Long = -1,
        var downloadError: LocalProfileAssistant.ProfileDownloadException? = null,
    ) {
        companion object {
            private const val FRAGMENT = "currentStepFragmentClassName"
            private const val SLOT = "selectedLogicalSlot"
            private const val ACT_CODE = "activationCode"
            private const val DL_STARTED = "downloadStarted"
            private const val DL_ID = "downloadTaskID"
        }

        fun onSaveInstanceState(outState: Bundle) {
            outState.putString(FRAGMENT, currentStepFragmentClassName)
            outState.putInt(SLOT, selectedLogicalSlot)
            outState.putParcelable(ACT_CODE, activationCode)
            outState.putBoolean(DL_STARTED, downloadStarted)
            outState.putLong(DL_ID, downloadTaskID)
        }

        fun onRestoreInstanceState(savedInstanceState: Bundle) {
            currentStepFragmentClassName = savedInstanceState
                .getString(FRAGMENT, currentStepFragmentClassName)
            selectedLogicalSlot = savedInstanceState
                .getInt(SLOT, selectedLogicalSlot)
            activationCode = savedInstanceState
                .getCompatParcelable(ACT_CODE, ActivationCode::class.java) ?: ActivationCode()
            downloadStarted = savedInstanceState
                .getBoolean(DL_STARTED, downloadStarted)
            downloadTaskID = savedInstanceState
                .getLong(DL_ID, downloadTaskID)
        }

        private fun <T> Bundle.getCompatParcelable(key: String, clazz: Class<T>): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                getParcelable(key, clazz) else getParcelable(key)
    }

    private lateinit var state: DownloadWizardState

    private lateinit var progressBar: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var prevButton: Button

    private var currentFragment: DownloadWizardStepFragment? = null
        set(value) {
            if (this::state.isInitialized) {
                state.currentStepFragmentClassName = value?.javaClass?.name
            }
            field = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_wizard)
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Make back == prev
                onPrevPressed()
            }
        })

        state = DownloadWizardState(
            selectedLogicalSlot = intent.getIntExtra("selectedLogicalSlot", 0),
        )

        progressBar = requireViewById(R.id.progress)
        nextButton = requireViewById(R.id.download_wizard_next)
        prevButton = requireViewById(R.id.download_wizard_back)

        nextButton.setOnClickListener {
            onNextPressed()
        }

        prevButton.setOnClickListener {
            onPrevPressed()
        }

        val navigation = requireViewById<View>(R.id.download_wizard_navigation)
        val origHeight = navigation.layoutParams.height

        ViewCompat.setOnApplyWindowInsetsListener(navigation) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
                        or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(bars.left, 0, bars.right, bars.bottom)
            val newParams = navigation.layoutParams
            newParams.height = origHeight + bars.bottom
            navigation.layoutParams = newParams
            WindowInsetsCompat.CONSUMED
        }

        val fragmentRoot = requireViewById<View>(R.id.step_fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(fragmentRoot) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bars.left, bars.top, bars.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        state.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        state.onRestoreInstanceState(savedInstanceState)
    }

    private fun onPrevPressed() {
        hideIme()

        if (currentFragment?.hasPrev == true) {
            val prevFrag = currentFragment?.createPrevFragment()
            if (prevFrag == null) {
                finish()
            } else {
                showFragment(prevFrag, R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    private fun onNextPressed() {
        hideIme()

        nextButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        lifecycleScope.launch(Dispatchers.Main) {
            if (state.selectedLogicalSlot >= 0) {
                try {
                    // This is run on IO by default
                    euiccChannelManager.withEuiccChannel(state.selectedLogicalSlot) { channel ->
                        // Be _very_ sure that the channel we got is valid
                        if (!channel.valid) throw EuiccChannelManager.EuiccChannelNotFoundException()
                    }
                } catch (e: EuiccChannelManager.EuiccChannelNotFoundException) {
                    this@DownloadWizardActivity
                        .makeLongToast(R.string.download_wizard_slot_removed)
                        .show()
                    finish()
                }
            }

            progressBar.visibility = View.GONE
            nextButton.isEnabled = true

            if (currentFragment?.hasNext == true) {
                currentFragment?.beforeNext()
                val nextFrag = currentFragment?.createNextFragment()
                if (nextFrag == null) {
                    finish()
                } else {
                    showFragment(nextFrag, R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
        }
    }

    override fun onInit() {
        progressBar.visibility = View.GONE

        if (state.currentStepFragmentClassName != null) {
            val clazz = Class.forName(state.currentStepFragmentClassName!!)
            showFragment(clazz.getDeclaredConstructor().newInstance() as DownloadWizardStepFragment)
        } else {
            showFragment(DownloadWizardSlotSelectFragment())
        }
    }

    private fun showFragment(
        nextFrag: DownloadWizardStepFragment,
        enterAnim: Int = 0,
        exitAnim: Int = 0
    ) {
        currentFragment = nextFrag
        supportFragmentManager.beginTransaction().setCustomAnimations(enterAnim, exitAnim)
            .replace(R.id.step_fragment_container, nextFrag)
            .commit()
        refreshButtons()
    }

    private fun refreshButtons() {
        currentFragment?.let {
            nextButton.visibility = if (it.hasNext) {
                View.VISIBLE
            } else {
                View.GONE
            }
            prevButton.visibility = if (it.hasPrev) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun hideIme() {
        currentFocus?.let {
            val imm = getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    abstract class DownloadWizardStepFragment : Fragment(), OpenEuiccContextMarker {
        private val activity: DownloadWizardActivity
            get() = requireActivity() as DownloadWizardActivity

        protected val state: DownloadWizardState
            get() = activity.state

        abstract val hasNext: Boolean
        abstract val hasPrev: Boolean
        abstract fun createNextFragment(): DownloadWizardStepFragment?
        abstract fun createPrevFragment(): DownloadWizardStepFragment?

        protected fun gotoNextFragment(next: DownloadWizardStepFragment? = null) {
            activity.showFragment(
                next ?: createNextFragment()!!,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }

        protected fun hideProgressBar() {
            activity.progressBar.visibility = View.GONE
        }

        protected fun showProgressBar(progressValue: Int) {
            activity.progressBar.apply {
                visibility = View.VISIBLE
                if (progressValue >= 0) {
                    isIndeterminate = false
                    progress = progressValue
                } else {
                    isIndeterminate = true
                }
            }
        }

        protected fun refreshButtons() {
            activity.refreshButtons()
        }

        open fun beforeNext() {}
    }
}