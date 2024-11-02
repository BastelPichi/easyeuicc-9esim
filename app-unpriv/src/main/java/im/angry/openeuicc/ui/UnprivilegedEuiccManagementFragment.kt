package im.angry.openeuicc.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import im.angry.easyeuicc.R
import im.angry.openeuicc.util.intentSTK
import im.angry.openeuicc.util.isInstalledSTK
import im.angry.openeuicc.util.isUsb
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
        menu.findItem(R.id.open_sim_toolkit).isVisible =
            slotId != -1 && !isUsb && isInstalledSTK(requireContext())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.open_sim_toolkit -> {
                try {
                    startActivity(intentSTK(slotId))
                } catch (_: Exception) {
                    startActivity(intentSTK(null))
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
}