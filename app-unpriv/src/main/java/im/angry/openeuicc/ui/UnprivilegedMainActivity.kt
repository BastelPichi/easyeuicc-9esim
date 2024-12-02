package im.angry.openeuicc.ui

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import im.angry.easyeuicc.R

class UnprivilegedMainActivity: MainActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_main_unprivileged, menu)
        menu.findItem(R.id.compatibility_check).intent =
            Intent(this, CompatibilityCheckActivity::class.java)
        return true
    }
}