package im.angry.openeuicc.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.channel
import im.angry.openeuicc.util.newInstanceEuicc
import im.angry.openeuicc.util.slotId

class UnprivilegedEuiccManagementFragment : EuiccManagementFragment() {
    companion object {
        const val TAG = "UnprivilegedEuiccManagementFragment"

        fun newInstance(slotId: Int, portId: Int): EuiccManagementFragment =
            newInstanceEuicc(UnprivilegedEuiccManagementFragment::class.java, slotId, portId)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_sim_toolkit, menu)

        menu.findItem(R.id.open_sim_toolkit).let {
            val supported = try {
                requireContext().packageManager.getPackageInfo("com.android.stk", 0)
                true
            } catch (_: NameNotFoundException) {
                false
            }
            it.isVisible = slotId != -1 && supported
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.open_sim_toolkit -> {
                Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(
                        "com.android.stk",
                        "com.android.stk.StkMain${channel.slotId + 1}"
                    )
                    startActivity(this)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
}