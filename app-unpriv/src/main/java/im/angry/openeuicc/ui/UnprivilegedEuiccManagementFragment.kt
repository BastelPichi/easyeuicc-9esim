package im.angry.openeuicc.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import im.angry.easyeuicc.R
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.channel
import im.angry.openeuicc.util.intentSTK
import im.angry.openeuicc.util.isInstalledSTK
import im.angry.openeuicc.util.newInstanceEuicc

class UnprivilegedEuiccManagementFragment : EuiccManagementFragment() {
    companion object {
        const val TAG = "UnprivilegedEuiccManagementFragment"

        fun newInstance(slotId: Int, portId: Int): EuiccManagementFragment =
            newInstanceEuicc(UnprivilegedEuiccManagementFragment::class.java, slotId, portId)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_sim_toolkit, menu)
        menu.findItem(R.id.open_sim_toolkit).isVisible = when (channel.logicalSlotId) {
            -1 -> false
            EuiccChannelManager.USB_CHANNEL_ID -> false
            else -> isInstalledSTK(requireContext())
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.open_sim_toolkit -> {
                startActivity(intentSTK(channel.logicalSlotId))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
}