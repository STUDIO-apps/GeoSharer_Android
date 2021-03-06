package uk.co.appsbystudio.geoshare.base

import android.app.Activity
import android.app.DialogFragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.header_layout.view.*
import uk.co.appsbystudio.geoshare.R
import uk.co.appsbystudio.geoshare.authentication.AuthActivity
import uk.co.appsbystudio.geoshare.base.adapters.FriendsNavAdapter
import uk.co.appsbystudio.geoshare.friends.manager.FriendsManager
import uk.co.appsbystudio.geoshare.maps.MapsFragment
import uk.co.appsbystudio.geoshare.utils.ProfileSelectionResult
import uk.co.appsbystudio.geoshare.utils.SettingsPreferencesHelper
import uk.co.appsbystudio.geoshare.utils.ShowMarkerPreferencesHelper
import uk.co.appsbystudio.geoshare.utils.TrackingPreferencesHelper
import uk.co.appsbystudio.geoshare.utils.dialog.ProfilePictureOptions
import uk.co.appsbystudio.geoshare.utils.dialog.ShareOptions
import uk.co.appsbystudio.geoshare.utils.services.TrackingService
import uk.co.appsbystudio.geoshare.utils.ui.SettingsActivity

class MainActivity : AppCompatActivity(), MainView, FriendsNavAdapter.Callback {

    private var presenter: MainPresenter? = null

    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    private var header: View? = null
    private var friendsNavAdapter: FriendsNavAdapter? = null

    private var mapsFragment: MapsFragment = MapsFragment()

    private val hasTracking = HashMap<String, Boolean>()
    private val friendsMap = LinkedHashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        presenter = MainPresenterImpl(this,
                ShowMarkerPreferencesHelper(getSharedPreferences("showOnMap", Context.MODE_PRIVATE)),
                TrackingPreferencesHelper(getSharedPreferences("tracking", Context.MODE_PRIVATE)),
                SettingsPreferencesHelper(PreferenceManager.getDefaultSharedPreferences(applicationContext)),
                MainInteractorImpl(cacheDir.toString()))

        presenter?.setTrackingService()

        /* HANDLES FOR VARIOUS VIEWS */
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.frame_content_map_main, mapsFragment, "maps_fragment").commit()
        } else {
            mapsFragment = supportFragmentManager.findFragmentByTag("maps_fragment") as MapsFragment
            presenter?.swapFragment(mapsFragment)
        }

        var navItemSelected = false
        var checkedItem = 0

        drawer_left_nav_main.setNavigationItemSelectedListener { item ->
            navItemSelected = true
            drawer_layout?.closeDrawers()
            when (item.itemId) {
                R.id.maps -> checkedItem = R.id.maps
                R.id.friends -> checkedItem = R.id.friends
                R.id.settings -> checkedItem = R.id.settings
                R.id.logout -> checkedItem = R.id.logout
                R.id.feedback -> checkedItem = R.id.feedback
            }
            false
        }

        val drawerListener = object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}

            override fun onDrawerClosed(drawerView: View) {
                if (navItemSelected) {
                    navItemSelected = false

                    presenter?.run {
                        when (checkedItem) {
                            R.id.maps -> swapFragment(mapsFragment)
                            R.id.friends -> friends()
                            R.id.settings -> settings()
                            R.id.logout -> logout()
                            R.id.feedback -> feedback()
                            else -> showError("Hmm... Looks like something has gone wrong")
                        }
                    }
                }
            }
        }

        drawer_layout.addDrawerListener(drawerListener)

        header = drawer_left_nav_main.getHeaderView(0)

        recycler_right_nav_main?.setHasFixedSize(true)
        recycler_right_nav_main?.layoutManager = LinearLayoutManager(this@MainActivity)

        //Get friends and populate right nav drawer
        presenter?.run {
            getFriends()
            getFriendsTrackingState()
            updatedProfileListener()
        }

        friendsNavAdapter = FriendsNavAdapter(this@MainActivity, recycler_right_nav_main, friendsMap, hasTracking, this)
        recycler_right_nav_main.adapter = friendsNavAdapter

        /* POPULATE LEFT NAV DRAWER HEADER FIELDS */
        header?.profile_image?.setOnClickListener {
            presenter?.openDialog(ProfilePictureOptions(), "")
        }

        presenter?.run {
            updateNavProfilePicture(header?.profile_image, this@MainActivity.cacheDir.toString())
            updateNavDisplayName()
            setMarkerVisibilityState()
        }

        switch_markers_main.setOnCheckedChangeListener { buttonView, isChecked ->
            mapsFragment.setAllMarkersVisibility(isChecked)
        }

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                presenter?.run {
                    clearSharedPreferences()
                    stopTrackingService()
                    auth()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 213) {
                mapsFragment.setup()
            } else {
                ProfileSelectionResult(main = this).profilePictureResult(this@MainActivity, requestCode, resultCode, data, FirebaseAuth.getInstance().currentUser?.uid)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onResume() {
        super.onResume()
        presenter?.updateNavDisplayName()
        presenter?.updateNavProfilePicture(header?.profile_image, this@MainActivity.cacheDir.toString())
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter?.stop()
        friendsMap.clear()
    }

    override fun updateFriendsList(uid: String?, name: String?) {
        if (uid != null && name != null) {
            friendsMap[uid] = name
            add_friends.visibility = View.GONE
        }
        friendsNavAdapter?.notifyDataSetChanged()
    }

    override fun removeFromFriendList(uid: String?) {
        friendsMap.remove(uid)
        friendsNavAdapter?.notifyDataSetChanged()
        if (friendsMap.isEmpty()) add_friends.visibility = View.VISIBLE
    }

    override fun updateTrackingState(uid: String?, trackingState: Boolean?) {
        if (uid != null && trackingState != null) hasTracking[uid] = trackingState
        friendsNavAdapter?.notifyDataSetChanged()
    }

    override fun removeTrackingState(uid: String?) {
        hasTracking.remove(uid)
        friendsNavAdapter?.notifyDataSetChanged()
    }

    override fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().show(fragment).commit()
    }

    override fun friendsIntent() {
        val intent = Intent(this@MainActivity, FriendsManager::class.java)
        intent.putExtra("friends_map", friendsMap)
        startActivity(intent)
    }

    override fun settingsIntent() {
        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }

    override fun logoutIntent() {
        startActivity(Intent(this@MainActivity, AuthActivity::class.java))
        finish()
    }

    override fun feedbackIntent(intent: Intent) {
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(intent, "Send email via"))
        } else {
            Toast.makeText(this@MainActivity, "No email applications found on this device!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun startTrackingServiceIntent() {
        val trackingService = Intent(applicationContext, TrackingService::class.java)
        startService(trackingService)
    }

    override fun stopTrackingServiceIntent() {
        val trackingService = Intent(applicationContext, TrackingService::class.java)
        stopService(trackingService)
    }

    override fun showDialog(dialog: DialogFragment, tag: String) {
        dialog.show(fragmentManager, tag)
    }

    override fun setDisplayName(name: String?) {
        header?.username?.text = String.format(resources.getString(R.string.welcome_user_header), name)
    }

    override fun updateProfilePicture() {
        presenter?.updateNavProfilePicture(header?.profile_image, this@MainActivity.cacheDir.toString())
    }

    override fun markerToggleState(state: Boolean?) {
        if (state != null) switch_markers_main.isChecked = state
    }

    override fun openNavDrawer() {
        drawer_layout?.openDrawer(GravityCompat.START)
    }

    override fun openFriendsNavDrawer() {
        drawer_layout?.openDrawer(GravityCompat.END)
    }

    override fun closeNavDrawer() {
        drawer_layout?.closeDrawer(GravityCompat.START)
    }

    override fun closeFriendsNavDrawer() {
        drawer_layout?.closeDrawer(GravityCompat.END)
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showErrorSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.coordinator), message, Snackbar.LENGTH_SHORT)
                .setAction("RETRY?") { presenter?.logout() }
    }

    override fun setMarkerHidden(friendId: String, visible: Boolean) {
        mapsFragment.setMarkerVisibility(friendId, visible)
    }

    override fun findOnMapClicked(friendId: String) {
        mapsFragment.findFriendOnMap(friendId)
    }

    override fun sendLocationDialog(name: String, friendId: String) {
        val arguments = Bundle()
        arguments.putString("name", name)
        arguments.putString("friendId", friendId)
        arguments.putString("uid", FirebaseAuth.getInstance().currentUser?.uid)

        val fragmentManager = fragmentManager
        val friendDialog = ShareOptions()
        friendDialog.arguments = arguments
        friendDialog.show(fragmentManager, "location_dialog")
    }

    override fun stopSharing(friendId: String) {
        presenter?.setFriendSharingState(friendId, false)
        friendsNavAdapter?.notifyDataSetChanged()
    }

    override fun onBackPressed() {
        when {
            drawer_layout.isDrawerOpen(GravityCompat.START) -> drawer_layout.closeDrawer(GravityCompat.START)
            drawer_layout.isDrawerOpen(GravityCompat.END) -> drawer_layout.closeDrawer(GravityCompat.END)
            else -> super.onBackPressed()
        }
    }
}